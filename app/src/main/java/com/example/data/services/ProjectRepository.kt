package com.example.data.services

import android.content.Context
import com.example.data.model.CodeArtifact
import com.example.data.model.SourceFile
import com.example.util.ProjectNaming
import com.example.util.SafePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
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
        return File(rootProjectsDir, ProjectNaming.toDirectoryName(projectName)).apply { mkdirs() }
    }

    /**
     * Returns a name based on [baseName] whose folder is not used by an existing project folder
     * or by any name in [takenNames] (for example the app names already stored in Room).
     */
    suspend fun uniqueProjectName(baseName: String, takenNames: Collection<String> = emptyList()): String =
        withContext(Dispatchers.IO) {
            val existingDirs = rootProjectsDir.listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
            ProjectNaming.uniqueName(baseName, takenNames + existingDirs)
        }

    /**
     * Writes every file of [artifact] into the project folder. Paths that would land outside the
     * project folder (absolute escapes or "../") are skipped rather than written.
     */
    suspend fun saveArtifactToDisk(projectName: String, artifact: CodeArtifact): File = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(projectName)
        for (file in artifact.files) {
            val target = SafePaths.resolveInside(projectDir, file.path) ?: continue
            target.parentFile?.mkdirs()
            target.writeText(file.content)
        }
        projectDir
    }

    suspend fun saveFile(projectDir: File, relativePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = SafePaths.resolveInside(projectDir, relativePath) ?: return@withContext false
            target.parentFile?.mkdirs()
            target.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createNewFolder(projectDir: File, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = SafePaths.resolveInside(projectDir, relativePath) ?: return@withContext false
            target.mkdirs()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteFile(projectDir: File, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = SafePaths.resolveInside(projectDir, relativePath) ?: return@withContext false
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

    /**
     * Reads the project's editable text files. Dependency and build output folders
     * (node_modules, .git, dist, ...) and binary or very large files are skipped: after an
     * `npm install` they would otherwise mean reading tens of thousands of files into memory.
     */
    suspend fun readProjectFiles(projectDir: File): List<SourceFile> = withContext(Dispatchers.IO) {
        if (!projectDir.exists()) return@withContext emptyList()
        val list = mutableListOf<SourceFile>()
        projectDir.walkTopDown()
            .onEnter { dir -> dir == projectDir || !isSkippedDirectory(dir) }
            .forEach { file ->
                if (file.isFile && !file.name.startsWith(".") && isEditableTextFile(file)) {
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
                    if (child.isDirectory && isSkippedDirectory(child)) {
                        // Shown so the user can see it exists, but never walked.
                        ProjectFileNode(
                            name = child.name,
                            relativePath = child.relativeTo(projectDir).path,
                            isDirectory = true
                        )
                    } else if (child.isDirectory) {
                        buildTree(child)
                    } else {
                        val lines = if (isEditableTextFile(child)) {
                            try { child.useLines { it.count() } } catch (_: Exception) { 0 }
                        } else {
                            0
                        }
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
            inputStream.use { input ->
                tempZip.outputStream().use { out ->
                    input.copyTo(out)
                }
            }

            try {
                ZipFile(tempZip).use { zip ->
                    val entries = zip.entries().toList()
                    // Strip the single wrapping folder of GitHub zipballs ("repo-abc123/..."),
                    // but keep the layout of archives that have several top-level entries.
                    val rootPrefix = SafePaths.commonRootFolder(entries.map { it.name })
                    for (entry in entries) {
                        val entryName = entry.name.replace('\\', '/')
                        if (entryName.startsWith("__MACOSX")) continue
                        val cleanPath = if (rootPrefix != null) entryName.removePrefix(rootPrefix) else entryName
                        // Rejects "Zip Slip" entries such as "../../shared_prefs/x.xml".
                        val targetFile = SafePaths.resolveInside(projectDir, cleanPath) ?: continue
                        if (entry.isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                targetFile.outputStream().use { fos -> input.copyTo(fos) }
                            }
                        }
                    }
                }
            } finally {
                tempZip.delete()
            }
            Result.success(projectDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cloneGitHubRepo(
        repoUrlOrSlug: String,
        branch: String? = null,
        authToken: String? = null,
        takenNames: Collection<String> = emptyList()
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
            val projectName = uniqueProjectName(repo, takenNames)

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

            val importResult = client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Failed to download repository ($owner/$repo): HTTP ${response.code} ${response.message}")
                    )
                }

                val bodyStream = response.body?.byteStream()
                    ?: return@withContext Result.failure(Exception("Empty response received from GitHub"))

                importProjectFromZipStream(bodyStream, projectName)
            }
            if (importResult.isSuccess) {
                Result.success(Pair(importResult.getOrThrow(), projectName))
            } else {
                Result.failure(importResult.exceptionOrNull() ?: Exception("Failed to extract repository archive."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isSkippedDirectory(dir: File): Boolean = dir.name in SKIPPED_DIRECTORIES

    private fun isEditableTextFile(file: File): Boolean {
        if (file.length() > MAX_EDITABLE_FILE_BYTES) return false
        return try {
            val head = ByteArray(BINARY_SNIFF_BYTES)
            val read = file.inputStream().use { it.read(head) }
            (0 until maxOf(read, 0)).none { head[it] == 0.toByte() }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        /** Dependency, VCS and build-output folders that are never loaded into the editor or AI context. */
        val SKIPPED_DIRECTORIES = setOf(
            "node_modules", ".git", ".gradle", ".kotlin", ".idea", ".next", ".nuxt", ".svelte-kit",
            ".cache", "dist", "build", "coverage", "__pycache__", ".venv", "venv"
        )
        private const val MAX_EDITABLE_FILE_BYTES = 512L * 1024
        private const val BINARY_SNIFF_BYTES = 8000
    }
}
