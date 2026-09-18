package com.capyreader.app.ui.settings.panels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.capyreader.app.preferences.AppPreferences

class AiSettingsViewModel(
    private val appPreferences: AppPreferences,
) : ViewModel() {
    var baseURL by mutableStateOf(appPreferences.aiOptions.baseURL.get())
        private set

    var model by mutableStateOf(appPreferences.aiOptions.model.get())
        private set

    var apiKey by mutableStateOf(appPreferences.aiOptions.apiKey.get())
        private set

    var prompt by mutableStateOf(appPreferences.aiOptions.prompt.get())
        private set

    var listPrompt by mutableStateOf(appPreferences.aiOptions.listPrompt.get())
        private set

    var listDigestCacheEnabled by mutableStateOf(appPreferences.aiOptions.listDigestCacheEnabled.get())
        private set

    var articleSummaryCacheEnabled by mutableStateOf(appPreferences.aiOptions.articleSummaryCacheEnabled.get())
        private set

    fun updateListPrompt(value: String) {
        appPreferences.aiOptions.listPrompt.set(value)
        listPrompt = value
    }

    fun updateListDigestCacheEnabled(value: Boolean) {
        appPreferences.aiOptions.listDigestCacheEnabled.set(value)
        listDigestCacheEnabled = value
    }

    fun updateArticleSummaryCacheEnabled(value: Boolean) {
        appPreferences.aiOptions.articleSummaryCacheEnabled.set(value)
        articleSummaryCacheEnabled = value
    }

    fun updateBaseURL(value: String) {
        appPreferences.aiOptions.baseURL.set(value)
        baseURL = value
    }

    fun updateModel(value: String) {
        appPreferences.aiOptions.model.set(value)
        model = value
    }

    fun updateApiKey(value: String) {
        appPreferences.aiOptions.apiKey.set(value)
        apiKey = value
    }

    fun updatePrompt(value: String) {
        appPreferences.aiOptions.prompt.set(value)
        prompt = value
    }
}
