import java.util.Locale
import java.util.concurrent.TimeUnit
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.aboutlibraries)
}

android {
    namespace = "dev.hachifridge.hachidroid"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.hachifridge.hachidroid"
        minSdk = 24
        targetSdk = 37

        versionCode = 1
        versionName = "1.0.0"

        // Self-release identity, shared by the in-app updater and the About links.
        buildConfigField("String", "REPO_PATH", "\"HachiFridge/HachiDroid\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        create("release") {
            val keyFile = System.getenv("SIGNING_KEY_FILE")
            if (keyFile != null && keyFile.isNotEmpty()) {
                storeFile = file(keyFile)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }

        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        // Bytecode level for javac and (via AGP's built-in Kotlin) kotlinc.
        // Java 21 LTS matches the pinned daemon JVM; D8 desugars the class
        // files to dex, so API 24+ devices are unaffected.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    packaging {
        resources {
            pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/*"
        }
    }
    lint {
        disable += "MissingTranslation"
        abortOnError = false
    }
}

aboutLibraries {
    collect {
        configPath = rootProject.layout.projectDirectory.dir("config")
    }
}

// ---- extensions.dex: compile the DocumentsProvider into an asset at build time ----
val extensionsSrcDir = file("src/main/extensions")

val extensionsSdkDir: File = run {
    val localProps = rootProject.file("local.properties")
    val fromLocal = if (localProps.isFile) {
        localProps.readLines()
            .firstOrNull { it.startsWith("sdk.dir=") }
            ?.substringAfter("sdk.dir=")?.trim()
    } else null
    val fromEnv = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    val resolved = fromLocal ?: fromEnv
        ?: throw GradleException("extensions dex: Android SDK directory could not be resolved")
    File(resolved)
}

val extensionsPlatformJar: File = run {
    val compileSdk = android.compileSdk ?: 37
    val wanted = "android-$compileSdk"
    val platformsDir = File(extensionsSdkDir, "platforms")
    val platformDir = platformsDir.listFiles()?.firstOrNull { it.name == wanted }
        ?: platformsDir.listFiles()
            ?.filter { it.name.startsWith("$wanted.") }
            ?.maxByOrNull { it.name }
        ?: throw GradleException("extensions dex: platform jar not found for $wanted under $platformsDir")
    val jar = File(platformDir, "android.jar")
    if (!jar.isFile) throw GradleException("extensions dex: platform jar not found: $jar")
    jar
}

val extensionsD8Jar: File = run {
    val buildToolsRoot = File(extensionsSdkDir, "build-tools")
    val candidates = buildToolsRoot.listFiles()
        ?.filter { it.isDirectory && File(it, "lib/d8.jar").isFile }
        ?.map { File(it, "lib/d8.jar") }
        ?: emptyList()
    if (candidates.isEmpty()) {
        throw GradleException("extensions dex: no d8.jar found under $buildToolsRoot; install the Android SDK build-tools package")
    }
    candidates.maxByOrNull { it.parentFile.parentFile.name }!!
}

val extensionsClassesDir = layout.buildDirectory.dir("extensions/classes")
val extensionsDexDir = layout.buildDirectory.dir("extensions/dex")
val extensionsAssetsDir = layout.buildDirectory.dir("generated/extensionsAssets")

val compileExtensionsJava = tasks.register<JavaCompile>("compileExtensionsJava") {
    source(extensionsSrcDir)
    include("**/*.java")
    classpath = files(extensionsPlatformJar)
    destinationDirectory.set(extensionsClassesDir)
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.encoding = "UTF-8"
}

val dexExtensions = tasks.register<JavaExec>("dexExtensions") {
    dependsOn(compileExtensionsJava)
    mainClass.set("com.android.tools.r8.D8")
    classpath = files(extensionsD8Jar)
    // Bind script values to configuration-lambda locals so the execution
    // action below doesn't capture script object references
    // (configuration cache requirement).
    val classesDir = extensionsClassesDir
    val dexDir = extensionsDexDir
    val platformJar = extensionsPlatformJar
    inputs.dir(classesDir)
    outputs.dir(dexDir)
    doFirst {
        val dexOut = dexDir.get().asFile
        dexOut.mkdirs()
        val classesRoot = classesDir.get().asFile
        val classFiles = classesRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".class") }
            .sortedBy { it.name }
            .toList()
        if (classFiles.isEmpty()) {
            throw GradleException("extensions dex: no class files under $classesRoot")
        }
        setArgs(
            listOf(
                "--release", "--min-api", "21",
                "--lib", platformJar.absolutePath,
                "--output", dexOut.absolutePath
            ) + classFiles.map { it.absolutePath }
        )
    }
}

val generateExtensionsAsset = tasks.register<Copy>("generateExtensionsAsset") {
    dependsOn(dexExtensions)
    from(extensionsDexDir) {
        include("classes.dex")
        rename { "extensions.dex" }
    }
    into(extensionsAssetsDir)
}

android.sourceSets.getByName("main").assets.directories.add(extensionsAssetsDir.get().asFile.absolutePath)

tasks.matching { t ->
    (t.name.startsWith("merge") && t.name.endsWith("Assets")) ||
        t.name.lowercase(Locale.ROOT).contains("lint")
}.configureEach {
    dependsOn(generateExtensionsAsset)
}

val cleanStaleExtensionsAsset = tasks.register("cleanStaleExtensionsAsset") {
    // Resolve at configuration time so the action captures only a File local
    // (configuration cache requirement).
    val stale = file("src/main/assets/extensions.dex")
    outputs.upToDateWhen { false }
    doLast {
        if (stale.isFile) {
            logger.lifecycle("extensions: removing stale asset $stale (superseded by build-time generation)")
            stale.delete()
        }
    }
}

generateExtensionsAsset.configure { dependsOn(cleanStaleExtensionsAsset) }

// ---- senbei-jni: compile the Rust restore bridge and package it as a JNI library ----
// The bridge lives outside the Gradle build (Cargo owns it); Gradle only orchestrates. It is
// optional on purpose: without cargo or an NDK the library is simply absent, and the app hides
// on-device restore instead of failing to build.
val senbeiRustDir = file("src/main/rust/senbei-jni")
val senbeiJniLibsDir = layout.buildDirectory.dir("generated/senbeiJniLibs")
val senbeiCargoTargetDir = layout.buildDirectory.dir("rust/senbei-jni")

val senbeiNdkDir: File? = run {
    val localProps = rootProject.file("local.properties")
    val fromLocal = if (localProps.isFile) {
        localProps.readLines()
            .firstOrNull { it.startsWith("ndk.dir=") }
            ?.substringAfter("ndk.dir=")?.trim()
    } else null
    val explicit = fromLocal
        ?: System.getenv("ANDROID_NDK_ROOT")
        ?: System.getenv("ANDROID_NDK_HOME")
    if (!explicit.isNullOrBlank()) {
        File(explicit)
    } else {
        File(extensionsSdkDir, "ndk").listFiles()
            ?.filter { File(it, "toolchains/llvm/prebuilt").isDirectory }
            ?.maxByOrNull { it.name }
    }
}

val buildSenbeiJni = tasks.register<Exec>("buildSenbeiJni") {
    group = "build"
    description = "Compiles libsenbei_jni.so for arm64-v8a and stages it as a JNI library."

    val rustDir = senbeiRustDir
    val stagedDir = senbeiJniLibsDir.get().asFile
    val targetDir = senbeiCargoTargetDir.get().asFile
    val cargo = System.getenv("CARGO")?.takeIf { it.isNotBlank() } ?: "cargo"
    val ndk = senbeiNdkDir
    val hostExe = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) ".cmd" else ""

    // Nothing here may reference a script-level helper: onlyIf is stored in the configuration
    // cache, and script objects cannot be serialized. JDK types and bound locals are fine.
    onlyIf("cargo and an Android NDK are available") {
        ndk != null && runCatching {
            val process = ProcessBuilder(cargo, "--version").redirectErrorStream(true).start()
            process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0
        }.getOrDefault(false)
    }

    workingDir = rustDir
    inputs.dir(File(rustDir, "src"))
    inputs.file(File(rustDir, "Cargo.toml"))
    outputs.dir(stagedDir)

    environment("CARGO_TARGET_DIR", targetDir.absolutePath)
    if (ndk != null) {
        val prebuilt = File(ndk, "toolchains/llvm/prebuilt")
            .listFiles()
            ?.firstOrNull { File(File(it, "bin"), "llvm-strip$hostExe").isFile }
            ?: File(ndk, "toolchains/llvm/prebuilt/windows-x86_64")
        // minSdk 24: the platform API level the bridge is compiled against.
        val linker = File(File(prebuilt, "bin"), "aarch64-linux-android24-clang$hostExe")
        environment("CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER", linker.absolutePath)
    }
    commandLine(cargo, "build", "--release", "--target", "aarch64-linux-android")

    doLast {
        val built = File(targetDir, "aarch64-linux-android/release/libsenbei_jni.so")
        if (!built.isFile) {
            throw GradleException("senbei-jni: cargo produced no library at $built")
        }
        val destination = File(stagedDir, "arm64-v8a")
        destination.mkdirs()
        built.copyTo(File(destination, "libsenbei_jni.so"), overwrite = true)
    }
}

android.sourceSets.getByName("main").jniLibs.directories.add(senbeiJniLibsDir.get().asFile.absolutePath)

tasks.matching { task ->
    task.name.startsWith("merge") &&
        (task.name.endsWith("JniLibFolders") || task.name.endsWith("NativeLibs"))
}.configureEach {
    dependsOn(buildSenbeiJni)
}

// ---- aboutlibraries: the 10.10.0 plugin wires Android res generation via the
// removed AGP (AppExtension) API, which silently does nothing on AGP 9.
// Bridge its export task output into a generated res directory instead.
val aboutLibsResDir = layout.buildDirectory.dir("generated/aboutLibrariesRes")
val generateAboutLibsRes = tasks.register<Copy>("generateAboutLibsRes") {
    dependsOn(tasks.named("exportLibraryDefinitions"))
    from(layout.buildDirectory.dir("generated/aboutLibraries"))
    include("aboutlibraries.json")
    into(aboutLibsResDir.map { it.dir("raw") })
}

android.sourceSets.getByName("main").res.directories.add(aboutLibsResDir.get().asFile.absolutePath)

tasks.matching { t ->
    val n = t.name
    n.contains("Resources") || n.contains("SourceSet") ||
        n.lowercase(Locale.ROOT).contains("lint")
}.configureEach {
    dependsOn(generateAboutLibsRes)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.gson)
    implementation(libs.libsu.core)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.apksig)
    implementation(libs.arsclib)
    implementation(libs.bcprov)
    implementation(libs.bcpkix)
    implementation(libs.zip4j)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
