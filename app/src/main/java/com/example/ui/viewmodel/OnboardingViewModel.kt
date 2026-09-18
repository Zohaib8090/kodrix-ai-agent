package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferenceStorage
import com.example.data.model.ProviderConfig
import com.example.data.model.UiState
import com.example.data.model.VerifyResult
import com.example.data.remote.ApiClient
import com.example.data.remote.GitHubUser
import com.example.data.services.AiProviderRepository
import com.example.data.services.GitHubOAuthService
import com.example.data.services.RepoService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceFlowState(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String? = null,
    val expiresIn: Int = 900,
    val interval: Int = 5,
    val isPolling: Boolean = true,
    val statusText: String = "Waiting for authorization on GitHub...",
    val error: String? = null
)

data class OnboardingUiState(
    val githubToken: String = "",
    val githubClientId: String = "",
    val githubRepoName: String = "my-nocode-apps",
    val verifiedUser: GitHubUser? = null,
    val isVerifyingGithub: Boolean = false,
    val githubError: String? = null,
    val isRepoCreated: Boolean = false,
    val isStartingOAuth: Boolean = false,
    val deviceFlow: DeviceFlowState? = null,
    val providers: List<ProviderConfig> = emptyList(),
    val verifyingProviderId: String? = null,
    val verificationResults: Map<String, VerifyResult> = emptyMap(),
    val isCompleted: Boolean = false
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceStorage(application)
    private val aiRepo = AiProviderRepository(prefs)
    private val oAuthService = GitHubOAuthService()
    private var pollingJob: Job? = null

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val savedToken = prefs.githubToken
        val savedRepo = prefs.githubRepo
        val savedProviders = prefs.getProviders()
        val savedClientId = prefs.githubClientId

        _state.value = _state.value.copy(
            githubToken = savedToken,
            githubClientId = savedClientId,
            githubRepoName = savedRepo,
            providers = savedProviders,
            isCompleted = prefs.isOnboardingCompleted
        )

        if (savedToken.isNotBlank() && prefs.isGithubVerified) {
            verifyGithubToken(savedToken, savedRepo)
        }
    }

    fun onClientIdChange(clientId: String) {
        val trimmed = clientId.trim()
        prefs.githubClientId = trimmed
        _state.value = _state.value.copy(githubClientId = trimmed, githubError = null)
    }

    fun onGithubTokenChange(token: String) {
        _state.value = _state.value.copy(githubToken = token, githubError = null)
    }

    fun onRepoNameChange(name: String) {
        _state.value = _state.value.copy(githubRepoName = name)
    }

    fun startDeviceOAuth() {
        val clientId = _state.value.githubClientId.ifBlank { PreferenceStorage.DEFAULT_GITHUB_CLIENT_ID }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isStartingOAuth = true,
                githubError = null,
                deviceFlow = null
            )

            val result = oAuthService.requestDeviceCode(clientId)
            if (result.isSuccess) {
                val flow = result.getOrThrow()
                val deviceFlowState = DeviceFlowState(
                    deviceCode = flow.deviceCode,
                    userCode = flow.userCode,
                    verificationUri = flow.verificationUri,
                    verificationUriComplete = flow.verificationUriComplete,
                    expiresIn = flow.expiresIn,
                    interval = flow.interval,
                    isPolling = true,
                    statusText = "Waiting for you to authorize in your browser..."
                )
                _state.value = _state.value.copy(
                    isStartingOAuth = false,
                    deviceFlow = deviceFlowState
                )

                // Start polling
                startPolling(clientId, flow.deviceCode, flow.interval, flow.expiresIn)
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage 
                    ?: "Failed to connect to GitHub OAuth. Check your internet connection or Client ID."
                _state.value = _state.value.copy(
                    isStartingOAuth = false,
                    githubError = errorMsg
                )
            }
        }
    }

    private fun startPolling(
        clientId: String,
        deviceCode: String,
        interval: Int,
        expiresIn: Int
    ) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            val tokenResult = oAuthService.pollForAccessToken(
                clientId = clientId,
                deviceCode = deviceCode,
                initialIntervalSeconds = interval,
                expiresInSeconds = expiresIn,
                onProgress = { progress ->
                    _state.value = _state.value.copy(
                        deviceFlow = _state.value.deviceFlow?.copy(statusText = progress)
                    )
                }
            )

            if (tokenResult.isSuccess) {
                val token = tokenResult.getOrThrow()
                prefs.githubToken = token
                prefs.authMethod = "OAUTH"
                _state.value = _state.value.copy(
                    githubToken = token,
                    deviceFlow = null
                )
                // Now verify the token and repo with GitHub
                verifyGithubToken(token, _state.value.githubRepoName)
            } else {
                val error = tokenResult.exceptionOrNull()?.localizedMessage ?: "Authorization was not completed"
                _state.value = _state.value.copy(
                    deviceFlow = _state.value.deviceFlow?.copy(
                        isPolling = false,
                        error = error
                    )
                )
            }
        }
    }

    fun cancelDeviceOAuth() {
        pollingJob?.cancel()
        pollingJob = null
        _state.value = _state.value.copy(
            isStartingOAuth = false,
            deviceFlow = null
        )
    }

    fun disconnectGithub() {
        pollingJob?.cancel()
        pollingJob = null
        prefs.githubToken = ""
        prefs.githubUsername = ""
        prefs.isGithubVerified = false
        _state.value = _state.value.copy(
            githubToken = "",
            verifiedUser = null,
            isRepoCreated = false,
            githubError = null,
            deviceFlow = null
        )
    }

    fun verifyGithubToken(token: String = _state.value.githubToken, repoName: String = _state.value.githubRepoName) {
        if (token.isBlank()) {
            _state.value = _state.value.copy(githubError = "GitHub token or session is missing")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isVerifyingGithub = true, githubError = null)
            val apiService = ApiClient.createGitHubService { token.trim() }
            val repoService = RepoService(apiService)

            val userResult = repoService.verifyUser()
            if (userResult.isSuccess) {
                val user = userResult.getOrThrow()
                // Ensure target repo exists
                val repoResult = repoService.ensureRepoExists(user.login, repoName.trim())
                if (repoResult.isSuccess) {
                    prefs.githubToken = token.trim()
                    prefs.githubUsername = user.login
                    prefs.githubRepo = repoName.trim()
                    prefs.isGithubVerified = true

                    _state.value = _state.value.copy(
                        verifiedUser = user,
                        isVerifyingGithub = false,
                        isRepoCreated = true,
                        githubError = null
                    )
                } else {
                    _state.value = _state.value.copy(
                        verifiedUser = user,
                        isVerifyingGithub = false,
                        isRepoCreated = false,
                        githubError = repoResult.exceptionOrNull()?.localizedMessage ?: "Failed to verify repository"
                    )
                }
            } else {
                prefs.isGithubVerified = false
                _state.value = _state.value.copy(
                    isVerifyingGithub = false,
                    githubError = userResult.exceptionOrNull()?.localizedMessage ?: "Invalid GitHub credentials"
                )
            }
        }
    }

    fun onProviderApiKeyChange(providerId: String, newKey: String) {
        val updatedList = _state.value.providers.map { p ->
            if (p.id == providerId) p.copy(apiKey = newKey) else p
        }
        _state.value = _state.value.copy(providers = updatedList)
        prefs.saveProviders(updatedList)
    }

    fun onProviderBaseUrlChange(providerId: String, newUrl: String) {
        val updatedList = _state.value.providers.map { p ->
            if (p.id == providerId) p.copy(baseUrl = newUrl) else p
        }
        _state.value = _state.value.copy(providers = updatedList)
        prefs.saveProviders(updatedList)
    }

    fun onProviderModelChange(providerId: String, newModel: String) {
        val updatedList = _state.value.providers.map { p ->
            if (p.id == providerId) p.copy(defaultModel = newModel) else p
        }
        _state.value = _state.value.copy(providers = updatedList)
        prefs.saveProviders(updatedList)
    }

    fun verifyProvider(providerId: String) {
        val provider = _state.value.providers.firstOrNull { it.id == providerId } ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(verifyingProviderId = providerId)
            val result = aiRepo.verifyProvider(provider)
            val updatedMap = _state.value.verificationResults.toMutableMap()
            updatedMap[providerId] = result

            val refreshedProviders = aiRepo.getProviders()
            _state.value = _state.value.copy(
                verifyingProviderId = null,
                verificationResults = updatedMap,
                providers = refreshedProviders
            )
        }
    }

    fun completeOnboarding(onSuccess: () -> Unit) {
        prefs.isOnboardingCompleted = true
        _state.value = _state.value.copy(isCompleted = true)
        onSuccess()
    }
}
