package dev.hachifridge.hachidroid.client

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.hachifridge.hachidroid.client.tracen.TracenLibrarySafeguard
import dev.hachifridge.hachidroid.core.GitHubReleases
import dev.hachifridge.hachidroid.core.PrefKey
import dev.hachifridge.hachidroid.core.dataStore
import dev.hachifridge.hachidroid.core.getPrefValue
import dev.hachifridge.hachidroid.utils.bytesToHex
import dev.hachifridge.hachidroid.utils.downloadFileAndDigestSHA256
import dev.hachifridge.hachidroid.utils.fetchJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL

/**
 * One game's mod runtime, and the one place its source is decided.
 *
 * Hachimi and DaringTact are different libraries for different games, but the workflow around them
 * is identical: pick GitHub releases or a local `.so`, keep the file in the client's library folder,
 * and let the patch read it from there. Keeping one implementation means the studio, the dashboard
 * tile and the patcher cannot drift into three slightly different answers about "where the mod
 * comes from" — the mod source is the answer, and the client decides which source it is.
 *
 * The settings behind it stay per client (each game has its own repo, and its own local file), but
 * the keys live here rather than in a pair of parallel objects.
 */
class ModSource(
    /** Log tag and preference namespace, e.g. `hachimi`. */
    val id: String,
    /** The file name the patch writes and the game loads. */
    val libraryName: String,
    val libraries: LibrarySet,
    val repoKey: Preferences.Key<String>,
    val sourceKey: Preferences.Key<String>,
    val uriKey: Preferences.Key<String>,
    val nameKey: Preferences.Key<String>,
    /** Release tag of the cached library, when the runtime publishes one. */
    val versionKey: Preferences.Key<String>? = null,
    /** A checksum file published beside the asset, as Hachimi does; null when there is none. */
    private val checksumAsset: String? = null,
    /** Extra names a release may use for this library, beyond [libraryName]. */
    private val alternativeAsset: (String) -> Boolean = { false },
    /** Runtime-specific content check for a freshly written library. */
    private val validator: ((Context, File) -> Validation)? = null
) {

    /** What one fetch or import did, in enough detail for the studio to say it. */
    sealed interface Outcome {
        /** Installed; [detail] is the release tag for GitHub, the picked name for a local file. */
        data class Installed(val detail: String) : Outcome

        /** The cached library is already the newest release, so nothing was downloaded. */
        data class AlreadyLatest(val version: String) : Outcome

        /** No repository is configured, so there is nothing to download from. */
        data object RepoMissing : Outcome

        /** The release exists but carries no library for this game. */
        data object AssetMissing : Outcome

        /** The release has no checksum file, so the download could not be verified. */
        data object ChecksumMissing : Outcome

        /** The bytes did not match the published checksum. */
        data object ChecksumMismatch : Outcome

        /** Downloaded or copied fine, but the bytes are not a library the game could load. */
        data class Rejected(val message: String) : Outcome

        data class Failed(val reason: String?) : Outcome
    }

    sealed interface Validation {
        data class Ok(val detail: String? = null) : Validation
        data class Rejected(val message: String) : Validation
    }

    private val tag = "ModSource($id)"

    /** Where the patcher reads the library from. */
    fun target(context: Context): File =
        libraries.directory(context).resolve(libraryName)

    fun isCached(context: Context): Boolean = target(context).isFile

    suspend fun cachedVersion(context: Context): String =
        versionKey?.let { context.getPrefValue(it) as String? }.orEmpty()

    /**
     * Fetches the newest release asset. Network-bound, and [withContext] keeps that off the main
     * thread. A Codeberg mirror of the same repository is tried when GitHub refuses.
     */
    suspend fun download(
        context: Context,
        onProgress: ((Float) -> Unit)? = null
    ): Outcome = withContext(Dispatchers.IO) {
        val repo = (context.getPrefValue(repoKey) as String).trim().trim('/')
        if (repo.isEmpty()) return@withContext Outcome.RepoMissing

        val latest = try {
            GitHubReleases(repo).fetchLatest()
        } catch (ex: Exception) {
            try {
                GitHubReleases(repo, baseUrl = "https://codeberg.org/api/v1/repos").fetchLatest()
            } catch (fallback: Exception) {
                Log.w(tag, "Release lookup failed for $repo", fallback)
                return@withContext Outcome.Failed(fallback.message ?: ex.message)
            }
        }

        val version = latest["tag_name"] as? String ?: ""
        val target = target(context)
        if (version.isNotEmpty() && version == cachedVersion(context) && target.isFile) {
            return@withContext Outcome.AlreadyLatest(version)
        }

        val assets = (latest["assets"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        fun urlOf(name: String): String? = assets.firstNotNullOfOrNull { asset ->
            if (asset["name"] == name) asset["browser_download_url"] as? String else null
        }

        val assetName = assets.asSequence()
            .mapNotNull { it["name"] as? String }
            .firstOrNull { it == libraryName || alternativeAsset(it) }
            ?: return@withContext Outcome.AssetMissing
        val libUrl = urlOf(assetName) ?: return@withContext Outcome.AssetMissing

        val expected = checksumAsset?.let { checksumName ->
            val checksumUrl = urlOf(checksumName) ?: return@withContext Outcome.ChecksumMissing
            try {
                (fetchJson(URL(checksumUrl))[assetName] as? String)?.lowercase()
                    ?: return@withContext Outcome.ChecksumMismatch
            } catch (ex: Exception) {
                Log.w(tag, "Checksum fetch failed", ex)
                return@withContext Outcome.Failed(ex.message)
            }
        }

        try {
            // Same-directory staging keeps the final step a rename, so a failure never leaves a
            // half-written library where the patcher would pick it up.
            val staged = stage(context)
            val digest = bytesToHex(downloadFileAndDigestSHA256(URL(libUrl), staged) { fraction ->
                onProgress?.invoke(fraction)
            })
            if (expected != null && digest != expected) {
                staged.delete()
                Log.w(tag, "Checksum mismatch: $digest != $expected")
                return@withContext Outcome.ChecksumMismatch
            }
            install(context, staged, version)
        } catch (ex: Exception) {
            Log.w(tag, "Mod library download failed", ex)
            Outcome.Failed(ex.message)
        }
    }

    /** Copies a picked `.so` into the library folder, replacing whatever was there. */
    suspend fun importLocal(context: Context, uri: Uri): Outcome = withContext(Dispatchers.IO) {
        val name = uri.lastPathSegment ?: libraryName
        try {
            val staged = stage(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                staged.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                staged.delete()
                return@withContext Outcome.Failed(null)
            }
            install(context, staged, name)
        } catch (ex: Exception) {
            Log.w(tag, "Local mod import failed", ex)
            Outcome.Failed(ex.message)
        }
    }

    /**
     * Makes sure the patcher has the library it is about to write, from whichever source the user
     * chose, and returns the version (or file name) to log. `null` means there is nothing usable.
     *
     * A download that fails but leaves an older cached library in place is a warning, not a
     * failure: that copy is what the previous patch used and is still the best available answer.
     * Touches disk and the network: call from a background dispatcher.
     */
    suspend fun ensure(
        context: Context,
        log: (String) -> Unit,
        onProgress: ((Float) -> Unit)? = null
    ): String? {
        val source = context.getPrefValue(sourceKey) as String
        if (source == "local") {
            val cached = target(context)
            val label = (context.getPrefValue(nameKey) as String).ifEmpty { libraryName }
            if (cached.isFile && cached.length() > 0L) return cachedVersion(context).ifEmpty { label }

            val uri = (context.getPrefValue(uriKey) as String).ifEmpty {
                log("No local mod library selected")
                return null
            }
            return when (val outcome = importLocal(context, uri.toUri())) {
                is Outcome.Installed -> outcome.detail
                is Outcome.Rejected -> {
                    log(outcome.message)
                    null
                }
                is Outcome.Failed -> {
                    log("Could not read the local mod library: ${outcome.reason.orEmpty()}")
                    null
                }
                else -> null
            }
        }

        return when (val outcome = download(context, onProgress)) {
            is Outcome.Installed -> outcome.detail
            is Outcome.AlreadyLatest -> outcome.version
            Outcome.RepoMissing -> {
                log("No mod repository configured")
                null
            }
            Outcome.AssetMissing -> {
                log("The latest release has no $libraryName")
                null
            }
            Outcome.ChecksumMissing, Outcome.ChecksumMismatch -> {
                log("The mod library download could not be verified")
                cachedVersion(context).ifEmpty { null }
            }
            is Outcome.Rejected -> {
                log(outcome.message)
                null
            }
            is Outcome.Failed -> {
                val cached = cachedVersion(context)
                if (cached.isNotEmpty()) {
                    log("Could not update the mod library, using $cached")
                    cached
                } else {
                    log("Could not download the mod library: ${outcome.reason.orEmpty()}")
                    null
                }
            }
        }
    }

    /**
     * Moves [staged] into place once it passes the library checks. A missing or unusable file is
     * dropped either way, so the folder never holds a half-written library.
     */
    private suspend fun install(context: Context, staged: File, detail: String): Outcome {
        if (!staged.isFile || staged.length() == 0L) {
            staged.delete()
            return Outcome.Failed("the library is empty")
        }
        if (!isElf(staged)) {
            staged.delete()
            return Outcome.Rejected("The mod library is not an ELF shared object")
        }

        val check = validator?.invoke(context, staged)
        if (check is Validation.Rejected) {
            staged.delete()
            return Outcome.Rejected(check.message)
        }
        val version = (check as? Validation.Ok)?.detail
        val label = version ?: detail

        val target = prepare(context)
        if (target.exists() && !target.delete()) {
            staged.delete()
            return Outcome.Failed("Could not replace the existing library")
        }
        if (!staged.renameTo(target)) {
            staged.delete()
            return Outcome.Failed("Could not move the library into place")
        }
        versionKey?.let { key -> context.dataStore.edit { it[key] = label } }
        return Outcome.Installed(label)
    }

    /** The library folder only exists once something has been written to it. */
    private fun prepare(context: Context): File {
        libraries.directory(context).mkdirs()
        return target(context)
    }

    private fun stage(context: Context): File {
        val target = prepare(context)
        return File(target.parentFile, "${target.name}.part")
    }

    private fun isElf(file: File): Boolean = runCatching {
        file.inputStream().use { stream ->
            val magic = ByteArray(4)
            stream.read(magic) == magic.size &&
                magic[0] == 0x7F.toByte() && magic[1] == 'E'.code.toByte() &&
                magic[2] == 'L'.code.toByte() && magic[3] == 'F'.code.toByte()
        }
    }.getOrDefault(false)
}

/**
 * Hachimi, Tracen's mod runtime. Its releases publish `libmain-arm64-v8a.so` with a `sha256.json`
 * beside it, and the library carries an engine marker the safeguard checks.
 */
val TracenModSource = ModSource(
    id = "hachimi",
    libraryName = "libmain-arm64-v8a.so",
    libraries = TracenLibraries,
    repoKey = PrefKey.HACHIMI_REPO,
    sourceKey = PrefKey.TRACEN_MOD_SOURCE,
    uriKey = PrefKey.TRACEN_MOD_SO_URI,
    nameKey = PrefKey.TRACEN_MOD_SO_NAME,
    versionKey = PrefKey.HACHIMI_LIBS_VERSION,
    checksumAsset = "sha256.json",
    validator = { context, file ->
        when (val result = TracenLibrarySafeguard.validateModLibraryFile(file)) {
            is TracenLibrarySafeguard.ValidationResult.Valid -> ModSource.Validation.Ok(result.version)
            is TracenLibrarySafeguard.ValidationResult.Error -> ModSource.Validation.Rejected(result.message(context))
        }
    }
)

/** DaringTact, Astraea's mod runtime: one `.so` asset, no checksum file, no engine marker. */
val AstraeaModSource = ModSource(
    id = "daringtact",
    libraryName = AstraeaLibraries.modLibrary,
    libraries = AstraeaLibraries,
    repoKey = PrefKey.DARINGTACT_REPO,
    sourceKey = PrefKey.ASTRAEA_MOD_SOURCE,
    uriKey = PrefKey.ASTRAEA_MOD_SO_URI,
    nameKey = PrefKey.ASTRAEA_MOD_SO_NAME,
    alternativeAsset = { name -> name.endsWith(".so", ignoreCase = true) && name.contains("daringtact", ignoreCase = true) }
)
