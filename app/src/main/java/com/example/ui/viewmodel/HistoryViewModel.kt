package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.BuildRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val projectRepo = com.example.data.services.ProjectRepository(application)

    val historyRecords: StateFlow<List<BuildRecord>> = db.buildRecordDao()
        .getAllRecords()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun renameProject(record: BuildRecord, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == record.appName) return
        viewModelScope.launch {
            projectRepo.renameProject(record.appName, trimmed)
            val updated = record.copy(appName = trimmed)
            db.buildRecordDao().update(updated)
        }
    }

    fun deleteRecord(record: BuildRecord) {
        viewModelScope.launch {
            projectRepo.deleteProject(record.appName)
            db.buildRecordDao().deleteById(record.id)
        }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            val record = db.buildRecordDao().getRecordDirect(id)
            if (record != null) {
                projectRepo.deleteProject(record.appName)
            }
            db.buildRecordDao().deleteById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            db.buildRecordDao().clearAll()
        }
    }
}
