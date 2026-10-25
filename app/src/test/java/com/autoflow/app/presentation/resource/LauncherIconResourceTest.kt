package com.autoflow.app.presentation.resource

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LauncherIconResourceTest {

    private val projectRoot = File(".").canonicalFile

    @Test
    fun testAdaptiveIconResourcesExistAndReferenceCorrectDrawables() {
        val resDir = File(projectRoot, "src/main/res")
        val launcherXml = File(resDir, "mipmap-anydpi-v26/ic_launcher.xml")
        val launcherRoundXml = File(resDir, "mipmap-anydpi-v26/ic_launcher_round.xml")

        assertTrue("ic_launcher.xml must exist", launcherXml.exists())
        assertTrue("ic_launcher_round.xml must exist", launcherRoundXml.exists())

        val launcherContent = launcherXml.readText()
        val launcherRoundContent = launcherRoundXml.readText()

        listOf(launcherContent, launcherRoundContent).forEach { content ->
            assertTrue(content.contains("android:drawable=\"@drawable/ic_launcher_background\""))
            assertTrue(content.contains("android:drawable=\"@drawable/ic_launcher_foreground\""))
            assertTrue(content.contains("android:drawable=\"@drawable/ic_launcher_monochrome\""))
        }

        val bgXml = File(resDir, "drawable/ic_launcher_background.xml")
        val fgXml = File(resDir, "drawable/ic_launcher_foreground.xml")
        val monoXml = File(resDir, "drawable/ic_launcher_monochrome.xml")

        assertTrue("ic_launcher_background.xml must exist", bgXml.exists())
        assertTrue("ic_launcher_foreground.xml must exist", fgXml.exists())
        assertTrue("ic_launcher_monochrome.xml must exist", monoXml.exists())

        val bgContent = bgXml.readText()
        assertTrue("Main background must contain emerald green gradient #0D9E71", bgContent.contains("#0D9E71"))
        assertTrue("Main background must contain dark green gradient #047857", bgContent.contains("#047857"))

        val fgContent = fgXml.readText()
        assertTrue("Foreground must use white stroke", fgContent.contains("android:strokeColor=\"#FFFFFF\""))

        val monoContent = monoXml.readText()
        assertTrue("Monochrome must use #FF000000 stroke", monoContent.contains("android:strokeColor=\"#FF000000\""))
    }

    @Test
    fun testQaSourceSetOverridesBackgroundWithAmberGradient() {
        val qaResDir = File(projectRoot, "src/qa/res")
        val qaBgXml = File(qaResDir, "drawable/ic_launcher_background.xml")

        assertTrue("QA ic_launcher_background.xml must exist", qaBgXml.exists())
        val qaBgContent = qaBgXml.readText()
        assertTrue("QA background must contain amber gradient #F59E0B", qaBgContent.contains("#F59E0B"))
        assertTrue("QA background must contain dark amber gradient #B45309", qaBgContent.contains("#B45309"))

        // Ensure QA source set only overrides background and does not duplicate foreground or mipmaps
        assertFalse("QA source set must not duplicate foreground", File(qaResDir, "drawable/ic_launcher_foreground.xml").exists())
        assertFalse("QA source set must not duplicate mipmap", File(qaResDir, "mipmap-anydpi-v26").exists())
    }

    @Test
    fun testManifestReferencesMipmapIcons() {
        val manifest = File(projectRoot, "src/main/AndroidManifest.xml")
        assertTrue(manifest.exists())
        val content = manifest.readText()
        assertTrue(content.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(content.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
    }
}
