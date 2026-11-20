package dev.hachifridge.hachidroid.core

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import dev.hachifridge.hachidroid.client.Client

object GameChecker {
    /** Packages of the active [client]; pickers list exactly these. */
    val packageNames: Array<String>
        get() = client.packageNames.toTypedArray()

    var client: Client = Client.TRACEN
        private set

    var currentPackageName: String? = null

    /**
     * Switches the active client and resolves a target when the current one no longer belongs to
     * it.
     *
     * A target that is still valid is kept: the user picked it explicitly on the app-select
     * screen, so refreshing the dashboard must not silently fall back to the client default.
     */
    fun applyClient(next: Client, pm: PackageManager? = null) {
        val changed = next != client
        client = next
        val stillValid = currentPackageName?.let { it in next.packageNames } == true
        if (pm != null && (changed || !stillValid)) {
            init(pm)
        }
    }

    fun init(pm: PackageManager) {
        // Defaults to whichever version is installed
        for (packageName in client.packageNames) {
            currentPackageName = packageName
            if (getPackageInfo(pm) != null) {
                return
            }
        }
        // Otherwise, the client's default target
        currentPackageName = client.defaultPackageName
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
        val packages = client.packageNames
        return Array(packages.size) {
            try {
                pm.getPackageInfo(packages[it], 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}
