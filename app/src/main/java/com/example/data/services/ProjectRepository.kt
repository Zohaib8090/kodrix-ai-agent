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

    suspend fun renameProject(oldName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val oldDir = getProjectDir(oldName)
            val newDir = getProjectDir(newName)
            if (oldDir.exists() && oldDir.absolutePath != newDir.absolutePath) {
                oldDir.renameTo(newDir)
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

    suspend fun importProjectFromZipStream(
        inputStream: java.io.InputStream,
        desiredProjectName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val projectDir = getProjectDir(desiredProjectName)
            projectDir.mkdirs()

            val tempZip = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.zip")
            tempZip.outputStream().use { out ->
                inputStream.copyTo(out)
            }

            java.util.zip.ZipInputStream(tempZip.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (!entryName.startsWith("__MACOSX") && !entryName.contains("../")) {
                        // Strip leading root directory if GitHub zipball format (e.g. repo-name-hash/file)
                        val cleanPath = entryName.replaceFirst(Regex("^[^/]+/(?=.+)"), "")
                        val targetFile = File(projectDir, if (cleanPath.isBlank()) entryName else cleanPath)
                        if (entry.isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            targetFile.outputStream().use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            tempZip.delete()
            Result.success(projectDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cloneGitHubRepo(
        repoUrlOrSlug: String,
        branch: String? = null,
        authToken: String? = null
    ): Result<Pair<File, String>> = withContext(Dispatchers.IO) {
        try {
            val cleaned = repoUrlOrSlug.trim()
                .removePrefix("https://github.com/")
                .removePrefix("http://github.com/")
                .removePrefix("github.com/")
                .removeSuffix(".git")
                .trim('/')

            val parts = cleaned.split('/')
            if (parts.size < 2) {
                return@withContext Result.failure(Exception("Invalid GitHub repository format. Use 'owner/repo' or a full GitHub repository URL."))
            }

            val owner = parts[0]
            val repo = parts[1]
            val projectName = repo

            val client = com.example.data.remote.ApiClient.createAiHttpClient(60)
            val branchParam = branch?.trim()?.ifEmpty { null }
            val downloadUrl = if (branchParam != null) {
                "https://api.github.com/repos/$owner/$repo/zipball/$branchParam"
            } else {
                "https://api.github.com/repos/$owner/$repo/zipball"
            }

            val requestBuilder = okhttp3.Request.Builder()
                .url(downloadUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Kodrix-AI-Agent")

            if (!authToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer ${authToken.trim()}")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to download repository ($owner/$repo): HTTP ${response.code} ${response.message}")
                )
            }

            val bodyStream = response.body?.byteStream()
                ?: return@withContext Result.failure(Exception("Empty response received from GitHub"))

            val importResult = importProjectFromZipStream(bodyStream, projectName)
            if (importResult.isSuccess) {
                Result.success(Pair(importResult.getOrThrow(), projectName))
            } else {
                Result.failure(importResult.exceptionOrNull() ?: Exception("Failed to extract repository archive."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
