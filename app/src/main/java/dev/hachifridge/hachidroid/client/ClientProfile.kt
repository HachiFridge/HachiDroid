package dev.hachifridge.hachidroid.client

import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.hachifridge.hachidroid.R

/**
 * A preset / profile for a distinct game server client.
 *
 * One [Client] family covers multiple regional store releases (e.g. Japan, Global, Taiwan, Korea).
 * When more than one different server client is installed on the device (e.g. 1 Global and 1 Japan),
 * presets activate so each client preserves its own picked split APKs, restored native libraries,
 * and install methods independently.
 */
enum class ClientProfile(
    val id: String,
    val client: Client,
    val packageNames: List<String>,
    @StringRes val titleRes: Int,
    @StringRes val regionLabelRes: Int,
    val regionTag: String,
    val shellProtected: Boolean,
    val libraries: LibrarySet
) {
    TRACEN_GLB(
        id = "tracen_glb",
        client = Client.TRACEN,
        packageNames = listOf("com.cygames.umamusume"),
        titleRes = R.string.preset_tracen_glb,
        regionLabelRes = R.string.region_global,
        regionTag = "EN",
        shellProtected = true,
        libraries = TracenGlbLibraries
    ),

    TRACEN_JP(
        id = "tracen_jp",
        client = Client.TRACEN,
        packageNames = listOf("jp.co.cygames.umamusume"),
        titleRes = R.string.preset_tracen_jp,
        regionLabelRes = R.string.region_japan,
        regionTag = "JP",
        shellProtected = false,
        libraries = TracenJpLibraries
    ),

    TRACEN_TW(
        id = "tracen_tw",
        client = Client.TRACEN,
        packageNames = listOf("com.komoe.kmumamusumegp", "com.komoe.umamusumeofficial"),
        titleRes = R.string.preset_tracen_tw,
        regionLabelRes = R.string.region_taiwan,
        regionTag = "TW",
        shellProtected = false,
        libraries = TracenTwLibraries
    ),

    TRACEN_KR(
        id = "tracen_kr",
        client = Client.TRACEN,
        packageNames = listOf("com.kakaogames.umamusume"),
        titleRes = R.string.preset_tracen_kr,
        regionLabelRes = R.string.region_korea,
        regionTag = "KR",
        shellProtected = false,
        libraries = TracenKrLibraries
    ),

    ASTRAEA_JP(
        id = "astraea",
        client = Client.ASTRAEA,
        packageNames = listOf("jp.co.cygames.princessconnectredive"),
        titleRes = R.string.preset_astraea,
        regionLabelRes = R.string.region_japan,
        regionTag = "JP",
        shellProtected = true,
        libraries = AstraeaLibraries
    );

    val defaultPackageName: String
        get() = packageNames.first()

    val plugins: PluginStore
        get() = client.plugins

    val modSource: ModSource
        get() = client.modSource

    val fileUrisKey: Preferences.Key<String>
        get() = stringPreferencesKey("app_patcher_file_uris_$id")

    val installMethodKey: Preferences.Key<Int>
        get() = intPreferencesKey("install_method_$id")

    companion object {
        fun fromId(id: String?): ClientProfile =
            entries.firstOrNull { it.id == id } ?: TRACEN_GLB

        fun fromPackageName(packageName: String?): ClientProfile? =
            if (packageName == null) null
            else entries.firstOrNull { it.packageNames.contains(packageName) }

        fun forClient(client: Client): List<ClientProfile> =
            entries.filter { it.client == client }

        fun defaultForClient(client: Client): ClientProfile =
            entries.firstOrNull { it.client == client } ?: TRACEN_GLB

        /**
         * Returns all distinct server client profiles that are currently installed on the device
         * for the given client family.
         */
        fun getInstalledProfiles(pm: PackageManager, client: Client): List<ClientProfile> {
            return forClient(client).filter { profile ->
                profile.packageNames.any { pkg ->
                    runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
                }
            }
        }
    }
}
