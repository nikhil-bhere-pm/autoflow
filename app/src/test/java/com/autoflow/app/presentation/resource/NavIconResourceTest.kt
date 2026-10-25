package com.autoflow.app.presentation.resource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NavIconResourceTest {

    private val resDir = File("src/main/res/drawable").canonicalFile

    @Test
    fun testNavIconVectorsExistAndMatchSpecifications() {
        val icons = listOf(
            Triple("ic_nav_home.xml", "ic_nav_home_selected.xml", "M4,11l8,-7 8,7v9a1,1 0 0,1 -1,1h-4v-6H9v6H5a1,1 0 0,1 -1,-1v-9z"),
            Triple("ic_nav_goals.xml", "ic_nav_goals_selected.xml", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z M12,7a5,5 0 1,0 0,10a5,5 0 1,0 0,-10z M12,11a1,1 0 1,0 0,2a1,1 0 1,0 0,-2z"),
            Triple("ic_nav_review.xml", "ic_nav_review_selected.xml", "M8,4h8a2,2 0 0,1 2,2v13a2,2 0 0,1 -2,2h-8a2,2 0 0,1 -2,-2v-13a2,2 0 0,1 2,-2z M9,4h6v3H9z M9,14l2,2 4,-4"),
            Triple("ic_nav_categories.xml", "ic_nav_categories_selected.xml", "M12,3a9,9 0 1,0 9,9h-9z M15,3.5A9,9 0 0,1 20.5,9H15z")
        )

        for ((unselectedName, selectedName, expectedPath) in icons) {
            val unselectedFile = File(resDir, unselectedName)
            val selectedFile = File(resDir, selectedName)

            assertTrue("$unselectedName must exist", unselectedFile.exists())
            assertTrue("$selectedName must exist", selectedFile.exists())

            val unselectedContent = unselectedFile.readText()
            val selectedContent = selectedFile.readText()

            // Viewport & Dimensions
            listOf(unselectedContent, selectedContent).forEach { content ->
                assertTrue(content.contains("android:width=\"24dp\""))
                assertTrue(content.contains("android:height=\"24dp\""))
                assertTrue(content.contains("android:viewportWidth=\"24\""))
                assertTrue(content.contains("android:viewportHeight=\"24\""))
                assertTrue(content.contains("android:strokeLineCap=\"round\""))
                assertTrue(content.contains("android:strokeLineJoin=\"round\""))
                assertTrue(content.contains(expectedPath))
            }

            // Stroke widths: 1.75 for unselected, 2 for selected
            assertTrue("$unselectedName must have strokeWidth 1.75", unselectedContent.contains("android:strokeWidth=\"1.75\""))
            assertTrue("$selectedName must have strokeWidth 2", selectedContent.contains("android:strokeWidth=\"2\""))
        }
    }
}
