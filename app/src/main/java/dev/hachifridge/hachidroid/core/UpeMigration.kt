package dev.hachifridge.hachidroid.core

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.topjohnwu.superuser.Shell
import dev.hachifridge.hachidroid.patcher.HACHIDROID_KS_PASSWORD
import dev.hachifridge.hachidroid.patcher.RootUtils
import dev.hachifridge.hachidroid.utils.ksFile
import dev.hachifridge.hachidroid.utils.pluginsDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security

/**
 * Imports state left behind by UmaPatcher-Edge (fork dev.LeadRDRK.UmaPatcherEdge,
 * upstream com.leadrdrk.umapatcher.edge, and base upstream com.leadrdrk.umapatcher —
 * same DataStore, plugin layout and BKS key parameters, so one path covers all):
 * signing key, shared settings, the Hachimi arm64 library and plugins.
 * Requires root; files are staged through the app cache so HachiDroid's own
 * ownership/SELinux labels stay correct.
 */
object UpeMigration {
    private const val TAG = "UpeMigration"

    const val PKG_LEGACY = "dev.LeadRDRK.UmaPatcherEdge"
    const val PKG_UPSTREAM_EDGE = "com.leadrdrk.umapatcher.edge"
    const val PKG_UPSTREAM = "com.leadrdrk.umapatcher"

    /** Detection/migration order: the fork first, then upstream builds. */
    private val SOURCE_PACKAGES = listOf(PKG_LEGACY, PKG_UPSTREAM_EDGE, PKG_UPSTREAM)

    // DataStore keys shared by both UPE variants (identical string names).
    internal val SRC_CHECK_FOR_UPDATES = booleanPreferencesKey("check_for_updates")
    internal val SRC_HACHIMI_REPO = stringPreferencesKey("hachimi_repo")
    internal val SRC_MERGE_APKS = booleanPreferencesKey("merge_apks")
    internal val SRC_EXPORT_INTERNAL_DATA_PROVIDER = booleanPreferencesKey("export_internal_data_provider")
    internal val SRC_USE_INTERNAL_FILES_DIR = booleanPreferencesKey("use_internal_files_dir")
    internal val SRC_HACHIMI_LIBS_VERSION = stringPreferencesKey("app_libs_version")
    internal val SRC_USE_UNIVERSAL_SIGNING_KEY = booleanPreferencesKey("use_universal_signing_key")

    // Keystore password shared by both UPE variants; reads keys imported from them.
    internal const val KS_PASSWORD = "securep@ssw0rd816-n"
    private const val UNIVERSAL_KS_ALIAS = "patcheduma"
    private const val KS_FILE_NAME = "keystore.bks"
    private const val UNIVERSAL_KS_FILE_NAME = "patched.keystore"

    private const val MOD_LIB_NAME = "libmain-arm64-v8a.so"
    private const val CONFIG_PREFS_FILE = "config.preferences_pb"

    data class DetectedSource(val packageName: String, val label: String)

    data class MigrationSummary(
        val sourceLabels: String,
        val prefsApplied: Int,
        val keystoreImported: Boolean,
        val modLibImported: Boolean,
        val pluginsImported: Int
    ) {
        val isBlank: Boolean
            get() = prefsApplied == 0 && !keystoreImported && !modLibImported && pluginsImported == 0
    }

    // ---------------------------------------------------------------- detection

    /**
     * Finds UPE installs on this device: installed packages via PackageManager
     * (`<queries>` covers both IDs), leftover data of an uninstalled UPE via root.
     */
    // The absolute /data/data path addresses ANOTHER app's private directory
    // (root only); Context.getFilesDir() would return HachiDroid's own.
    @Suppress("SdCardPath")
    suspend fun detectSources(context: Context): List<DetectedSource> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val isRoot = try { Shell.getShell().isRoot } catch (_: Exception) { false }
        SOURCE_PACKAGES.mapNotNull { pkg ->
            val installed = try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
            val hasData = when {
                installed -> true
                isRoot -> RootUtils.testDirectory("/data/data/$pkg")
                else -> false
            }
            if (!hasData) return@mapNotNull null
            val label = if (installed) {
                try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) {
                    pkg
                }
            } else {
                pkg
            }
            DetectedSource(pkg, label)
        }
    }

    // ------------------------------------------------------------ prefs parsing

    /** Whitelisted UPE preferences HachiDroid also understands (nullable = absent). */
    internal data class SourcePrefs(
        val checkForUpdates: Boolean? = null,
        val hachimiRepo: String? = null,
        val mergeApks: Boolean? = null,
        val exportInternalDataProvider: Boolean? = null,
        val useInternalFilesDir: Boolean? = null,
        val appLibsVersion: String? = null,
        val useUniversalSigningKey: Boolean? = null
    )

    /**
     * Extracts only migratable keys. Skipped: install_method (different index
     * semantics), file URI prefs (grants belong to UPE's uid), dynamic_color
     * (opt-in here), and prefs with no HachiDroid equivalent.
     */
    internal fun parseSourcePrefs(source: Preferences): SourcePrefs = SourcePrefs(
        checkForUpdates = source[SRC_CHECK_FOR_UPDATES],
        hachimiRepo = source[SRC_HACHIMI_REPO]?.takeIf { it.isNotBlank() },
        mergeApks = source[SRC_MERGE_APKS],
        exportInternalDataProvider = source[SRC_EXPORT_INTERNAL_DATA_PROVIDER],
        useInternalFilesDir = source[SRC_USE_INTERNAL_FILES_DIR],
        appLibsVersion = source[SRC_HACHIMI_LIBS_VERSION]?.takeIf { it.isNotEmpty() },
        useUniversalSigningKey = source[SRC_USE_UNIVERSAL_SIGNING_KEY]
    )

    /** First source wins for every key (the fork is queried before upstream). */
    internal fun foldSourcePrefs(sources: List<SourcePrefs>): SourcePrefs =
        sources.reduce { acc, next ->
            SourcePrefs(
                checkForUpdates = acc.checkForUpdates ?: next.checkForUpdates,
                hachimiRepo = acc.hachimiRepo ?: next.hachimiRepo,
                mergeApks = acc.mergeApks ?: next.mergeApks,
                exportInternalDataProvider = acc.exportInternalDataProvider ?: next.exportInternalDataProvider,
                useInternalFilesDir = acc.useInternalFilesDir ?: next.useInternalFilesDir,
                appLibsVersion = acc.appLibsVersion ?: next.appLibsVersion,
                useUniversalSigningKey = acc.useUniversalSigningKey ?: next.useUniversalSigningKey
            )
        }

    // ------------------------------------------------------------- keystore I/O

    /**
     * Converts upstream's PKCS12 keystore to HachiDroid's BKS while preserving
     * the key and chain, so signatures stay compatible with an installed game.
     * Kept under UPE's password; ApkSigner falls back to it when signing.
     */
    internal fun convertPkcs12ToBks(pkcs12: File, dest: File) {
        Security.addProvider(BouncyCastleProvider())
        val passwords = listOf(KS_PASSWORD, HACHIDROID_KS_PASSWORD, "")
        var source: KeyStore? = null
        var keyPassword = KS_PASSWORD

        for (pw in passwords) {
            try {
                val candidate = KeyStore.getInstance("PKCS12")
                pkcs12.inputStream().use { candidate.load(it, pw.toCharArray()) }
                if (candidate.aliases().hasMoreElements()) {
                    source = candidate
                    keyPassword = pw
                    break
                }
            } catch (_: Exception) {}
        }

        if (source == null) {
            throw IllegalStateException("PKCS12 keystore could not be opened")
        }

        val alias = when {
            source.containsAlias(UNIVERSAL_KS_ALIAS) -> UNIVERSAL_KS_ALIAS
            source.aliases().hasMoreElements() -> source.aliases().nextElement()
            else -> throw IllegalStateException("Universal keystore contains no key")
        }
        val key = source.getKey(alias, keyPassword.toCharArray()) as? PrivateKey
            ?: throw IllegalStateException("Universal keystore key is not readable")
        val chain = source.getCertificateChain(alias)
            ?: throw IllegalStateException("Universal keystore certificate chain is missing")
        val converted = KeyStore.getInstance("BKS", "BC")
        converted.load(null, KS_PASSWORD.toCharArray())
        converted.setKeyEntry("alias", key, KS_PASSWORD.toCharArray(), chain)
        dest.outputStream().use { converted.store(it, KS_PASSWORD.toCharArray()) }
    }

    /** Verifies a BKS keystore the way ApkSigner.signApk() consumes it; returns the private key. */
    internal fun verifyBks(file: File): PrivateKey {
        Security.addProvider(BouncyCastleProvider())
        val ks = KeyStore.getInstance("BKS", "BC")
        file.inputStream().use { ks.load(it, null) }
        if (!ks.aliases().hasMoreElements()) {
            throw IllegalStateException("Keystore contains no alias")
        }
        val alias = ks.aliases().nextElement()
        for (pw in listOf(HACHIDROID_KS_PASSWORD, KS_PASSWORD, "")) {
            try {
                val key = ks.getKey(alias, pw.toCharArray()) as? PrivateKey
                if (key != null) return key
            } catch (_: Exception) {}
        }
        throw IllegalStateException("Keystore key entry is not usable")
    }

    /**
     * Validates and imports a signing keystore from a content [Uri].
     * Supports both BKS keystores (HachiDroid, UPE) and PKCS12 keystores (UPE universal key),
     * automatically converting PKCS12 to BKS before storing into [Context.ksFile].
     * Leaves existing keys intact if the imported file is invalid or unreadable.
     */
    suspend fun importKeystoreFromUri(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val stagingDir = context.cacheDir.resolve("keystore_import")
            stagingDir.mkdirs()
            val tempFile = File.createTempFile("imported_", ".tmp", stagingDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(IOException("Could not open file stream"))

                if (tempFile.length() == 0L) {
                    return@withContext Result.failure(IllegalArgumentException("The selected keystore file is empty"))
                }

                // 1. Try BKS directly
                try {
                    verifyBks(tempFile)
                    tempFile.copyTo(context.ksFile, overwrite = true)
                    return@withContext Result.success(Unit)
                } catch (bksEx: Exception) {
                    Log.d(TAG, "Not a valid BKS keystore, attempting PKCS12: ${bksEx.message}")
                }

                // 2. Try PKCS12 conversion
                try {
                    val converted = File(stagingDir, "converted_${System.currentTimeMillis()}.bks")
                    try {
                        convertPkcs12ToBks(tempFile, converted)
                        verifyBks(converted)
                        converted.copyTo(context.ksFile, overwrite = true)
                        return@withContext Result.success(Unit)
                    } finally {
                        converted.delete()
                    }
                } catch (p12Ex: Exception) {
                    Log.d(TAG, "Not a valid PKCS12 keystore either: ${p12Ex.message}")
                }

                Result.failure(IllegalArgumentException("Unsupported keystore format or unreadable key. Please select a valid BKS or PKCS12 keystore."))
            } finally {
                tempFile.delete()
                stagingDir.delete()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ----------------------------------------------------------------- migration

    /**
     * Runs the migration for every detected source; root required. Never touches
     * an existing HachiDroid key or library — only missing pieces are imported.
     */
    suspend fun migrate(context: Context, sources: List<DetectedSource>): MigrationSummary =
        withContext(Dispatchers.IO) {
            val isRoot = try { Shell.getShell().isRoot } catch (_: Exception) { false }
            require(isRoot || Shell.isAppGrantedRoot() == true) { "Root is required for automatic migration" }
            require(sources.isNotEmpty()) { "No UmaPatcher-Edge source to migrate from" }

            val labels = sources.joinToString(" and ") { it.label }
            val stagingDir = context.cacheDir.resolve("upe_migration")
            try {
                deleteStaging(stagingDir)
                stagingDir.mkdirs()

                // 1. Read each source's DataStore through a staged copy.
                val parsed = sources.mapNotNull { src ->
                    val staged = stagingDir.resolve("${src.packageName}_$CONFIG_PREFS_FILE")
                    stageFromRoot(sourcePath(src, "files/datastore/$CONFIG_PREFS_FILE"), staged)
                    if (staged.exists()) src to readSourcePrefs(staged) else null
                }

                // 2. Apply the settings whitelist (first source with values wins).
                var prefsApplied = 0
                var sourceLibVersion: String? = null
                if (parsed.isNotEmpty()) {
                    val effective = foldSourcePrefs(parsed.map { it.second })
                    var applied = 0
                    context.dataStore.edit { prefs ->
                        effective.checkForUpdates?.let { prefs[PrefKey.CHECK_FOR_UPDATES] = it; applied++ }
                        effective.hachimiRepo?.let { prefs[PrefKey.HACHIMI_REPO] = it; applied++ }
                        effective.mergeApks?.let { prefs[PrefKey.MERGE_APKS] = it; applied++ }
                        effective.exportInternalDataProvider?.let { prefs[PrefKey.EXPORT_INTERNAL_DATA_PROVIDER] = it; applied++ }
                        effective.useInternalFilesDir?.let { prefs[PrefKey.USE_INTERNAL_FILES_DIR] = it; applied++ }
                    }
                    prefsApplied += applied
                    sourceLibVersion = effective.appLibsVersion
                }

                // 3. Signing key - only when HachiDroid doesn't have one yet.
                var keystoreImported = false
                var keystoreCandidateFound = false
                if (!context.ksFile.exists()) {
                    sourceLoop@ for ((src, prefs) in parsed) {
                        val universal = prefs.useUniversalSigningKey == true
                        val candidates = if (universal) {
                            listOf(UNIVERSAL_KS_FILE_NAME, KS_FILE_NAME)
                        } else {
                            listOf(KS_FILE_NAME, UNIVERSAL_KS_FILE_NAME)
                        }
                        for (name in candidates) {
                            val staged = stagingDir.resolve("${src.packageName}_$name")
                            stageFromRoot(sourcePath(src, "files/$name"), staged)
                            if (!staged.exists()) continue
                            keystoreCandidateFound = true
                            try {
                                importKeystore(staged, name == UNIVERSAL_KS_FILE_NAME, context.ksFile)
                                keystoreImported = true
                                break@sourceLoop
                            } catch (e: Exception) {
                                Log.w(TAG, "Skipping unusable keystore $name from ${src.packageName}", e)
                            }
                        }
                    }
                    if (!keystoreImported && keystoreCandidateFound) {
                        throw IllegalStateException("Signing key could not be imported")
                    }
                }

                // 4. Hachimi arm64 library + its version pref (only if we have none).
                var modLibImported = false
                val ourLib = context.filesDir.resolve("libs").resolve(MOD_LIB_NAME)
                if (!ourLib.exists()) {
                    val sourceLib = parsed.firstNotNullOfOrNull { (src, _) ->
                        sourcePath(src, "files/libs/$MOD_LIB_NAME").takeIf { RootUtils.testFile(it) }
                    }
                    if (sourceLib != null) {
                        ourLib.parentFile?.mkdirs()
                        if (rootCopyInto(sourceLib, ourLib)) {
                            modLibImported = true
                            val version = sourceLibVersion
                                ?: parsed.firstNotNullOfOrNull { it.second.appLibsVersion }
                            if (version != null) {
                                context.dataStore.edit { it[PrefKey.HACHIMI_LIBS_VERSION] = version }
                                prefsApplied++
                            }
                        }
                    }
                }

                // 5. Plugins: copy missing files, append missing manifest entries.
                var pluginsImported = 0
                val manifest = HachimiPluginManager.listPlugins(context)
                for ((src, _) in parsed) {
                    val sourcePlugins = rootListDir(sourcePath(src, "files/plugins"))
                    if (sourcePlugins.isEmpty()) continue
                    if (!context.pluginsDir.exists()) context.pluginsDir.mkdirs()
                    val enabledBySource = readSourcePluginManifest(
                        stagingDir.resolve("${src.packageName}_plugins.json"),
                        src
                    )
                    for (fileName in sourcePlugins) {
                        val target = context.pluginsDir.resolve(fileName)
                        if (!target.exists() &&
                            !rootCopyInto(sourcePath(src, "files/plugins/$fileName"), target)
                        ) {
                            throw IllegalStateException("Failed to import plugin $fileName")
                        }
                        if (manifest.none { it.fileName == fileName }) {
                            manifest.add(
                                HachimiPluginEntry(
                                    name = fileName.removeSuffix(".so"),
                                    fileName = fileName,
                                    enabled = enabledBySource[fileName] ?: true
                                )
                            )
                            pluginsImported++
                        }
                    }
                }
                if (pluginsImported > 0) {
                    HachimiPluginManager.savePlugins(context, manifest)
                }

                val summary = MigrationSummary(
                    sourceLabels = labels,
                    prefsApplied = prefsApplied,
                    keystoreImported = keystoreImported,
                    modLibImported = modLibImported,
                    pluginsImported = pluginsImported
                )
                Log.i(TAG, "Migration from $labels: $summary")
                summary
            } finally {
                deleteStaging(stagingDir)
            }
        }

    // ------------------------------------------------------------------ internals

    // Absolute /data/data addresses the source app's files, not HachiDroid's own storage.
    @Suppress("SdCardPath")
    private fun sourcePath(source: DetectedSource, relative: String): String =
        "/data/data/${source.packageName}/$relative"

    private fun shellQuote(path: String): String = "'${path.replace("'", "'\"'\"'")}'"

    /**
     * Copies a file out of another app's data directory into our cache with root,
     * fixing ownership and the SELinux label. A missing source means nothing to
     * migrate and leaves [dest] untouched; an unreadable one is an error.
     */
    private fun stageFromRoot(srcPath: String, dest: File) {
        if (!RootUtils.testFile(srcPath)) return
        dest.parentFile?.mkdirs()
        val uid = Process.myUid()
        val q = ::shellQuote
        val result = Shell.cmd(
            "cp ${q(srcPath)} ${q(dest.path)} && chown $uid:$uid ${q(dest.path)} && " +
                "chmod 600 ${q(dest.path)} && { restorecon ${q(dest.path)} 2>/dev/null || true; }"
        ).exec()
        if (!result.isSuccess || !dest.canRead()) {
            dest.delete()
            throw IllegalStateException("Failed to read $srcPath")
        }
    }

    /** Root copy of a source file directly into one of HachiDroid's own files. */
    private fun rootCopyInto(srcPath: String, dest: File): Boolean {
        if (!RootUtils.testFile(srcPath)) return false
        val uid = Process.myUid()
        val q = ::shellQuote
        val result = Shell.cmd(
            "cp ${q(srcPath)} ${q(dest.path)} && chown $uid:$uid ${q(dest.path)} && " +
                "chmod 644 ${q(dest.path)} && { restorecon ${q(dest.path)} 2>/dev/null || true; }"
        ).exec()
        if (!result.isSuccess || !dest.canRead()) {
            dest.delete()
            throw IllegalStateException("Failed to import $srcPath")
        }
        return true
    }

    private fun rootListDir(dirPath: String): List<String> {
        if (!RootUtils.testDirectory(dirPath)) return emptyList()
        val result = Shell.cmd("ls -1 ${shellQuote(dirPath)} 2>/dev/null").exec()
        if (!result.isSuccess) return emptyList()
        return result.out.filter { it.isNotBlank() }
    }

    private suspend fun readSourcePrefs(file: File): SourcePrefs {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
            parseSourcePrefs(dataStore.data.first())
        } finally {
            scope.cancel()
        }
    }

    private fun importKeystore(staged: File, universal: Boolean, dest: File) {
        // Verify inside the staging dir first so a failed conversion never leaves a half-written keystore.
        val prepared = if (universal) {
            val converted = File(staged.parentFile, "${staged.name}.converted")
            convertPkcs12ToBks(staged, converted)
            verifyBks(converted)
            converted
        } else {
            verifyBks(staged)
            staged
        }
        prepared.copyTo(dest, overwrite = true)
        verifyBks(dest) // re-check the final location before trusting it
    }

    private fun readSourcePluginManifest(staged: File, source: DetectedSource): Map<String, Boolean> {
        stageFromRoot(sourcePath(source, "files/plugins.json"), staged)
        if (!staged.exists()) return emptyMap()
        return try {
            val listType = object : TypeToken<List<SourcePluginEntry>>() {}.type
            val entries: List<SourcePluginEntry>? = Gson().fromJson(staged.readText(), listType)
            entries.orEmpty()
                .filter { !it.fileName.isNullOrBlank() }
                .associate { it.fileName!! to (it.enabled ?: true) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse ${source.packageName} plugins.json", e)
            emptyMap()
        }
    }

    /** UPE's manifest entry with nullable fields: Gson leaves absent values null. */
    private data class SourcePluginEntry(
        val name: String? = null,
        val fileName: String? = null,
        val enabled: Boolean? = null
    )

    private fun deleteStaging(dir: File) {
        if (dir.exists()) dir.deleteRecursively()
        if (dir.exists()) RootUtils.removeDirectory(dir.path)
    }
}
