package dev.hachifridge.hachidroid.client

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginStoreTest {

    @Test
    fun tracenPluginsGetTheHachimiPrefix() {
        assertEquals("libhachimi_gallop.so", TracenPlugins.prefixedName("libgallop.so"))
        assertEquals("libhachimi_skill.so", TracenPlugins.prefixedName("skill.so"))
    }

    @Test
    fun astraeaPluginsGetTheDaringTactPrefix() {
        assertEquals("libdaringtact_speed.so", AstraeaPlugins.prefixedName("libspeed.so"))
        // A plugin already named for the loader must not be prefixed twice.
        assertEquals(
            "libdaringtact_speed.so",
            AstraeaPlugins.prefixedName("libdaringtact_speed.so")
        )
    }
}
