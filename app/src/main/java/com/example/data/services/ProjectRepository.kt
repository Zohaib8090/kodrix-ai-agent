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

class ProjectRepository(private val context: Context) {

    val rootProjectsDir: File
        get() = File(context.filesDir, "kodrix-projects").apply { mkdirs() }

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
