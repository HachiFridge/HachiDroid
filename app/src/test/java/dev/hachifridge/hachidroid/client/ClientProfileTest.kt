package dev.hachifridge.hachidroid.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientProfileTest {

    @Test
    fun testProfileResolutionById() {
        assertEquals(ClientProfile.TRACEN_GLB, ClientProfile.fromId("tracen_glb"))
        assertEquals(ClientProfile.TRACEN_JP, ClientProfile.fromId("tracen_jp"))
        assertEquals(ClientProfile.TRACEN_TW, ClientProfile.fromId("tracen_tw"))
        assertEquals(ClientProfile.TRACEN_KR, ClientProfile.fromId("tracen_kr"))
        assertEquals(ClientProfile.ASTRAEA_JP, ClientProfile.fromId("astraea"))
        assertEquals(ClientProfile.TRACEN_GLB, ClientProfile.fromId("unknown_id"))
    }

    @Test
    fun testProfileResolutionByPackageName() {
        assertEquals(ClientProfile.TRACEN_GLB, ClientProfile.fromPackageName("com.cygames.umamusume"))
        assertEquals(ClientProfile.TRACEN_JP, ClientProfile.fromPackageName("jp.co.cygames.umamusume"))
        assertEquals(ClientProfile.TRACEN_TW, ClientProfile.fromPackageName("com.komoe.kmumamusumegp"))
        assertEquals(ClientProfile.TRACEN_TW, ClientProfile.fromPackageName("com.komoe.umamusumeofficial"))
        assertEquals(ClientProfile.TRACEN_KR, ClientProfile.fromPackageName("com.kakaogames.umamusume"))
        assertEquals(ClientProfile.ASTRAEA_JP, ClientProfile.fromPackageName("jp.co.cygames.princessconnectredive"))
        assertNull(ClientProfile.fromPackageName("com.example.nonexistent"))
    }

    @Test
    fun testDifferentServerClientsInSameMode() {
        // Global and Japan are different server clients under Tracen
        val glbProfile = ClientProfile.fromPackageName("com.cygames.umamusume")
        val jpProfile = ClientProfile.fromPackageName("jp.co.cygames.umamusume")
        assertNotNull(glbProfile)
        assertNotNull(jpProfile)
        assertEquals(Client.TRACEN, glbProfile?.client)
        assertEquals(Client.TRACEN, jpProfile?.client)
        assertNotEquals(glbProfile, jpProfile)

        // Taiwan Google Play and Official are the SAME server client
        val twGp = ClientProfile.fromPackageName("com.komoe.kmumamusumegp")
        val twOfficial = ClientProfile.fromPackageName("com.komoe.umamusumeofficial")
        assertEquals(twGp, twOfficial)
    }

    @Test
    fun testProfileShellProtection() {
        assertTrue(ClientProfile.TRACEN_GLB.shellProtected)
        assertFalse(ClientProfile.TRACEN_JP.shellProtected)
        assertFalse(ClientProfile.TRACEN_TW.shellProtected)
        assertFalse(ClientProfile.TRACEN_KR.shellProtected)
        assertTrue(ClientProfile.ASTRAEA_JP.shellProtected)
    }

    @Test
    fun testLibraryIsolationAndModSharing() {
        // Global and Japan store their unpacked/restored libraries in isolated folders
        assertNotEquals(
            ClientProfile.TRACEN_GLB.libraries.directoryName,
            ClientProfile.TRACEN_JP.libraries.directoryName
        )
        assertEquals("libs_glb", ClientProfile.TRACEN_GLB.libraries.directoryName)
        assertEquals("libs_jp", ClientProfile.TRACEN_JP.libraries.directoryName)

        // Both share the same mod library folder so Hachimi runtime does not need duplicate downloads
        assertEquals(
            ClientProfile.TRACEN_GLB.libraries.modDirectoryName,
            ClientProfile.TRACEN_JP.libraries.modDirectoryName
        )
        assertEquals("libs", ClientProfile.TRACEN_GLB.libraries.modDirectoryName)
    }

    @Test
    fun testPreferenceKeyIsolation() {
        assertNotEquals(
            ClientProfile.TRACEN_GLB.fileUrisKey.name,
            ClientProfile.TRACEN_JP.fileUrisKey.name
        )
        assertNotEquals(
            ClientProfile.TRACEN_GLB.installMethodKey.name,
            ClientProfile.TRACEN_JP.installMethodKey.name
        )
    }
}
