package dev.hachifridge.hachidroid.core

import android.content.pm.PackageInfo
import android.content.pm.PackageManager

object GameChecker {
    /** Packages of the active [mode]; pickers list exactly these. */
    val packageNames: Array<String>
        get() = mode.packageNames.toTypedArray()

    var mode: PatchMode = PatchMode.TRACEN
        private set

    var currentPackageName: String? = null

    /**
     * Switches the active mode and resolves a target when the current one no longer belongs to it.
     *
     * A target that is still valid is kept: the user picked it explicitly on the app-select
     * screen, so refreshing the dashboard must not silently fall back to the mode default.
     */
    fun applyMode(next: PatchMode, pm: PackageManager? = null) {
        val changed = next != mode
        mode = next
        val stillValid = currentPackageName?.let { it in next.packageNames } == true
        if (pm != null && (changed || !stillValid)) {
            init(pm)
        }
    }

    fun init(pm: PackageManager) {
        // Defaults to whichever version is installed
        for (packageName in mode.packageNames) {
            currentPackageName = packageName
            if (getPackageInfo(pm) != null) {
                return
            }
        }
        // Otherwise, the mode's default client
        currentPackageName = mode.defaultPackageName
    }

    fun getPackageInfo(pm: PackageManager): PackageInfo? {
        val packageName = currentPackageName ?: return null
        return try {
            pm.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun isPackageInstalled(pm: PackageManager): Boolean {
        return getPackageInfo(pm) != null
    }

    fun getAllPackageInfo(pm: PackageManager): Array<PackageInfo?> {
        val packages = mode.packageNames
        return Array(packages.size) {
            try {
                pm.getPackageInfo(packages[it], 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}
