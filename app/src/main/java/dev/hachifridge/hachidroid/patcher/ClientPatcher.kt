package dev.hachifridge.hachidroid.patcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.format.Formatter
import dev.hachifridge.hachidroid.BuildConfig
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.core.GameChecker
import dev.hachifridge.hachidroid.client.Client
import dev.hachifridge.hachidroid.client.ShellDetection
import dev.hachifridge.hachidroid.core.PrefKey
import dev.hachifridge.hachidroid.core.getPrefValue
import dev.hachifridge.hachidroid.patcher.astraea.AstraeaApkPatcher
import dev.hachifridge.hachidroid.patcher.tracen.GlbApkPatcher
import dev.hachifridge.hachidroid.patcher.tracen.HachimiExtensionsPatcher
import dev.hachifridge.hachidroid.shizuku.ShizukuInstaller
import dev.hachifridge.hachidroid.shizuku.ShizukuState
import dev.hachifridge.hachidroid.utils.ksFile
import dev.hachifridge.hachidroid.utils.workDir
import dev.hachifridge.hachidroid.zip.ZipExtractor
import com.reandroid.apk.ApkModule
import com.reandroid.archive.Archive
import com.reandroid.archive.FileInputSource
import com.reandroid.archive.ZipEntryMap
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.progress.ProgressMonitor
import java.io.File
import java.util.Locale

private const val APK_ARM64_LIB_DIR = "lib/arm64-v8a"
private const val APK_ARM64_LIB_PATH = "$APK_ARM64_LIB_DIR/libmain.so"
private const val APK_ORIG_ARM64_LIB_PATH = "$APK_ARM64_LIB_DIR/libmain_orig.so"

internal const val LEGACY_MOUNT_SCRIPT_DIR = "/data/adb/umapatcher-edge"

private val Context.apkExtractDir: File
    get() = workDir.resolve("apk_extract")

private val Context.xapkExtractDir: File
    get() = workDir.resolve("xapk_extract")

class ClientPatcher(
    private val fileUris: Array<Uri>,
    private val install: Boolean,
    private val directInstall: Boolean,
    private val shizukuInstall: Boolean,
    private val legacyInstall: Boolean = false,
    /** Which client this run patches. Read once: the client cannot change mid-run. */
    private val client: Client = GameChecker.client
) : Patcher() {
    /**
     * Whether this run patches a packer-protected build. UM:PD's two releases differ here while being
     * one client, so it is decided from the picked files (or the installed package, for a direct
     * install) instead of from [client] alone.
     */
    private var shellProtected = false

    /** Version of the build being patched, when its manifest could be read. */
    private var targetStamp: String? = null

    /** Non-null when this run has to remove a native packer before the game can start. */
    private val shellPatcher: ShellSplitPatcher?
        get() = if (!shellProtected) null else when (client) {
            Client.ASTRAEA -> AstraeaApkPatcher
            Client.TRACEN -> GlbApkPatcher
        }

    /** The mod runtime this client patches in, wherever its source put it. */
    private fun modLib(context: Context): File = client.modSource.target(context)

    override suspend fun run(context: Context): Boolean {
        val startedAt = SystemClock.elapsedRealtime()
        log(
            "[env] " + (if (BuildConfig.DEBUG) "debug" else "release") +
                " build · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
        )
        try {
            val probe = ShellDetection.inspect(context, fileUris.toList())
            targetStamp = probe.versionStamp
            shellProtected = client.alwaysShellProtected ||
                probe.protected ||
                // The installed copy stands in only for a run with no picks (a direct install);
                // it must not decide for the files the user did pick.
                ShellDetection.installedTargetIsPacked(fileUris.isNotEmpty(), GameChecker.currentPackageName)
            log("· build: " + (if (shellProtected) "packer-protected" else "plain"))

            if (directInstall && !isDirectInstallAllowed(context))
                return false

            if (!directInstall && fileUris.isNotEmpty() && !checkStorageSpace(context))
                return false

            if (shellProtected) {
                // A packer-owned build can only be patched by rebuilding and reinstalling the
                // split set: root injection has nothing to inject into until the shell is gone.
                if (directInstall) {
                    log(context.getString(R.string.shell_no_direct_install))
                    return false
                }
                // The legacy path merges the rebuilt set and mounts it over the store copy, which
                // a shell-protected build has no stock Application to come back from. The studio
                // already hides the method; a deep link or a stale pick can still reach here.
                if (legacyInstall) {
                    log(context.getString(R.string.shell_no_legacy_install))
                    return false
                }
                log(context.getString(R.string.shell_install_replaces_store_copy))

                // The mod runtime is what makes a protected build run at all, so it is synced
                // before the checklist: a missing one must gate the run like any other library.
                val libVer = timed("sync mod library") { syncModLibrary(context) } ?: return false
                log(context.getString(R.string.using_app_lib_ver).format(libVer))

                if (!gateLibraries(context)) return false
            } else {
                val libVer = timed("sync mod library") { syncModLibrary(context) } ?: return false
                log(context.getString(R.string.using_app_lib_ver).format(libVer))

                if (directInstall)
                    return timed("direct install") { runHachimiDirectInstall(context) }
            }

            if (fileUris.size == 1) {
                val input = timed("copy input") { copyInputFiles(context) } ?: return false
                return timed("patch apk") { runXapkOrFullApk(context, input[0]) }
            }
            else if (fileUris.size > 1) {
                return timed("patch split apks") { runSplitApks(context) }
            }

            // Nothing to patch: say so, otherwise the run fails with an empty log.
            log(context.getString(R.string.patch_no_input_files))
            return false
        } finally {
            val total = (SystemClock.elapsedRealtime() - startedAt) / 1000f
            log("· summary: ${timingSummary()}")
            log("· total: ${String.format(Locale.ROOT, "%.2f", total)}s")
            onStage("total", null, total)
            if (isCancelled) {
                task = context.getString(R.string.cleaning_up)
            }
            dev.hachifridge.hachidroid.utils.deleteRecursive(context.workDir, deleteRoot = false)
        }
    }

    private fun checkStorageSpace(context: Context): Boolean {
        var totalSize = 0L
        var multiplier = if (fileUris.size > 1) 2 else 3
        for (uri in fileUris) {
            val cursor = try {
                context.contentResolver.query(uri, null, null, null, null)
            } catch (_: Exception) {
                continue
            }
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !it.isNull(sizeIndex))
                        totalSize += it.getLong(sizeIndex)
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else null
                    if (name != null && name.endsWith(".xapk", ignoreCase = true))
                        multiplier = 2
                }
            }
        }
        if (totalSize <= 0L) return true

        // Worst case: extracted files + rebuilt APK + signed copy (fewer for splits/xapk)
        val requiredBytes = totalSize * multiplier
        val availableBytes = StatFs(context.workDir.path).availableBytes
        if (availableBytes >= requiredBytes) return true

        val missingSpace = Formatter.formatFileSize(context, requiredBytes - availableBytes)
        log(context.getString(R.string.insufficient_storage).format(missingSpace))

        return false
    }

    private suspend fun runHachimiDirectInstall(context: Context): Boolean {
        if (RootUtils.testDirectory(LEGACY_MOUNT_SCRIPT_DIR)) {
            task = context.getString(R.string.removing_legacy_files)
            progress = -1f

            timed("clear legacy mount") {
                if (isApkMounted(context)) unmountApk(context)
                RootUtils.removeDirectory(LEGACY_MOUNT_SCRIPT_DIR)
            }
        }

        task = context.getString(R.string.installing)
        progress = -1f

        val hachimiModLib = modLib(context)

        val packageInfo = GameChecker.getPackageInfo(context.packageManager) ?: return false
        RootUtils.killPackage(packageInfo.packageName)
        val publicSourceDir = packageInfo.applicationInfo?.publicSourceDir ?: return false
        val appApkDir = File(publicSourceDir).parentFile ?: return false

        val arm64LibDir = appApkDir.resolve("lib/arm64")

        if (!RootUtils.testDirectory(arm64LibDir.path)) {
            log(context.getString(R.string.app_lib_dir_not_found))
            return false
        }

        val installed = timed("install mod library") { installHachimiModLib(hachimiModLib, arm64LibDir) }
        if (!installed) return false

        timed("install plugins") { installPlugins(context, arm64LibDir, isDirectInstall = true) }
        timed("internal files marker") { syncHachimiInternalFilesMarker(context, packageInfo) }

        return true
    }

    private fun installHachimiModLib(modLib: File, libDir: File): Boolean {
        val lib = libDir.resolve("libmain.so")
        val origLib = libDir.resolve("libmain_orig.so")
        if (!RootUtils.testFile(origLib.path)) {
            RootUtils.moveGameLibrary(lib.path, origLib.path)
                .isSuccess || return false
        }
        RootUtils.copyGameLibrary(modLib.path, lib.path)
            .isSuccess || return false

        return true
    }

    private suspend fun runXapkOrFullApk(context: Context, file: File): Boolean {
        val fileHeaders = ZipFile(file).use { it.fileHeaders }
        val zip = ZipExtractor.maybeMapped(file)

        // Detect file type without extracting first
        for (header in fileHeaders) {
            if (header.fileName.endsWith(".apk")) {
                log(context.getString(R.string.detected_xapk_file))
                return runXapk(context, zip)
            }
            else if (header.fileName == "classes.dex") {
                log(context.getString(R.string.detected_apk_file))
                return runFullApk(context, zip)
            }
        }

        zip.close()
        log(context.getString(R.string.invalid_apk_file))
        return false
    }

    private suspend fun runXapk(context: Context, zip: ZipExtractor): Boolean {
        val extractDir = context.xapkExtractDir
        timed("extract", zip.file.name) { extractZipWithProgress(context, zip, extractDir) }
        zip.close()
        zip.file.delete()

        try {
            val apkFiles = extractDir.listFiles { _, name -> name.endsWith(".apk") } ?: return false
            if (!classifyFromFiles(context, apkFiles)) return false
            if (!patchSplitApks(context, apkFiles))
                return false

            return when {
                shizukuInstall -> installApksShizuku(context, apkFiles)
                legacyInstall -> timed("merge apks") { mergeAndInstallLegacy(context, apkFiles) }
                install -> installApks(context, apkFiles)
                else -> {
                    val mergeApks = context.getPrefValue(PrefKey.MERGE_APKS) as Boolean
                    if (mergeApks) mergeAndSaveApk(context, apkFiles)
                    else createAndSaveXapk(context, apkFiles)
                }
            }
        }
        catch (ex: Exception) {
            logException(ex)
            return false
        }
        finally {
            task = context.getString(R.string.cleaning_up)
            progress = -1f

            timed("cleanup") {
                extractDir.deleteRecursively()
                context.apkExtractDir.deleteRecursively()
            }
        }
    }

    private suspend fun runFullApk(context: Context, zip: ZipExtractor): Boolean {
        try {
            if (!classifyFromFiles(context, arrayOf(zip.file))) return false
            if (!patchApk(context, zip))
                return false

            return when {
                shizukuInstall -> installApksShizuku(context, arrayOf(zip.file))
                legacyInstall -> installLegacyOrFallback(context, zip.file)
                install -> installApks(context, arrayOf(zip.file))
                else -> {
                    val success = saveFile("patched-${System.currentTimeMillis()}.apk", zip.file)
                    log(
                        if (success) context.getString(R.string.file_saved)
                        else context.getString(R.string.failed_to_save_file)
                    )
                    success
                }
            }
        }
        catch (ex: Exception) {
            logException(ex)
            return false
        }
        finally {
            task = context.getString(R.string.cleaning_up)
            progress = -1f

            zip.close()
            zip.file.delete()
            timed("cleanup") { context.apkExtractDir.deleteRecursively() }
        }
    }

    private suspend fun runSplitApks(context: Context): Boolean {
        log(context.getString(R.string.patching_as_split_apks))
        val files = timed("copy input") { copyInputFiles(context, ".apk") } ?: return false
        try {
            if (!classifyFromFiles(context, files)) return false
            if (!patchSplitApks(context, files))
                return false

            return when {
                shizukuInstall -> installApksShizuku(context, files)
                legacyInstall -> timed("merge apks") { mergeAndInstallLegacy(context, files) }
                install -> installApks(context, files)
                else -> {
                    val mergeApks = context.getPrefValue(PrefKey.MERGE_APKS) as Boolean
                    if (mergeApks) mergeAndSaveApk(context, files)
                    else createAndSaveXapk(context, files)
                }
            }
        }
        catch (ex: Exception) {
            logException(ex)
            return false
        }
        finally {
            task = context.getString(R.string.cleaning_up)
            progress = -1f

            files.forEach { it.delete() }
            context.apkExtractDir.deleteRecursively()
        }
    }

    private fun copyInputFiles(context: Context, ext: String = ""): Array<File>? {
        return Array(fileUris.size) {
            val filename = "file$it$ext"
            val file = context.workDir.resolve(filename)

            task = if (fileUris.size > 1) context.getString(R.string.copying_file_name).format(filename)
                else context.getString(R.string.copying_file)
            progress = -1f

            try {
            val input = try {
                context.contentResolver.openInputStream(fileUris[it])
            } catch (ex: Exception) {
                logException(ex)
                null
            }
            input.use { stream ->
                if (stream == null) {
                    log(context.getString(R.string.failed_to_read_file).format(filename))
                    return null
                }

                file.outputStream().use { output ->
                    stream.copyTo(output)
                }
            }
            } catch (ex: Exception) {
                log(context.getString(R.string.failed_to_read_file).format(filename))
                logException(ex)
                return null
            }

            if (!file.exists() || file.length() <= 0L) {
                log(context.getString(R.string.failed_to_read_file).format(filename))
                return null
            }

            file
        }
    }

    private enum class SplitApkType {
        BASE,
        CONFIG_ARM64
    }

    private suspend fun patchSplitApks(context: Context, files: Array<File>): Boolean {
        patchUnitCount = files.size
        val extractDir = context.apkExtractDir
        val res = run {
            var success = true
            useZipExtractors(files) { zipFiles ->
                val processedSplits = mutableMapOf<SplitApkType, Boolean>()
                for (zip in zipFiles) {
                    try {
                        if (!patchApk(context, zip, processedSplits)) {
                            success = false
                            return@useZipExtractors
                        }
                    }
                    catch (ex: Exception) {
                        logException(ex)
                        success = false
                        return@useZipExtractors
                    }
                }

                if (processedSplits[SplitApkType.BASE] != true) {
                    log(context.getString(R.string.apk_splits_missing_base))
                    success = false
                }

                if (processedSplits[SplitApkType.CONFIG_ARM64] != true) {
                    log(context.getString(R.string.apk_files_missing_lib))
                    success = false
                }
            }

            success
        }

        extractDir.deleteRecursively()
        return res
    }

    private inline fun useZipExtractors(files: Array<File>, callback: (Array<ZipExtractor>) -> Unit) {
        val zipFiles = Array(files.size) {
            ZipExtractor.maybeMapped(files[it])
        }
        callback(zipFiles)
        zipFiles.forEach { it.close() }
    }

    private suspend fun extractZipWithProgress(context: Context, zip: ZipExtractor, extractDir: File) {
        task = context.getString(R.string.extracting_file).format(zip.file.name)
        progress = -1f

        extractDir.deleteRecursively()
        zip.extractAll(extractDir, { ensureActive() }) { progress = it }
    }

    private suspend fun monitorZipProgress(zip: ZipFile) {
        val progressMonitor = zip.progressMonitor
        try {
            while (!progressMonitor.state.equals(ProgressMonitor.State.READY)) {
                ensureActive()
                progress = progressMonitor.percentDone / 100f
                delay(100)
            }
        } catch (ex: kotlinx.coroutines.CancellationException) {
            progressMonitor.isCancelAllTasks = true
            throw ex
        }
    }

    private suspend fun patchApk(
        context: Context,
        zip: ZipExtractor,
        processedSplits: MutableMap<SplitApkType, Boolean>? = null
    ): Boolean {
        val doNotCompress = zip.fileHeaders
            .filter { it.compressionMethod == CompressionMethod.STORE }
            .map { it.fileName }
            .toSet() + setOf(APK_ORIG_ARM64_LIB_PATH) +
            // The libraries the patch writes are new entries, so the source archive says nothing
            // about them; they must still land stored for the 16 KB alignment to hold.
            (shellPatcher?.let { patcher ->
                patcher.archivePaths.toSet() +
                    client.plugins.enabledPluginFiles(context)
                        .map { patcher.archivePath(client.plugins.prefixedName(it.name)) }
                        .toSet()
            } ?: emptySet<String>())

        val file = zip.file
        val filename = file.name

        val extractDir = context.apkExtractDir
        timed("extract", filename) { extractZipWithProgress(context, zip, extractDir) }
        zip.close()
        file.delete()

        if (!extractDir.resolve("AndroidManifest.xml").isFile) {
            log(context.getString(R.string.invalid_apk_file_name).format(filename))
            return false
        }

        task = context.getString(R.string.patching)
        progress = -1f

        // A packer-protected client shares everything around the patch (extract, rebuild, 16 KB
        // alignment, signing, install) and nothing inside it, so the paths split here and rejoin
        // at rebuildAndSignApk.
        shellPatcher?.let { patcher ->
            if (!patchShellSplit(patcher, context, extractDir, filename, processedSplits)) return false
            return rebuildAndSignApk(context, filename, file, doNotCompress, extractDir)
        }

        // A stub here means the build is packed after all. Writing a Hachimi loader beside an
        // encrypted library would install and then die at startup, with nothing in the log.
        if (ShellDetection.hasPackerStub(extractDir)) {
            log(context.getString(R.string.shell_build_needs_split_patch))
            return false
        }

        val arm64Lib = extractDir.resolve(APK_ARM64_LIB_PATH)
        val classesDex = extractDir.resolve("classes.dex")
        var processed = false
        var libPatched = false

        fun isInvalidSplit(splitType: SplitApkType): Boolean {
            if (processedSplits == null) return false
            log(context.getString(R.string.detected_apk_split).format(splitType.name))

            if (processed) {
                log(context.getString(R.string.invalid_split_apk_multiple_type))
                return true
            }

            if (processedSplits[splitType] == true) {
                log(
                    context.getString(R.string.invalid_apk_splits_duplicate)
                        .format(splitType.name)
                )
                return true
            }

            return false
        }

        fun patchArchLibs(
            splitType: SplitApkType,
            lib: File,
            modLib: File,
            origLibPath: String
        ): Boolean {
            if (isInvalidSplit(splitType)) return false

            // Keep an existing orig lib so an already-patched APK stays updatable.
            val origLib = extractDir.resolve(origLibPath)
            if (!origLib.exists()) {
                lib.renameTo(origLib)
            }
            modLib.copyTo(lib)

            log(context.getString(R.string.libraries_patched).format(lib.parentFile!!.name))
            processed = true
            libPatched = true
            processedSplits?.put(splitType, true)

            return true
        }

        if (arm64Lib.exists()) {
            timed("patch libraries", filename) {
                patchArchLibs(
                    splitType = SplitApkType.CONFIG_ARM64,
                    lib = arm64Lib,
                    modLib = modLib(context),
                    origLibPath = APK_ORIG_ARM64_LIB_PATH
                )
            } || return false
            installPlugins(context, arm64Lib.parentFile!!)
        }

        if (classesDex.exists()) {
            val splitType = SplitApkType.BASE
            if (isInvalidSplit(splitType)) return false
            processed = true
            processedSplits?.put(splitType, true)
        }

        if (!processed) {
            log(context.getString(R.string.invalid_apk_file_name).format(filename))
            return false
        }

        if (processedSplits == null && !libPatched) {
            log(context.getString(R.string.apk_files_missing_lib))
            return false
        }

        timed("patch extensions", filename) { patchHachimiExtensions(context, extractDir) }

        return rebuildAndSignApk(context, filename, file, doNotCompress, extractDir)
    }

    /**
     * Repacks the extracted split with 16 KB-aligned `.so` entries and signs it in place. Shared by
     * both modes: the alignment is what lets the patched libraries load on Android 16+, and the
     * signature is what makes the result installable.
     */
    private suspend fun rebuildAndSignApk(
        context: Context,
        filename: String,
        file: File,
        doNotCompress: Set<String>,
        extractDir: File
    ): Boolean {
        task = context.getString(R.string.creating_file).format(filename)
        progress = -1f

        val zipEntryMap = ZipEntryMap()
        val prefixLen = extractDir.canonicalPath.length + 1
        var fileCount = 0
        timed("index apk entries", filename) {
            extractDir.walkTopDown().forEach { child ->
                if (child.isFile) {
                    val name = child.canonicalPath.substring(prefixLen)
                    val inputSource = FileInputSource(child, name).apply {
                        if (doNotCompress.contains(name)) method = Archive.STORED
                    }
                    zipEntryMap.add(inputSource)
                    ++fileCount
                }
            }
        }

        val apk = ApkModule(zipEntryMap)
        var writtenFiles = 0
        val apkWriter = apk.createApkFileWriter(file)
        val signedApkFile = context.workDir.resolve("tmp_signed.apk")

        try {
            timed("rebuild apk", filename) {
                apkWriter.setWriteProgress { path, _, _ ->
                    log(path)
                    progress = ++writtenFiles / fileCount.toFloat()
                }
                val aligner = com.reandroid.archive.writer.ZipAligner.apkAligner()
                aligner.setFileAlignment(
                    java.util.regex.Pattern.compile(".*\\.so$"),
                    16384
                )
                apkWriter.zipAligner = aligner
                apkWriter.write()
                apkWriter.close()
            }

            task = context.getString(R.string.signing_apk_file).format(filename)
            progress = -1f

            val apkSigner = ApkSigner("HachiDroid")
            timed("sign apk", filename) { apkSigner.signApk(file, signedApkFile, context.ksFile) }
            if (!signedApkFile.renameTo(file)) {
                log(context.getString(R.string.failed_to_move_file).format(signedApkFile.name))
                return false
            }
            return true
        } catch (ex: Exception) {
            logException(ex)
            signedApkFile.delete()
            return false
        } finally {
            try {
                apkWriter.close()
            } catch (_: Exception) {
            }
            signedApkFile.delete()
        }
    }

    /**
     * Applies a shell-protected client's patch to whichever part of the app this APK holds. A
     * merged APK carries both payloads and gets both in one pass; in a split set the base and the
     * arm64 config split each arrive separately.
     */
    private suspend fun patchShellSplit(
        patcher: ShellSplitPatcher,
        context: Context,
        extractDir: File,
        filename: String,
        processedSplits: MutableMap<SplitApkType, Boolean>?
    ): Boolean {
        val isBase = patcher.isBaseSplit(extractDir)
        val isConfig = patcher.isConfigSplit(extractDir)

        if (!isBase && !isConfig) {
            log(context.getString(R.string.invalid_apk_file_name).format(filename))
            return false
        }

        fun markSplit(type: SplitApkType): Boolean {
            log(context.getString(R.string.detected_apk_split).format(type.name))
            if (processedSplits == null) return true
            if (processedSplits[type] == true) {
                log(context.getString(R.string.invalid_apk_splits_duplicate).format(type.name))
                return false
            }
            processedSplits[type] = true
            return true
        }

        if (isBase) {
            if (!markSplit(SplitApkType.BASE)) return false

            val applicationDex = try {
                context.assets.open(HachimiExtensionsPatcher.DEX_ASSET_NAME).use { it.readBytes() }
            } catch (ex: Exception) {
                log(context.getString(R.string.shell_assets_missing))
                logException(ex)
                return false
            }

            val libsDir = client.libraries.directory(context)
            val patched = timed("patch manifest", filename) {
                patcher.patchBaseSplit(extractDir, libsDir, applicationDex) { line -> log(line) }
            }
            if (!patched) return false

            // The base split owns the application entry point, so the Hachimi extensions that
            // live there (internal-files marker, DocumentsProvider) belong in the same pass.
            // Priconne runs DaringTact instead of Hachimi, so this stays Tracen-only.
            if (client == Client.TRACEN) {
                timed("patch extensions", filename) { patchHachimiExtensions(context, extractDir) }
            }
        }

        if (isConfig) {
            if (!markSplit(SplitApkType.CONFIG_ARM64)) return false

            val libsDir = client.libraries.directory(context)
            val plugins = client.plugins.enabledPluginFiles(context)
                .map { client.plugins.prefixedName(it.name) to it }
            val patched = timed("patch libraries", filename) {
                patcher.patchConfigSplit(extractDir, libsDir, plugins) { line -> log(line) }
            }
            if (!patched) return false
        }

        return true
    }

    /**
     * Settles the build's shape from the files themselves, now that they are on local storage and
     * their entry lists can be read without streaming them. An XAPK is why this exists: its outer
     * archive carries no manifest and no stub, so only the extracted splits can say what it is.
     *
     * A packed build also needs its restored libraries before anything is written, so the gate is
     * re-run here whenever that answer arrives late.
     */
    private fun classifyFromFiles(context: Context, files: Array<File>): Boolean {
        val info = ShellDetection.inspectFiles(files.toList())
        // The copied files are the build that will be patched, so their version is the one the
        // restored libraries have to match.
        if (info.versionStamp != null) targetStamp = info.versionStamp
        val packed = client.alwaysShellProtected || info.protected
        if (packed == shellProtected) return true

        shellProtected = packed
        log("· build: " + (if (packed) "packer-protected" else "plain"))
        return if (packed) gateLibraries(context) else true
    }

    /** Refuses the run when a library the patch writes is not on disk yet. */
    private fun gateLibraries(context: Context): Boolean {
        // A restored image and metadata only match the build they were unpacked from; installing
        // them over another version fails at startup, so say which one is on disk first.
        val store = client.libraries
        if (store.isStale(context, targetStamp)) {
            log(
                context.getString(
                    R.string.libraries_stale_warning,
                    store.readStamp(context).orEmpty(),
                    targetStamp.orEmpty()
                )
            )
        }

        val missing = missingLibraries(context)
        if (missing.isEmpty()) return true
        log(
            context.getString(R.string.shell_libraries_incomplete)
                .format(missing.joinToString(", "))
        )
        return false
    }

    /** Libraries the run needs but the user has not imported or unpacked yet. */
    private fun missingLibraries(context: Context): List<String> {
        val dir = client.libraries.directory(context)
        return client.libraries.requiredLibraries(shellProtected).filter { name ->
            val lib = dir.resolve(name)
            !lib.isFile || lib.length() <= 0L
        }
    }

    private suspend fun patchHachimiExtensions(context: Context, extractDir: File) {
        val useInternalFilesDir = context.getPrefValue(PrefKey.USE_INTERNAL_FILES_DIR) as Boolean
        if (useInternalFilesDir && HachimiExtensionsPatcher.addInternalFilesMarker(extractDir))
            log(context.getString(R.string.internal_files_marker_added))

        if (!(context.getPrefValue(PrefKey.EXPORT_INTERNAL_DATA_PROVIDER) as Boolean)) return

        task = context.getString(R.string.patching_documents_provider)
        progress = -1f

        try {
            val providerDex = context.assets
                .open(HachimiExtensionsPatcher.DEX_ASSET_NAME)
                .use { it.readBytes() }
            val injected = HachimiExtensionsPatcher.patchExtractedApk(extractDir, providerDex)
            log(context.getString(
                if (injected) R.string.documents_provider_patched
                else R.string.documents_provider_already_patched
            ))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(context.getString(R.string.documents_provider_patch_failed))
            logException(e)
        }
    }

    private suspend fun syncHachimiInternalFilesMarker(context: Context, packageInfo: PackageInfo) {
        val dataDir = packageInfo.applicationInfo?.dataDir ?: return
        val filesDir = File(dataDir, "files")
        val marker = filesDir.resolve(HachimiExtensionsPatcher.INTERNAL_FILES_MARKER_NAME)

        if (context.getPrefValue(PrefKey.USE_INTERNAL_FILES_DIR) as Boolean) {
            val uid = packageInfo.applicationInfo!!.uid.toString()
            val fileContext = RootUtils.getFileContext(dataDir)

            if (RootUtils.testFile(marker.path)) {
                if (fileContext != null) RootUtils.chcon(marker.path, fileContext)
                return
            }

            if (!RootUtils.testDirectory(filesDir.path)) {
                if (RootUtils.createDirectory(filesDir.path).isSuccess) {
                    RootUtils.chown(filesDir.path, "$uid:$uid")
                    RootUtils.chmod(filesDir.path, "700")
                    if (fileContext != null) RootUtils.chcon(filesDir.path, fileContext)
                }
            }

            val res = RootUtils.createFile(marker.path)
            if (res.isSuccess) {
                RootUtils.chown(marker.path, "$uid:$uid")
                RootUtils.chmod(marker.path, "644")
                if (fileContext != null) RootUtils.chcon(marker.path, fileContext)
                log(context.getString(R.string.internal_files_marker_added))
            } else {
                var reason = res.out.orEmpty().joinToString(" ").trim()
                if (reason.isEmpty()) reason = "exit ${res.code}"
                log(context.getString(R.string.internal_files_marker_failed).format(reason))
            }
        } else if (RootUtils.testFile(marker.path)) {
            RootUtils.removeFile(marker.path)
        }
    }

    private fun installPlugins(context: Context, libDir: File, isDirectInstall: Boolean = false) {
        val plugins = client.plugins.enabledPluginFiles(context)
        if (plugins.isEmpty()) return

        val installedNames = mutableListOf<String>()
        for (plugin in plugins) {
            val destName = client.plugins.prefixedName(plugin.name)
            val dest = libDir.resolve(destName)
            try {
                val success = if (isDirectInstall) {
                    RootUtils.copyGameLibrary(plugin.path, dest.path).isSuccess
                } else {
                    plugin.copyTo(dest, overwrite = true)
                    true
                }

                if (success) {
                    installedNames.add(destName)
                } else {
                    log(context.getString(R.string.failed_to_install_plugin).format(plugin.name))
                }
            } catch (_: Exception) {
                log(context.getString(R.string.failed_to_install_plugin).format(plugin.name))
            }
        }

        if (installedNames.isNotEmpty()) {
            log(context.getString(R.string.plugins_patched).format(installedNames.joinToString(", ")))
        }
    }

    private suspend fun installApks(context: Context, files: Array<File>): Boolean {
        task = context.getString(R.string.staging_app)
        progress = -1f

        val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        sessionParams.setInstallLocation(PackageInfo.INSTALL_LOCATION_AUTO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sessionParams.setInstallReason(PackageManager.INSTALL_REASON_USER)
        }

        val packageInstaller = context.packageManager.packageInstaller
        val sessionId: Int = packageInstaller.createSession(sessionParams)

        packageInstaller.openSession(sessionId).use { session ->
            try {
                timed("stage apk") {
                    var i = 0
                    for (file in files) {
                        val length = file.length()
                        file.inputStream().use { input ->
                            session.openWrite("${i++}.apk", 0, length).use { output ->
                                log(context.getString(R.string.copying_file_name).format(file.name))
                                copyStreamProgress(input, output, length)
                                progress = -1f
                                session.fsync(output)
                            }
                        }
                    }
                }

                task = context.getString(R.string.installing)

                val callbackIntent = Intent(context, PackageInstallerStatusReceiver::class.java)
                val pendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        0,
                        callbackIntent,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                    )
                session.commit(pendingIntent.intentSender)
            }
            catch (ex: Exception) {
                log(context.getString(R.string.install_failed))
                logException(ex)
                session.abandon()
                return false
            }
        }

        val success = timed("package install") {
            PackageInstallerStatusReceiver.waitForInstallFinish()
        }
        log(context.getString(
            if (success) R.string.install_completed
            else R.string.install_failed
        ))
        return success
    }

    private suspend fun installApksShizuku(context: Context, files: Array<File>): Boolean {
        if(!ShizukuState.isAvailable.value) {
            log(context.getString(R.string.shizuku_unavailable))
            return false
        }
        return timed("shizuku install") { ShizukuInstaller.install(context, files, this) }
    }

    private suspend fun createAndSaveXapk(context: Context, files: Array<File>): Boolean {
        task = context.getString(R.string.creating_file).format("patched.xapk")
        progress = -1f

        val xapkFile = context.workDir.resolve("patched.xapk")
        xapkFile.delete()

        val zip = ZipFile(xapkFile)
        zip.isRunInThread = true
        zip.addFiles(files.toMutableList(), ZipParameters().apply {
            compressionMethod = CompressionMethod.STORE
        })
        monitorZipProgress(zip)
        zip.close()

        val success = saveFile("patched-${System.currentTimeMillis()}.xapk", xapkFile)
        log(
            if (success) context.getString(R.string.file_saved)
            else context.getString(R.string.failed_to_save_file)
        )

        task = context.getString(R.string.cleaning_up)
        progress = -1f
        xapkFile.delete()

        return success
    }

    /**
     * Merges the split APKs into one signed APK in the work dir. Returns null after logging when
     * merging or signing fails, so the save and legacy-install paths share a single pipeline.
     */
    private suspend fun mergeSplitApks(context: Context, files: Array<File>): File? {
        task = context.getString(R.string.merging_apks)
        progress = -1f

        val mergedApk = context.workDir.resolve("merged.apk")
        mergedApk.delete()

        var mergeResult: SplitApkMerger.Result? = null
        try {
            val logger = object : com.reandroid.apk.APKLogger {
                override fun logMessage(msg: String?) { if (!msg.isNullOrEmpty()) log(msg) }
                override fun logError(msg: String?, tr: Throwable?) {
                    if (!msg.isNullOrEmpty()) log(msg)
                    if (tr != null) logException(tr as? Exception ?: RuntimeException(tr))
                }
                override fun logVerbose(msg: String?) { }
            }

            mergeResult = SplitApkMerger.merge(files, logger)

            task = context.getString(R.string.merge_sanitizing_manifest)
            progress = -1f
            task = context.getString(R.string.merge_writing_apk)
            progress = 0f
            val totalFiles = mergeResult.merged.zipEntryMap.toArray(true).size.coerceAtLeast(1)
            var written = 0
            mergeResult.merged.writeApk(mergedApk) { _, _, _ ->
                progress = (++written).toFloat() / totalFiles
            }
        } catch (ex: SplitApkMerger.MergeException) {
            log(ex.message ?: context.getString(R.string.merge_no_base))
            return null
        } catch (ex: Exception) {
            log(context.getString(R.string.merge_failed).format(ex.message ?: ex.javaClass.simpleName))
            logException(ex)
            return null
        } finally {
            mergeResult?.let { r ->
                runCatching { r.merged.close() }
                r.inputModules.forEach { runCatching { it.close() } }
            }
        }

        return try {
            task = context.getString(R.string.signing_apk_file).format(mergedApk.name)
            progress = -1f

            val signedApkFile = context.workDir.resolve("tmp_signed_merged.apk")
            val apkSigner = ApkSigner("HachiDroid")
            apkSigner.signApk(mergedApk, signedApkFile, context.ksFile)
            if (!signedApkFile.renameTo(mergedApk)) {
                log(context.getString(R.string.failed_to_move_file).format(signedApkFile.name))
                return null
            }
            mergedApk
        } catch (ex: Exception) {
            logException(ex)
            null
        }
    }

    private suspend fun mergeAndSaveApk(context: Context, files: Array<File>): Boolean {
        val mergedApk = mergeSplitApks(context, files) ?: return false
        return try {
            val success = saveFile(
                "patched-${System.currentTimeMillis()}.apk",
                mergedApk
            )
            log(
                if (success) context.getString(R.string.file_saved)
                else context.getString(R.string.failed_to_save_file)
            )
            success
        } catch (ex: Exception) {
            logException(ex)
            false
        } finally {
            task = context.getString(R.string.cleaning_up)
            progress = -1f
            mergedApk.delete()
            context.workDir.resolve("tmp_signed_merged.apk").delete()
        }
    }

    private suspend fun mergeAndInstallLegacy(context: Context, files: Array<File>): Boolean {
        val mergedApk = mergeSplitApks(context, files) ?: return false
        return try {
            val launched = installLegacyOrFallback(context, mergedApk)
            log(
                if (launched) context.getString(R.string.install_completed)
                else context.getString(R.string.legacy_install_failed)
            )
            launched
        } catch (ex: Exception) {
            logException(ex)
            false
        }
    }

    private suspend fun installLegacyOrFallback(context: Context, apk: File): Boolean {
        task = context.getString(R.string.installing)
        progress = -1f

        val launched = try {
            timed("legacy install") { installLegacy(apk) }
        } catch (ex: Exception) {
            logException(ex)
            false
        }

        if (launched) return true

        log(context.getString(R.string.legacy_install_failed))
        return installApks(context, arrayOf(apk))
    }

    /**
     * Makes sure the client's mod runtime is on disk before it is written. The source is the one
     * the Mod Source sheet manages: GitHub releases, or the local file the user picked.
     */
    private suspend fun syncModLibrary(context: Context): String? {
        task = context.getString(R.string.syncing_app_libs)
        progress = -1f
        return client.modSource.ensure(context, ::log) { fraction -> progress = fraction }
    }

    companion object {
        fun isApkMounted(context: Context): Boolean {
            if (!RootUtils.isRootOperationAllowed(context)) return false
            val packageInfo = GameChecker.getPackageInfo(context.packageManager)!!
            val packageName = packageInfo.packageName
            val res = Shell.cmd("grep $packageName /proc/mounts").exec()
            return res.out.joinToString("").isNotEmpty()
        }

        fun unmountApk(context: Context): Boolean {
            if (!RootUtils.isRootOperationAllowed(context)) return false
            val packageInfo = GameChecker.getPackageInfo(context.packageManager)!!
            val packageName = packageInfo.packageName
            return Shell.cmd(
                "grep $packageName /proc/mounts | while read -r line; do echo ${'$'}line | cut -d \" \" -f 2 | sed \"s/apk.*/apk/\" | xargs -r umount -l; done"
            ).exec().isSuccess
        }
    }
}
