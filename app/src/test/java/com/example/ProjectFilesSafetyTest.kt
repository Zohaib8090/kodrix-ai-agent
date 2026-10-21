package com.example

import com.example.util.ProjectNaming
import com.example.util.SafePaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectFilesSafetyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `paths inside the project resolve`() {
        val project = tmp.newFolder("my_projects", "App")

        assertEquals(File(project, "src/App.tsx").canonicalFile, SafePaths.resolveInside(project, "src/App.tsx"))
        assertEquals(File(project, "index.html").canonicalFile, SafePaths.resolveInside(project, "/index.html"))
        assertEquals(File(project, "a/b.txt").canonicalFile, SafePaths.resolveInside(project, "a/../a/./b.txt"))
    }

    @Test
    fun `paths that escape the project are rejected`() {
        val project = tmp.newFolder("my_projects", "App")
        File(tmp.root, "my_projects/Other").mkdirs()

        assertNull(SafePaths.resolveInside(project, "../../shared_prefs/nocode_prefs.xml"))
        assertNull(SafePaths.resolveInside(project, "..\\Other\\index.html"))
        assertNull(SafePaths.resolveInside(project, "src/../../Other/x"))
        assertNull(SafePaths.resolveInside(project, ""))
        assertNull(SafePaths.resolveInside(project, "."))
    }

    @Test
    fun `common root folder is detected only when every entry shares it`() {
        assertEquals("repo-abc123/", SafePaths.commonRootFolder(listOf("repo-abc123/", "repo-abc123/index.html", "repo-abc123/src/a.js")))
        assertNull(SafePaths.commonRootFolder(listOf("index.html", "src/a.js")))
        assertNull(SafePaths.commonRootFolder(listOf("src/a.js", "lib/b.js")))
    }

    @Test
    fun `names are derived from the prompt`() {
        assertEquals("Todo List App", ProjectNaming.deriveName("Build a todo list app with dark mode"))
        assertEquals("Weather Dashboard", ProjectNaming.deriveName("I want to create a weather dashboard that shows forecasts"))
        assertEquals(ProjectNaming.FALLBACK_NAME, ProjectNaming.deriveName("   "))
        assertEquals(ProjectNaming.FALLBACK_NAME, ProjectNaming.deriveName("Please make me a"))
    }

    @Test
    fun `unique names never reuse a folder`() {
        val taken = listOf("Todo App", "todo_app 2", "Notes")

        assertEquals("Todo App 3", ProjectNaming.uniqueName("Todo App", taken))
        assertEquals("Notes 2", ProjectNaming.uniqueName("notes", taken).replaceFirstChar { it.uppercase() })
        assertEquals("Chat", ProjectNaming.uniqueName("Chat", taken))
    }

    @Test
    fun `folder names can never point at a parent directory`() {
        assertEquals("default_project", ProjectNaming.toDirectoryName(".."))
        assertEquals("hidden", ProjectNaming.toDirectoryName(".hidden"))
        assertTrue(ProjectNaming.toDirectoryName("../../etc").none { it == '/' })
    }
}
