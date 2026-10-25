package com.autoflow.app.presentation.resource

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsIconResourceTest {

    @Test
    fun testSettingsSlidersIconVectorExistsAndMatchesSpecification() {
        val file = File("src/main/res/drawable/ic_settings_sliders.xml").canonicalFile
        assertTrue("ic_settings_sliders.xml must exist", file.exists())

        val content = file.readText()
        assertTrue(content.contains("android:width=\"24dp\""))
        assertTrue(content.contains("android:height=\"24dp\""))
        assertTrue(content.contains("android:viewportWidth=\"24\""))
        assertTrue(content.contains("android:viewportHeight=\"24\""))
        assertTrue(content.contains("android:strokeWidth=\"1.75\""))
        assertTrue(content.contains("android:strokeLineCap=\"round\""))
        assertTrue(content.contains("android:strokeLineJoin=\"round\""))

        assertTrue(content.contains("M4,6h10"))
        assertTrue(content.contains("M18,6h2"))
        assertTrue(content.contains("M16,4a2,2 0 1,0 0,4a2,2 0 1,0 0,-4"))
        assertTrue(content.contains("M4,12h4"))
        assertTrue(content.contains("M12,12h8"))
        assertTrue(content.contains("M10,10a2,2 0 1,0 0,4a2,2 0 1,0 0,-4"))
        assertTrue(content.contains("M4,18h10"))
        assertTrue(content.contains("M18,18h2"))
        assertTrue(content.contains("M16,16a2,2 0 1,0 0,4a2,2 0 1,0 0,-4"))
    }
}
