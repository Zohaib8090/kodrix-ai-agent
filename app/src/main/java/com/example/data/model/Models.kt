package com.example.data.model

sealed class BuildPhase {
    object Idle : BuildPhase()
    object CommittingWorkflow : BuildPhase()
    object PushingSecrets : BuildPhase()
    object Dispatching : BuildPhase()
    object Queued : BuildPhase()
    data class InProgress(val step: String) : BuildPhase()
    data class Completed(val artifact: BuildArtifact) : BuildPhase()
    data class Failed(val log: String, val retryCount: Int = 0) : BuildPhase()
}

sealed class BuildArtifact {
    data class ApkArtifact(val localPath: String) : BuildArtifact()
    data class WebArtifact(val deployUrl: String, val previewHtml: String? = null) : BuildArtifact()
}

enum class PlatformType(val displayName: String, val icon: String) {
    ANDROID("Android", "android"),
    WEB("Web", "web")
}

enum class WebFramework(val displayName: String, val buildCommand: String, val outputDir: String, val isStaticHostable: Boolean) {
    REACT_VITE("React (Vite)", "npm run build", "dist/", true),
    NEXT_JS("Next.js (Static)", "npm run build && next export", "out/", true),
    NEXT_JS_SSR("Next.js (SSR)", "npm run build", ".next/", false),
    VUE_VITE("Vue (Vite)", "npm run build", "dist/", true),
    SVELTE_KIT("SvelteKit (Static)", "npm run build", "build/", true),
    PLAIN_HTML("Plain HTML/CSS/JS", "", "", true)
}

enum class DeploymentTarget(val displayName: String, val requiresExtraAuth: Boolean) {
    GITHUB_PAGES("GitHub Pages", false),
    VERCEL("Vercel", true),
    NETLIFY("Netlify", true)
}

data class ProviderConfig(
    val id: String,
    val name: String,
    val apiKey: String = "",
    val baseUrl: String = "",
    val authStyle: AuthStyle = AuthStyle.BEARER,
    val defaultModel: String = "",
    val lastVerifiedAt: Long = 0L,
    val isValid: Boolean = false,
    val statusMessage: String = "",
    val supportsVision: Boolean = false,
    val isPrimary: Boolean = false,
    val isEnabled: Boolean = true,
    val availableModels: List<String> = emptyList(),
    val thinkingEnabled: Boolean = false,
    val thinkingLevel: String = "medium",
    val thinkingBudgetTokens: Int = 2048,
    val supportedThinkingLevels: List<String> = listOf("low", "medium", "high", "ultra", "adaptive"),
    val customPayloadJson: String = ""
)

enum class AuthStyle {
    BEARER,
    X_API_KEY,
    QUERY_PARAM,
    NONE
}

data class VerifyResult(
    val providerId: String,
    val isValid: Boolean,
    val displayMessage: String,
    val rateLimitInfo: String? = null
)

data class Blueprint(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val platform: PlatformType,
    val framework: WebFramework? = null,
    val defaultPrompt: String,
    val defaultFeatures: List<String>
)

data class AppContext(
    val appName: String,
    val platform: PlatformType,
    val framework: WebFramework?,
    val features: List<String>,
    val prompt: String = ""
)

data class SourceFile(
    val path: String,
    val content: String
)

data class CodeArtifact(
    val files: List<SourceFile>,
    val explanation: String,
    val previewHtml: String? = null
)

data class DiffProposal(
    val summary: String,
    val changedFiles: List<SourceFile>,
    val explanation: String
)

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<out T>(val data: T) : UiState<T>()
    data class Error(val message: String, val canRetry: Boolean = true) : UiState<Nothing>()
}
