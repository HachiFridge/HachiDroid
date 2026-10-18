package dev.hachifridge.hachidroid.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Destination : NavKey {
    @Serializable
    data object Home : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data object About : Destination

    @Serializable
    data object GameManagement : Destination

    @Serializable
    data object Patching : Destination

    @Serializable
    data object OpenSourceLicenses : Destination
}

/** Thin wrapper over the Nav3 back stack mirroring navigate / popBackStack semantics. */
class Navigator(private val backStack: NavBackStack<NavKey>) {

    fun navigate(destination: Destination) {
        if (backStack.lastOrNull() == destination) return
        backStack.add(destination)
    }

    fun popBackStack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}
