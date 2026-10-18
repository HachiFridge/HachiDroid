//! JNI bridge between HachiDroid and Senbei's Android unpack engine.
//!
//! Senbei does the work; this crate is the seam. It exposes exactly two operations — probe a
//! library for the CrackProof payload, and write one usable library to a destination path — because
//! that is the whole contract HachiDroid's Priconne flow needs. Both are deliberately shaped for
//! Android: paths in, JSON out, no filesystem knowledge on the Kotlin side, and a scratch root
//! the caller owns so nothing lands in `/tmp` (which does not exist on a device).
//!
//! Results are reported as JSON rather than through Java exceptions: the Kotlin caller receives a
//! structured failure it can log and show, and a malformed argument can never crash the process.
//! Panics are caught at the boundary for the same reason — an unwind across FFI is undefined
//! behaviour, and the engine does far more pointer work than most code we call into.
//!
//! Senbei is AGPL-3.0-only, so this crate (`license = "AGPL-3.0-only"`) and any app shipping the
//! resulting `libsenbei_jni.so` fall under those terms. See README.md.

use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::Path;
use std::sync::Once;
use std::time::Instant;

use jni::objects::{JObject, JString};
use jni::sys::{jboolean, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use memmap2::MmapOptions;
use senbei_engine::android::{
    extract_stage2, is_protected_libil2cpp, restore_libil2cpp, ExtractOptions, RestoreOptions,
};
use serde::Serialize;

/// Where the engine's intermediate container modules go, relative to the caller's scratch root.
const STAGE2_SUBDIR: &str = "stage2";

/// Relative to the caller's scratch root. The engine allocates its output through `tempfile`,
/// which resolves `TMPDIR` at first use, so process-wide assignment has to happen once.
const TEMP_SUBDIR: &str = "tmp";

const ELFCLASS64: u8 = 2;
const EM_AARCH64: u16 = 0xb7;

/// Successful unpack, flattened to the handful of numbers worth storing or logging.
#[derive(Debug, Serialize)]
struct UnpackFacts {
    ok: bool,
    output_bytes: u64,
    elapsed_ms: u64,
    sections: usize,
    segments: usize,
    dynamic_symbols: usize,
    dynamic_relocations: usize,
    private_payload_section: bool,
}

#[derive(Debug, Serialize)]
struct Failure {
    ok: bool,
    error: String,
}

/// `SenbeiNative.nativeIsProtectedLibrary(path)`
#[no_mangle]
pub extern "system" fn Java_dev_hachifridge_hachidroid_core_senbei_SenbeiNative_nativeIsProtectedLibrary(
    mut env: JNIEnv,
    _instance: JObject,
    path: JString,
) -> jboolean {
    let Some(path) = string_arg(&mut env, &path) else {
        return JNI_FALSE;
    };
    match catch_unwind(AssertUnwindSafe(|| probe(Path::new(&path)))) {
        Ok(true) => JNI_TRUE,
        _ => JNI_FALSE,
    }
}

/// `SenbeiNative.nativeUnpackLibrary(input, output, workDir)` — returns a JSON document.
#[no_mangle]
pub extern "system" fn Java_dev_hachifridge_hachidroid_core_senbei_SenbeiNative_nativeUnpackLibrary(
    mut env: JNIEnv,
    _instance: JObject,
    input: JString,
    output: JString,
    work_dir: JString,
) -> jstring {
    let json = unpack_json(&mut env, &input, &output, &work_dir);
    match env.new_string(json) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn unpack_json(env: &mut JNIEnv, input: &JString, output: &JString, work_dir: &JString) -> String {
    let (input, output, work_dir) = match (
        string_arg(env, input),
        string_arg(env, output),
        string_arg(env, work_dir),
    ) {
        (Some(input), Some(output), Some(work_dir)) => (input, output, work_dir),
        _ => return failure_json("arguments could not be read"),
    };

    let attempt = catch_unwind(AssertUnwindSafe(|| {
        unpack(Path::new(&input), Path::new(&output), Path::new(&work_dir))
    }));

    match attempt {
        Ok(Ok(facts)) => serde_json::to_string(&facts)
            .unwrap_or_else(|error| failure_json(&format!("report encoding failed: {error}"))),
        Ok(Err(error)) => failure_json(&error),
        Err(_) => failure_json("unpack aborted by an internal panic"),
    }
}

/// Unpacks one protected AArch64 library, leaving the finished image at `output`.
fn unpack(input: &Path, output: &Path, work_dir: &Path) -> Result<UnpackFacts, String> {
    if !input.is_file() {
        return Err(format!("input is not a file: {}", input.display()));
    }
    if let Some(parent) = output.parent() {
        std::fs::create_dir_all(parent)
            .map_err(|error| format!("create output directory: {error}"))?;
    }
    std::fs::create_dir_all(work_dir).map_err(|error| format!("create scratch root: {error}"))?;
    set_temp_root(work_dir);

    let stage2 = work_dir.join(STAGE2_SUBDIR);
    let _ = std::fs::remove_dir_all(&stage2);
    std::fs::create_dir_all(&stage2).map_err(|error| format!("create stage-2 workspace: {error}"))?;

    let started = Instant::now();
    let extraction = extract_stage2(&ExtractOptions::with_defaults(
        input.to_path_buf(),
        stage2.clone(),
    ))
    .map_err(|error| format!("stage-2 extraction failed: {error}"))?;
    if !extraction.errors.is_empty() {
        let _ = std::fs::remove_dir_all(&stage2);
        return Err(format!(
            "stage-2 extraction reported {} error(s): {}",
            extraction.errors.len(),
            extraction.errors.join("; ")
        ));
    }

    let report = restore_libil2cpp(&RestoreOptions {
        input: input.to_path_buf(),
        output: output.to_path_buf(),
        index: stage2.join("index.json"),
        dump_auxiliary: None,
        outer_only: false,
        preserve_entrypoint: false,
        verbose: false,
    })
    .map_err(|error| format!("library unpack failed: {error}"))?;

    // The stage-2 workspace is an implementation detail; a failed unpack keeps it for diagnosis.
    let _ = std::fs::remove_dir_all(&stage2);

    Ok(UnpackFacts {
        ok: true,
        output_bytes: report.output_size,
        elapsed_ms: started.elapsed().as_millis().min(u64::MAX as u128) as u64,
        sections: report.validation.sections,
        segments: report.validation.segments,
        dynamic_symbols: report.validation.dynamic_symbols,
        dynamic_relocations: report.validation.dynamic_relocations,
        private_payload_section: report.validation.has_louser,
    })
}

/// True when `path` is an ELF64/AArch64 image carrying a CrackProof payload section.
fn probe(path: &Path) -> bool {
    let Ok(file) = std::fs::File::open(path) else {
        return false;
    };
    // SAFETY: read-only mapping; the file handle outlives the mapping.
    let Ok(bytes) = (unsafe { MmapOptions::new().map(&file) }) else {
        return false;
    };
    looks_like_aarch64(&bytes) && is_protected_libil2cpp(&bytes)
}

fn looks_like_aarch64(data: &[u8]) -> bool {
    data.len() > 20
        && data[0..4] == *b"\x7fELF"
        && data[4] == ELFCLASS64
        && u16::from_le_bytes([data[18], data[19]]) == EM_AARCH64
}

static TEMP_ROOT: Once = Once::new();

fn set_temp_root(work_dir: &Path) {
    TEMP_ROOT.call_once(|| {
        let dir = work_dir.join(TEMP_SUBDIR);
        if std::fs::create_dir_all(&dir).is_ok() {
            std::env::set_var("TMPDIR", &dir);
            std::env::set_var("TMP", &dir);
            std::env::set_var("TEMP", &dir);
        }
    });
}

fn string_arg(env: &mut JNIEnv, value: &JString) -> Option<String> {
    let raw = env.get_string(value).ok()?;
    Some(String::from(raw))
}

fn failure_json(error: &str) -> String {
    serde_json::to_string(&Failure {
        ok: false,
        error: error.to_owned(),
    })
    .unwrap_or_else(|_| "{\"ok\":false,\"error\":\"unreportable failure\"}".to_owned())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::PathBuf;

    fn temp_root(name: &str) -> PathBuf {
        let root = std::env::temp_dir().join(format!("senbei-jni-{name}"));
        let _ = std::fs::remove_dir_all(&root);
        std::fs::create_dir_all(&root).expect("create test root");
        root
    }

    #[test]
    fn recognises_an_aarch64_elf_header() {
        let mut header = vec![0_u8; 64];
        header[0..4].copy_from_slice(b"\x7fELF");
        header[4] = 2;
        header[18] = 0xb7;
        header[19] = 0x00;
        assert!(looks_like_aarch64(&header));

        header[18] = 0x28; // EM_ARM, a 32-bit machine
        assert!(!looks_like_aarch64(&header));

        header[18] = 0xb7;
        assert!(!looks_like_aarch64(&header[..12]));
    }

    #[test]
    fn reports_a_missing_input_as_a_failure() {
        let root = temp_root("missing");
        let error = unpack(
            &root.join("absent.so"),
            &root.join("out.so"),
            &root.join("work"),
        )
        .expect_err("a missing input must fail");
        assert!(error.contains("not a file"), "unexpected error: {error}");
        let _ = std::fs::remove_dir_all(&root);
    }

    /// Opt-in on purpose: the fixture is a real 300 MB protected library, which this repository
    /// must never carry. Run with `SENBEI_TEST_LIB=/path/to/libil2cpp.so cargo test --release`.
    #[test]
    fn unpacks_a_supplied_protected_library() {
        let Ok(source) = std::env::var("SENBEI_TEST_LIB") else {
            return;
        };
        let root = temp_root("restore");
        let output = root.join("restored.so");

        let facts = unpack(Path::new(&source), &output, &root.join("work")).expect("unpack");
        assert!(facts.ok);
        assert!(facts.sections > 0, "no section headers were rebuilt");
        assert!(!facts.private_payload_section, "payload section survived");
        assert_eq!(
            output.metadata().expect("output metadata").len(),
            facts.output_bytes
        );

        let head = std::fs::read(&output).expect("read output");
        assert_eq!(&head[0..4], b"\x7fELF");
        assert!(looks_like_aarch64(&head));
        let _ = std::fs::remove_dir_all(&root);
    }
}
