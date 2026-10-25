package com.autoflow.app.presentation.resource

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RepeatIconResourceTest {

    @Test
    fun testRepeatIconVectorExistsAndMatchesSpecification() {
        val file = File("src/main/res/drawable/ic_repeat.xml").canonicalFile
        assertTrue("ic_repeat.xml must exist", file.exists())

        val content = file.readText()
        assertTrue(content.contains("android:width=\"24dp\""))
        assertTrue(content.contains("android:height=\"24dp\""))
        assertTrue(content.contains("android:viewportWidth=\"24\""))
        assertTrue(content.contains("android:viewportHeight=\"24\""))
        assertTrue(content.contains("android:strokeWidth=\"1.75\""))
        assertTrue(content.contains("android:strokeLineCap=\"round\""))
        assertTrue(content.contains("android:strokeLineJoin=\"round\""))

        assertTrue(content.contains("M17,2l3,3 -3,3"))
        assertTrue(content.contains("M4,11V9a4,4 0 0,1 4,-4h12"))
        assertTrue(content.contains("M7,22l-3,-3 3,-3"))
        assertTrue(content.contains("M20,13v2a4,4 0 0,1 -4,4H4"))
    }
}
