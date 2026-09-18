package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "build_records")
data class BuildRecord(
    @PrimaryKey val id: String,
    val appName: String,
    val platform: String, // "ANDROID" or "WEB"
    val framework: String, // "NATIVE", "REACT_VITE", "NEXT_JS", etc.
    val prompt: String,
    val blueprintId: String,
    val blueprintTitle: String,
    val codegenProviderId: String,
    val fixProviderId: String,
    val featuresJson: String, // Comma separated or JSON
    val status: String, // "Queued", "InProgress", "Completed", "Failed"
    val currentStep: String,
    val timestamp: Long,
    val runId: Long? = null,
    val artifactType: String? = null, // "apk" or "web"
    val localApkPath: String? = null,
    val webDeployUrl: String? = null,
    val logSummary: String? = null,
    val retryCount: Int = 0
)
