package dev.hachifridge.hachidroid.ui.screen

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.client.Client
import dev.hachifridge.hachidroid.core.PrefKey
import dev.hachifridge.hachidroid.core.UpeMigration
import dev.hachifridge.hachidroid.core.dataStore
import dev.hachifridge.hachidroid.core.getPrefValue
import dev.hachifridge.hachidroid.utils.ksFile
import dev.hachifridge.hachidroid.utils.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {
    var checkForUpdates by mutableStateOf(false)
    var hachimiLibsVersion by mutableStateOf("")
    /** The client whose mod repo is shown: one field, because there is one mod source. */
    var client by mutableStateOf(Client.TRACEN)
    var modRepo by mutableStateOf("")
    var mergeApks by mutableStateOf(false)
    var exportInternalDataProvider by mutableStateOf(true)
    var useInternalFilesDir by mutableStateOf(false)
    var dynamicColor by mutableStateOf(false)
    var configRead by mutableStateOf(false)

    fun loadConfig(context: Context) {
        viewModelScope.launch {
            checkForUpdates = context.getPrefValue(PrefKey.CHECK_FOR_UPDATES) as Boolean
            hachimiLibsVersion = context.getPrefValue(PrefKey.HACHIMI_LIBS_VERSION) as String
            client = Client.fromPref(context.getPrefValue(PrefKey.CLIENT) as? String)
            modRepo = context.getPrefValue(client.modSource.repoKey) as String
            mergeApks = context.getPrefValue(PrefKey.MERGE_APKS) as Boolean
            exportInternalDataProvider = context.getPrefValue(PrefKey.EXPORT_INTERNAL_DATA_PROVIDER) as Boolean
            useInternalFilesDir = context.getPrefValue(PrefKey.USE_INTERNAL_FILES_DIR) as Boolean
            dynamicColor = context.getPrefValue(PrefKey.DYNAMIC_COLOR) as Boolean
            configRead = true
        }
    }

    fun updateCheckForUpdates(context: Context, value: Boolean) {
        checkForUpdates = value
        viewModelScope.launch {
            context.dataStore.edit { it[PrefKey.CHECK_FOR_UPDATES] = value }
        }
    }

    fun updateMergeApks(context: Context, value: Boolean) {
        mergeApks = value
        viewModelScope.launch {
            context.dataStore.edit { it[PrefKey.MERGE_APKS] = value }
        }
    }

    fun updateExportInternalDataProvider(context: Context, value: Boolean) {
        exportInternalDataProvider = value
        viewModelScope.launch {
            context.dataStore.edit { it[PrefKey.EXPORT_INTERNAL_DATA_PROVIDER] = value }
        }
    }

    fun updateUseInternalFilesDir(context: Context, value: Boolean) {
        useInternalFilesDir = value
        viewModelScope.launch {
            context.dataStore.edit { it[PrefKey.USE_INTERNAL_FILES_DIR] = value }
        }
    }

    fun updateDynamicColor(context: Context, value: Boolean) {
        dynamicColor = value
        viewModelScope.launch {
            context.dataStore.edit { it[PrefKey.DYNAMIC_COLOR] = value }
        }
    }

    fun updateModRepo(context: Context, value: String) {
        modRepo = value
        val key = client.modSource.repoKey
        viewModelScope.launch {
            context.dataStore.edit { it[key] = value }
        }
    }

    fun saveHachimiLibsVersion(context: Context, value: String) {
        viewModelScope.launch {
            context.dataStore.edit { it[PrefKey.HACHIMI_LIBS_VERSION] = value }
        }
    }

    fun exportKeystore(context: Context, uri: Uri) {
        val ksFile = context.ksFile
        if (!ksFile.exists()) return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        ksFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
                context.showToast(context.getString(R.string.file_saved), Toast.LENGTH_SHORT)
            } catch (e: Exception) {
                context.showToast(context.getString(R.string.failed_to_save_file), Toast.LENGTH_SHORT)
            }
        }
    }

    fun importKeystore(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = UpeMigration.importKeystoreFromUri(context, uri)
            if (result.isSuccess) {
                context.showToast(context.getString(R.string.keystore_imported), Toast.LENGTH_SHORT)
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage
                    ?: context.getString(R.string.failed_to_import_keystore)
                context.showToast(errorMsg, Toast.LENGTH_LONG)
            }
        }
    }
}
