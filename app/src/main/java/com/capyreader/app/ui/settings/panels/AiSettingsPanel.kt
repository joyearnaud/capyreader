package com.capyreader.app.ui.settings.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.common.RowItem
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.ui.components.FormSection
import com.capyreader.app.ui.components.TextSwitch
import com.capyreader.app.ui.theme.CapyTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiSettingsPanel(
    viewModel: AiSettingsViewModel = koinViewModel(),
) {
    AiSettingsPanelView(
        baseURL = viewModel.baseURL,
        updateBaseURL = viewModel::updateBaseURL,
        model = viewModel.model,
        updateModel = viewModel::updateModel,
        apiKey = viewModel.apiKey,
        updateApiKey = viewModel::updateApiKey,
        prompt = viewModel.prompt,
        updatePrompt = viewModel::updatePrompt,
        listPrompt = viewModel.listPrompt,
        updateListPrompt = viewModel::updateListPrompt,
        listDigestCacheEnabled = viewModel.listDigestCacheEnabled,
        updateListDigestCacheEnabled = viewModel::updateListDigestCacheEnabled,
        articleSummaryCacheEnabled = viewModel.articleSummaryCacheEnabled,
        updateArticleSummaryCacheEnabled = viewModel::updateArticleSummaryCacheEnabled,
    )
}

@Composable
fun AiSettingsPanelView(
    baseURL: String,
    updateBaseURL: (String) -> Unit,
    model: String,
    updateModel: (String) -> Unit,
    apiKey: String,
    updateApiKey: (String) -> Unit,
    prompt: String,
    updatePrompt: (String) -> Unit,
    listPrompt: String,
    updateListPrompt: (String) -> Unit,
    listDigestCacheEnabled: Boolean,
    updateListDigestCacheEnabled: (Boolean) -> Unit,
    articleSummaryCacheEnabled: Boolean,
    updateArticleSummaryCacheEnabled: (Boolean) -> Unit,
) {
    val (showApiKey, setApiKeyVisibility) = rememberSaveable {
        mutableStateOf(false)
    }

    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreListDialog by rememberSaveable { mutableStateOf(false) }

    val apiKeyTransformation = if (showApiKey) {
        VisualTransformation.None
    } else {
        PasswordVisualTransformation()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        FormSection(title = stringResource(R.string.settings_section_ai_provider)) {
            Column {
                RowItem {
                    TextField(
                        value = baseURL,
                        onValueChange = updateBaseURL,
                        label = {
                            Text(stringResource(R.string.settings_ai_base_url))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                RowItem {
                    TextField(
                        value = model,
                        onValueChange = updateModel,
                        label = {
                            Text(stringResource(R.string.settings_ai_model))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                RowItem {
                    TextField(
                        value = apiKey,
                        onValueChange = updateApiKey,
                        label = {
                            Text(stringResource(R.string.settings_ai_api_key))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                        ),
                        visualTransformation = apiKeyTransformation,
                        supportingText = {
                            Text(stringResource(R.string.settings_ai_api_key_subtitle))
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { setApiKeyVisibility(!showApiKey) }
                            ) {
                                Icon(
                                    imageVector = if (showApiKey) {
                                        Icons.Filled.Visibility
                                    } else {
                                        Icons.Filled.VisibilityOff
                                    },
                                    contentDescription = stringResource(
                                        if (showApiKey) {
                                            R.string.auth_fields_hide_password
                                        } else {
                                            R.string.auth_fields_show_password
                                        }
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        FormSection(title = stringResource(R.string.settings_section_ai_caches)) {
            RowItem {
                TextSwitch(
                    checked = listDigestCacheEnabled,
                    onCheckedChange = updateListDigestCacheEnabled,
                    title = stringResource(R.string.settings_ai_list_cache),
                    subtitle = stringResource(R.string.settings_ai_list_cache_subtitle),
                )
                TextSwitch(
                    checked = articleSummaryCacheEnabled,
                    onCheckedChange = updateArticleSummaryCacheEnabled,
                    title = stringResource(R.string.settings_ai_article_cache),
                    subtitle = stringResource(R.string.settings_ai_article_cache_subtitle),
                )
            }
        }

        FormSection(title = stringResource(R.string.settings_section_ai_prompt)) {
            RowItem {
                TextField(
                    value = prompt,
                    onValueChange = updatePrompt,
                    label = {
                        Text(stringResource(R.string.settings_ai_prompt))
                    },
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { showRestoreDialog = true }) {
                    Text(stringResource(R.string.settings_ai_restore_prompt))
                }
            }
        }

        FormSection(title = stringResource(R.string.settings_section_ai_list_prompt)) {
            RowItem {
                TextField(
                    value = listPrompt,
                    onValueChange = updateListPrompt,
                    label = {
                        Text(stringResource(R.string.settings_ai_list_prompt))
                    },
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { showRestoreListDialog = true }) {
                    Text(stringResource(R.string.settings_ai_list_restore))
                }
            }
        }

        if (showRestoreDialog) {
            RestorePromptDialog(
                titleRes = R.string.settings_ai_restore_prompt_title,
                messageRes = R.string.settings_ai_restore_prompt_text,
                confirmRes = R.string.settings_ai_restore_prompt_confirm,
                onConfirm = {
                    updatePrompt(AppPreferences.AiOptions.DEFAULT_PROMPT)
                    showRestoreDialog = false
                },
                onDismissRequest = { showRestoreDialog = false },
            )
        }

        if (showRestoreListDialog) {
            RestorePromptDialog(
                titleRes = R.string.settings_ai_list_restore_title,
                messageRes = R.string.settings_ai_list_restore_text,
                confirmRes = R.string.settings_ai_restore_prompt_confirm,
                onConfirm = {
                    updateListPrompt(AppPreferences.AiOptions.DEFAULT_LIST_PROMPT)
                    showRestoreListDialog = false
                },
                onDismissRequest = { showRestoreListDialog = false },
            )
        }
    }
}

@Preview
@Composable
private fun AiSettingsPanelViewPreview() {
    CapyTheme {
        AiSettingsPanelView(
            baseURL = "https://api.deepseek.com/v1",
            updateBaseURL = {},
            model = "deepseek-chat",
            updateModel = {},
            apiKey = "",
            updateApiKey = {},
            prompt = "Résume cet article en français.",
            updatePrompt = {},
            listPrompt = "Résume cette liste en français.",
            updateListPrompt = {},
            listDigestCacheEnabled = true,
            updateListDigestCacheEnabled = {},
            articleSummaryCacheEnabled = true,
            updateArticleSummaryCacheEnabled = {},
        )
    }
}
