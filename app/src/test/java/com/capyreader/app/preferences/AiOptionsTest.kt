package com.capyreader.app.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiOptionsTest {
    private val options = AppPreferences.AiOptions(InMemoryPreferenceStore())

    @Test
    fun `defaults to DeepSeek when nothing is configured`() {
        assertEquals("https://api.deepseek.com/v1", options.baseURL.get())
        assertEquals("deepseek-chat", options.model.get())
    }

    @Test
    fun `api key has no default`() {
        assertEquals("", options.apiKey.get())
    }

    @Test
    fun `prompt has a non-blank default`() {
        assertEquals(AppPreferences.AiOptions.DEFAULT_PROMPT, options.prompt.get())
        assertTrue(options.prompt.get().isNotBlank())
    }

    @Test
    fun `reads back what was written`() {
        options.baseURL.set("https://api.z.ai/api/paas/v4")
        options.model.set("glm-4.5")
        options.apiKey.set("k")

        assertEquals("https://api.z.ai/api/paas/v4", options.baseURL.get())
        assertEquals("glm-4.5", options.model.get())
        assertEquals("k", options.apiKey.get())
    }
}
