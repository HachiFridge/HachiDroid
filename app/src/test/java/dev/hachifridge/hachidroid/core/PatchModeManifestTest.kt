package dev.hachifridge.hachidroid.core

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every package a mode can target must be declared in the manifest's queries block.
 *
 * Package visibility is static from API 30 on: an id that is not listed there is invisible to
 * PackageManager, so an installed game reads as "not installed" — the hero card says so, the
 * launch intent comes back null and the management screen lists nothing. The manifest cannot
 * read [PatchMode], so this test is what keeps a newly added region declared.
 */
class PatchModeManifestTest {

    @Test
    fun everyModePackageIsQueryable() {
        val declared = queriedPackages()
        val expected = PatchMode.entries.flatMap { mode -> mode.packageNames }

        val missing = expected.filterNot { it in declared }
        assertTrue(
            "Packages in PatchMode but not in the manifest queries block: $missing. " +
                "Add them to app/src/main/AndroidManifest.xml.",
            missing.isEmpty()
        )
    }

    private fun queriedPackages(): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile())
        val queries = doc.getElementsByTagName("queries")
        assertTrue("manifest has no queries block", queries.length > 0)

        val names = mutableSetOf<String>()
        for (q in 0 until queries.length) {
            val children = queries.item(q).childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node.nodeName == "package") {
                    node.attributes?.getNamedItem("android:name")?.nodeValue?.let(names::add)
                }
            }
        }
        return names
    }

    /** Gradle runs unit tests from the module directory; an IDE may start at the repo root. */
    private fun manifestFile(): File =
        listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml"))
            .firstOrNull { it.isFile }
            ?: error("AndroidManifest.xml not found from " + File(".").absolutePath)
}
