package com.example

import android.app.Application
import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.PreferenceStorage
import com.example.data.remote.ApiClient
import com.example.data.remote.GitHubApiService
import com.example.data.services.AiProviderRepository
import com.example.data.services.ArtifactService
import com.example.data.services.RepoService
import com.example.data.services.SecretsService
import com.example.data.services.WorkflowService

class MyApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferenceStorage: PreferenceStorage
        private set

    lateinit var gitHubApiService: GitHubApiService
        private set

    lateinit var repoService: RepoService
        private set

    lateinit var secretsService: SecretsService
        private set

    lateinit var workflowService: WorkflowService
        private set

    lateinit var artifactService: ArtifactService
        private set

    lateinit var aiProviderRepository: AiProviderRepository
        private set

    override fun onCreate() {
        super.onCreate()
        appContext = this

        preferenceStorage = PreferenceStorage(this)
        database = AppDatabase.getInstance(this)

        gitHubApiService = ApiClient.createGitHubService {
            preferenceStorage.githubToken
        }

        val okHttpClient = ApiClient.createGenericOkHttpClient(30)
        repoService = RepoService(gitHubApiService)
        secretsService = SecretsService(gitHubApiService)
        workflowService = WorkflowService(gitHubApiService)
        artifactService = ArtifactService(this, gitHubApiService, okHttpClient)
        aiProviderRepository = AiProviderRepository(preferenceStorage)
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}
