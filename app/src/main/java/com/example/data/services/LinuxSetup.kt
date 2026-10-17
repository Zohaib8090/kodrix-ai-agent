package com.example.data.services

enum class LinuxSetupPhase(val label: String) {
    IDLE("Not started"),
    CHECKING("Checking device"),
    RESOLVING("Finding download"),
    DOWNLOADING("Downloading"),
    VERIFYING("Verifying archive"),
    EXTRACTING("Installing files"),
    CONFIGURING("Configuring"),
    DONE("Ready"),
    FAILED("Failed")
}

enum class LinuxSetupError(val title: String, val hint: String) {
    LOW_STORAGE("Not enough storage", "Free up some space on the device and try again."),
    NO_NETWORK("No internet connection", "Connect to Wi-Fi or mobile data and try again."),
    NO_MIRROR("Download unavailable", "None of the download sources responded. Try again in a few minutes."),
    DOWNLOAD_INTERRUPTED("Download interrupted", "The connection dropped before the download finished. Try again."),
    CORRUPT_ARCHIVE("Downloaded file is damaged", "The archive could not be read. Retrying downloads a fresh copy."),
    EXTRACT_FAILED("Installation failed", "The files could not be written to app storage."),
    CANCELLED("Cancelled", "Setup was cancelled. Nothing was installed."),
    UNKNOWN("Unexpected error", "Something went wrong. The log below has details.")
}

data class LinuxSetupState(
    val phase: LinuxSetupPhase = LinuxSetupPhase.IDLE,
    /** Progress of the current phase, 0..1, or null when it cannot be measured. */
    val phaseProgress: Float? = null,
    val message: String = "",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val mirrorIndex: Int = 0,
    val mirrorCount: Int = 0,
    val filesDone: Int = 0,
    val filesTotal: Int = 0,
    val failedPhase: LinuxSetupPhase? = null,
    val error: LinuxSetupError? = null,
    val errorDetail: String? = null
) {
    val isRunning: Boolean
        get() = phase != LinuxSetupPhase.IDLE && phase != LinuxSetupPhase.DONE && phase != LinuxSetupPhase.FAILED

    /** Overall progress across all phases, 0..1. Downloading and extracting dominate the time. */
    val overallProgress: Float
        get() {
            val p = (phaseProgress ?: 0f).coerceIn(0f, 1f)
            return when (phase) {
                LinuxSetupPhase.IDLE -> 0f
                LinuxSetupPhase.CHECKING -> 0.02f * p
                LinuxSetupPhase.RESOLVING -> 0.02f + 0.03f * p
                LinuxSetupPhase.DOWNLOADING -> 0.05f + 0.55f * p
                LinuxSetupPhase.VERIFYING -> 0.60f + 0.02f * p
                LinuxSetupPhase.EXTRACTING -> 0.62f + 0.33f * p
                LinuxSetupPhase.CONFIGURING -> 0.95f + 0.05f * p
                LinuxSetupPhase.DONE -> 1f
                LinuxSetupPhase.FAILED -> 0f
            }
        }
}

/** Raised internally by the installer; carries the category shown to the user. */
class LinuxSetupException(val error: LinuxSetupError, detail: String, cause: Throwable? = null) :
    Exception(detail, cause)

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
    return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
}
