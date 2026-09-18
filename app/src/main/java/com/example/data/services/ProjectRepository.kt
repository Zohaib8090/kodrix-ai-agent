package com.example.data.services

import android.content.Context
import com.example.data.model.CodeArtifact
import com.example.data.model.SourceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ProjectFileNode(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val children: List<ProjectFileNode> = emptyList(),
    val sizeBytes: Long = 0,
    val lineCount: Int = 0
)

class ProjectRepository(private val context: Context) {

    /**
     * Main folder: <context.filesDir>/my_projects
     * Contains subfolders for each created project.
     */
    val rootProjectsDir: File
        get() = File(context.filesDir, "my_projects").apply { mkdirs() }

    fun getProjectDir(projectName: String): File {
        val sanitized = projectName.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_").ifEmpty { "default_project" }
        return File(rootProjectsDir, sanitized).apply { mkdirs() }
    }

    suspend fun saveArtifactToDisk(projectName: String, artifact: CodeArtifact): File = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(projectName)
        for (file in artifact.files) {
            val target = File(projectDir, file.path)
            target.parentFile?.mkdirs()
            target.writeText(file.content)
        }
        projectDir
    }

    suspend fun saveFile(projectDir: File, relativePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = File(projectDir, relativePath)
            target.parentFile?.mkdirs()
            target.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createNewFolder(projectDir: File, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = File(projectDir, relativePath)
            target.mkdirs()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteFile(projectDir: File, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = File(projectDir, relativePath)
            if (target.isDirectory) target.deleteRecursively() else target.delete()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteProject(projectName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = getProjectDir(projectName)
            if (dir.exists()) {
                dir.deleteRecursively()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun listProjects(): List<File> = withContext(Dispatchers.IO) {
        rootProjectsDir.listFiles()?.filter { it.isDirectory }?.toList() ?: emptyList()
    }

    suspend fun readProjectFiles(projectDir: File): List<SourceFile> = withContext(Dispatchers.IO) {
        if (!projectDir.exists()) return@withContext emptyList()
        val list = mutableListOf<SourceFile>()
        projectDir.walkTopDown().forEach { file ->
            if (file.isFile && !file.name.startsWith(".")) {
                val relative = file.relativeTo(projectDir).path
                try {
                    val content = file.readText()
                    list.add(SourceFile(path = relative, content = content))
                } catch (ignored: Exception) {}
            }
        }
        list
    }

    suspend fun getProjectDirectoryTree(projectDir: File): ProjectFileNode = withContext(Dispatchers.IO) {
        fun buildTree(dir: File): ProjectFileNode {
            val children = dir.listFiles()
                ?.filter { !it.name.startsWith(".") }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.map { child ->
                    if (child.isDirectory) {
                        buildTree(child)
                    } else {
                        val lines = try { child.readLines().size } catch (_: Exception) { 0 }
                        ProjectFileNode(
                            name = child.name,
                            relativePath = child.relativeTo(projectDir).path,
                            isDirectory = false,
                            sizeBytes = child.length(),
                            lineCount = lines
                        )
                    }
                } ?: emptyList()

            return ProjectFileNode(
                name = dir.name,
                relativePath = if (dir == projectDir) "" else dir.relativeTo(projectDir).path,
                isDirectory = true,
                children = children
            )
        }
        buildTree(projectDir)
    }

    /**
     * Requirement 7: ZipOutputStream that walks through project dir File(project.path).walkTopDown()
     * and zips all real files with relative paths.
     */
    fun exportProjectAsZip(projectDir: File, zipFile: File): File {
        zipFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            projectDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relPath = file.relativeTo(projectDir).path
                    val entry = ZipEntry(relPath)
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
        return zipFile
    }
}
