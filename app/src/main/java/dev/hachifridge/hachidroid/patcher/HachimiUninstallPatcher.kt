package dev.hachifridge.hachidroid.patcher

import android.content.Context
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.core.GameChecker
import java.io.File

/**
 * Removes Hachimi from the installed game without uninstalling the game: unmounts
 * legacy mounts, restores the original libmain, drops plugin libs and the
 * hachimi_internal_files marker. Save data and downloaded game data are kept.
 */
class HachimiUninstallPatcher : Patcher() {
    override val successMessageRes: Int = R.string.uninstall_success_msg
    override val failureMessageRes: Int = R.string.uninstall_failed_msg
    override val failureTitleRes: Int = R.string.uninstall_failed_title

    override suspend fun run(context: Context): Boolean {
        val packageInfo = GameChecker.getPackageInfo(context.packageManager) ?: run {
            log(context.getString(R.string.game_not_installed))
            return false
        }
        val appInfo = packageInfo.applicationInfo ?: return false

        // Unmount legacy mounts first so restored files aren't shadowed and nothing remounts the APK later.
        if (RootUtils.testDirectory(LEGACY_MOUNT_SCRIPT_DIR)) {
            task = context.getString(R.string.removing_legacy_files)
            progress = -1f

            if (AppPatcher.isApkMounted(context)) AppPatcher.unmountApk(context)
            RootUtils.removeDirectory(LEGACY_MOUNT_SCRIPT_DIR)
        }

        if (AppPatcher.isApkMounted(context)) {
            task = context.getString(R.string.uninstall_unmounting)
            progress = -1f

            if (!AppPatcher.unmountApk(context)) {
                log(context.getString(R.string.uninstall_unmount_failed))
                return false
            }
        }

        val apkDir = File(appInfo.publicSourceDir ?: return false).parentFile
        // Direct install patches lib/arm64, repacked installs keep lib/arm64-v8a
        val libDirs = listOf("lib/arm64", "lib/arm64-v8a")
            .mapNotNull { dir -> apkDir?.resolve(dir)?.takeIf { RootUtils.testDirectory(it.path) } }

        for (libDir in libDirs) {
            task = context.getString(R.string.uninstall_restoring_lib)
            progress = -1f

            val lib = libDir.resolve("libmain.so")
            val origLib = libDir.resolve("libmain_orig.so")
            if (RootUtils.testFile(origLib.path)) {
                if (!RootUtils.moveGameLibrary(origLib.path, lib.path).isSuccess) {
                    log(context.getString(R.string.uninstall_restore_failed))
                    return false
                }
                log(context.getString(R.string.uninstall_lib_restored))
            } else {
                log(context.getString(R.string.uninstall_lib_not_patched))
            }

            task = context.getString(R.string.uninstall_removing_plugins)
            progress = -1f

            val res = RootUtils.removeGamePlugins(libDir.path)
            if (res.isSuccess) {
                log(context.getString(R.string.uninstall_plugins_removed))
            } else {
                log(res.out.orEmpty().joinToString("\n"))
                log(res.err.orEmpty().joinToString("\n"))
            }
        }

        if (libDirs.isEmpty()) {
            log(context.getString(R.string.app_lib_dir_not_found))
        }

        // Drop a stale marker so it cannot affect future installs.
        val dataDir = appInfo.dataDir
        if (dataDir != null) {
            val marker = File(dataDir, "files").resolve(HachimiExtensionsPatcher.INTERNAL_FILES_MARKER_NAME)
            if (RootUtils.testFile(marker.path) && RootUtils.removeFile(marker.path).isSuccess) {
                log(context.getString(R.string.uninstall_marker_removed))
            }
        }

        return true
    }
}
