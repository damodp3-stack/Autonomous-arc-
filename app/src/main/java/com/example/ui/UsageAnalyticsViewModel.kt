package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.UsageRecordEntity
import com.example.data.UsageRepository
import com.example.data.UsageSummary
import com.example.data.UsageTimeRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class UsageAnalyticsViewModel(
    private val usageRepository: UsageRepository
) : ViewModel() {

    private val _timeRange = MutableStateFlow(UsageTimeRange.ALL_TIME)
    val timeRange: StateFlow<UsageTimeRange> = _timeRange.asStateFlow()

    val usageSummary: StateFlow<UsageSummary> = _timeRange
        .flatMapLatest { range ->
            usageRepository.getUsageSummary(range)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UsageSummary()
        )

    val recentRecords: StateFlow<List<UsageRecordEntity>> = usageRepository.getRecentUsage(100)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setTimeRange(range: UsageTimeRange) {
        _timeRange.value = range
    }

    fun clearHistory() {
        viewModelScope.launch {
            usageRepository.clearHistory()
        }
    }
}

class UsageAnalyticsViewModelFactory(
    private val usageRepository: UsageRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UsageAnalyticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UsageAnalyticsViewModel(usageRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
