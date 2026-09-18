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

    val historyRecords: StateFlow<List<BuildRecord>> = db.buildRecordDao()
        .getAllRecords()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            db.buildRecordDao().deleteById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            db.buildRecordDao().clearAll()
        }
    }
}
