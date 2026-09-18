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

    var listDigestCacheEnabled by mutableStateOf(appPreferences.aiOptions.listDigestCacheEnabled.get())
        private set

    fun updateListDigestCacheEnabled(value: Boolean) {
        appPreferences.aiOptions.listDigestCacheEnabled.set(value)
        listDigestCacheEnabled = value
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
