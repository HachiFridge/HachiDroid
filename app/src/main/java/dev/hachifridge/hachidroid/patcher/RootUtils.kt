package dev.hachifridge.hachidroid.patcher

import android.content.Context
import dev.hachifridge.hachidroid.core.GameChecker
import com.topjohnwu.superuser.Shell

object RootUtils {
    private fun shellArg(path: String): String = "'${path.replace("'", "'\"'\"'")}'"

    fun isRootAvailable(): Boolean = Shell.isAppGrantedRoot() == true

    fun isRootOperationAllowed(context: Context): Boolean {
        return isRootAvailable() && GameChecker.isPackageInstalled(context.packageManager)
    }

    fun testFile(path: String): Boolean {
        return Shell.cmd("test -f ${shellArg(path)}").exec().isSuccess
    }

    fun testDirectory(path: String): Boolean {
        return Shell.cmd("test -d ${shellArg(path)}").exec().isSuccess
    }

    fun createDirectory(path: String): Shell.Result {
        return Shell.cmd(
            "mkdir -p ${shellArg(path)}"
        ).exec()
    }

    fun removeDirectory(path: String): Shell.Result {
        return Shell.cmd(
            "rm -rf ${shellArg(path)}"
        ).exec()
    }

    fun createFile(path: String): Shell.Result {
        return Shell.cmd(
            "touch ${shellArg(path)} 2>&1"
        ).exec()
    }

    fun removeFile(path: String): Shell.Result {
        return Shell.cmd(
            "rm -f ${shellArg(path)}"
        ).exec()
    }

    fun moveFile(src: String, dest: String): Shell.Result {
        return Shell.cmd(
            "mv ${shellArg(src)} ${shellArg(dest)}"
        ).exec()
    }

    fun chmod(path: String, perm: String): Shell.Result {
        return Shell.cmd(
            "chmod $perm ${shellArg(path)}"
        ).exec()
    }

    fun chown(path: String, owner: String): Shell.Result {
        return Shell.cmd(
            "chown $owner ${shellArg(path)}"
        ).exec()
    }

    fun getFileContext(path: String): String? {
        val res = Shell.cmd("ls -Zd ${shellArg(path)} 2>/dev/null").exec()
        return res.out.orEmpty().firstOrNull()?.trim()
            ?.split(" ")?.firstOrNull { ":" in it }
    }

    fun chcon(path: String, con: String): Shell.Result {
        return Shell.cmd(
            "chcon ${shellArg(con)} ${shellArg(path)}"
        ).exec()
    }

    fun restoreContext(path: String): Shell.Result {
        return Shell.cmd("restorecon -F ${shellArg(path)} 2>/dev/null || true").exec()
    }

    fun copyGameLibrary(src: String, dest: String): Shell.Result {
        return Shell.cmd(
            "cp ${shellArg(src)} ${shellArg(dest)}",
            "chown system:system ${shellArg(dest)}",
            "chmod 755 ${shellArg(dest)}",
            "restorecon -F ${shellArg(dest)} 2>/dev/null || true"
        ).exec()
    }

    fun moveGameLibrary(src: String, dest: String): Shell.Result {
        return Shell.cmd(
            "mv ${shellArg(src)} ${shellArg(dest)}",
            "chown system:system ${shellArg(dest)}",
            "chmod 755 ${shellArg(dest)}",
            "restorecon -F ${shellArg(dest)} 2>/dev/null || true"
        ).exec()
    }

    fun removeGamePlugins(dir: String): Shell.Result {
        return Shell.cmd(
            "find ${shellArg(dir)} -maxdepth 1 -type f -name 'libhachimi_*.so' -delete"
        ).exec()
    }

    fun killPackage(packageName: String): Shell.Result {
        return Shell.cmd("am force-stop ${shellArg(packageName)}").exec()
    }
}
