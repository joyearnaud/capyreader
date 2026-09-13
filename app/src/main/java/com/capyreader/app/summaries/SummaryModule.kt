package com.capyreader.app.summaries

import com.capyreader.app.preferences.AppPreferences
import com.jocmp.aiclient.OpenAiCompatibleClient
import com.jocmp.aiclient.ProviderConfig
import com.jocmp.aiclient.SummaryClient
import okhttp3.OkHttpClient
import org.koin.dsl.module
import java.time.Duration

val summaryModule = module {
    single<SummaryClient> {
        val appPreferences: AppPreferences = get()

        OpenAiCompatibleClient(
            httpClient = OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(120))
                .callTimeout(Duration.ofSeconds(120))
                .build(),
            config = {
                ProviderConfig(
                    baseURL = appPreferences.aiOptions.baseURL.get(),
                    model = appPreferences.aiOptions.model.get(),
                    apiKey = appPreferences.aiOptions.apiKey.get(),
                )
            },
        )
    }
}
