package dev.hachifridge.hachidroid.ui.screen

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hachifridge.hachidroid.core.GameChecker
import dev.hachifridge.hachidroid.core.PatchMode
import dev.hachifridge.hachidroid.core.AstraeaLibraries
import dev.hachifridge.hachidroid.patcher.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ManagedGameClient(
    val packageInfo: PackageInfo,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val regionLabel: String,
    val regionTag: String,
    val isCurrentTarget: Boolean,
    val isPatched: Boolean
)

class GameManagementViewModel : ViewModel() {
    private val _clients = MutableStateFlow<List<ManagedGameClient>>(emptyList())
    val clients: StateFlow<List<ManagedGameClient>> = _clients.asStateFlow()

    fun loadApps(context: Context) {
        viewModelScope.launch {
            val pm = context.packageManager
            val installedPackages = withContext(Dispatchers.IO) {
                GameChecker.getAllPackageInfo(pm).filterNotNull()
            }

            val currentTarget = GameChecker.currentPackageName ?: GameChecker.packageNames[0]

            val list = installedPackages.map { pkg ->
                val appLabel = try {
                    pm.getApplicationLabel(pkg.applicationInfo!!).toString()
                } catch (_: Exception) {
                    pkg.packageName
                }

                val (regionName, regionTag) = when (pkg.packageName) {
                    "jp.co.cygames.umamusume" -> "Japan" to "JP"
                    "com.komoe.kmumamusumegp" -> "Traditional Chinese" to "TW"
                    "com.komoe.umamusumeofficial" -> "Global" to "EN"
                    "com.kakaogames.umamusume" -> "Korean" to "KR"
                    "jp.co.cygames.princessconnectredive" -> "Japan" to "JP"
                    else -> when (GameChecker.mode) {
                        PatchMode.TRACEN -> "Umamusume" to "Custom"
                        PatchMode.ASTRAEA -> "Priconne" to "Custom"
                    }
                }

                val isPatched = checkIfPatched(pkg)

                ManagedGameClient(
                    packageInfo = pkg,
                    packageName = pkg.packageName,
                    appName = appLabel,
                    versionName = pkg.versionName ?: "Unknown",
                    regionLabel = regionName,
                    regionTag = regionTag,
                    isCurrentTarget = pkg.packageName == currentTarget,
                    isPatched = isPatched
                )
            }

            _clients.value = list
        }
    }

    fun selectTarget(packageName: String, context: Context) {
        GameChecker.currentPackageName = packageName
        loadApps(context)
    }

    private fun checkIfPatched(packageInfo: PackageInfo): Boolean {
        val appInfo = packageInfo.applicationInfo ?: return false
        val sourceDir = appInfo.publicSourceDir ?: return false
        val apkDir = File(sourceDir).parentFile ?: return false
        val marker = if (GameChecker.mode == PatchMode.ASTRAEA) {
            AstraeaLibraries.MOD_LIBRARY
        } else {
            "libmain_orig.so"
        }
        val libDirs = mutableListOf("lib/arm64", "lib/arm64-v8a")
        appInfo.nativeLibraryDir?.let { libDirs.add(it) }

        for (dir in libDirs) {
            val check = if (dir.startsWith("/")) File(dir, marker) else apkDir.resolve(dir).resolve(marker)
            if (check.exists() || (RootUtils.isRootAvailable() && RootUtils.testFile(check.path))) return true
        }
        return false
    }
}
