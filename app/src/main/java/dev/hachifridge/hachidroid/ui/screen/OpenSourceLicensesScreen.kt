package dev.hachifridge.hachidroid.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.ui.component.BackButton
import dev.hachifridge.hachidroid.ui.component.TopBar
import com.mikepenz.aboutlibraries.ui.compose.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.chipColors
import com.mikepenz.aboutlibraries.ui.compose.libraryColors
import dev.hachifridge.hachidroid.ui.navigation.Navigator

@Composable
fun OpenSourceLicensesScreen(navigator: Navigator) {
    Scaffold(
        topBar = {
            TopBar(
                title = stringResource(R.string.open_source_licenses),
                navigationIcon = { BackButton(navigator) }
            )
        }
    ) { contentPadding ->
        val libraries = produceLibraries(R.raw.aboutlibraries)
        LibrariesContainer(
            libraries = libraries.value,
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = contentPadding,
            colors = LibraryDefaults.libraryColors(
                libraryBackgroundColor = MaterialTheme.colorScheme.background,
                libraryContentColor = MaterialTheme.colorScheme.onBackground,
                versionChipColors = LibraryDefaults.chipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                licenseChipColors = LibraryDefaults.chipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                fundingChipColors = LibraryDefaults.chipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                dialogBackgroundColor = MaterialTheme.colorScheme.background,
                dialogContentColor = MaterialTheme.colorScheme.onBackground,
                dialogConfirmButtonColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}