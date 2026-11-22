package com.example.ui.composer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.network.AppError
import com.example.core.util.ContentLimits
import com.example.core.util.NumberFormatters
import com.example.core.util.SafeHtmlParser
import com.example.data.model.ContentType
import com.example.ui.components.ErrorStateView
import com.example.ui.components.SafeHtmlRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostComposerScreen(
    contentType: ContentType,
    editContentId: String? = null,
    onBack: () -> Unit,
    onCreated: (ContentType, String) -> Unit,
    onUpdated: (ContentType, String) -> Unit,
    viewModel: PostComposerViewModel = hiltViewModel(key = "composer:${contentType.name}:${editContentId.orEmpty()}"),
) {
    LaunchedEffect(contentType, editContentId) {
        viewModel.init(contentType, editContentId)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ComposerResultEvent.Created -> onCreated(event.contentType, event.id)
                is ComposerResultEvent.Updated -> onUpdated(event.contentType, event.id)
            }
        }
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showInsertLinkDialog by remember { mutableStateOf(false) }
    var showInsertImageDialog by remember { mutableStateOf(false) }
    val emptyContentError = stringResource(R.string.composer_empty_content_error)

    val handleAttemptBack: () -> Unit = {
        if (state.hasUserEdited && !state.isSubmitting) {
            showDiscardDialog = true
        } else if (!state.isSubmitting) {
            onBack()
        }
    }

    BackHandler(enabled = !state.isSubmitting) {
        handleAttemptBack()
    }

    val screenTitle = when {
        state.contentType == ContentType.BLOG && state.isEditMode -> stringResource(R.string.composer_edit_blog_title)
        state.contentType == ContentType.BLOG -> stringResource(R.string.composer_create_blog_title)
        state.isEditMode -> stringResource(R.string.composer_edit_post_title)
        else -> stringResource(R.string.composer_create_post_title)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier
            .fillMaxSize()
            .testTag("post_composer_screen"),
        topBar = {
            TopAppBar(
                title = { Text(screenTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = handleAttemptBack,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.testTag("composer_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::togglePreview,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(
                            imageVector = if (state.isPreviewMode) Icons.Rounded.Edit else Icons.Rounded.Visibility,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                        Text(
                            stringResource(
                                if (state.isPreviewMode) R.string.composer_edit_toggle else R.string.composer_preview_toggle,
                            ),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                state.isLoadingInitial -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.initialLoadError != null -> {
                    ErrorStateView(
                        message = state.initialLoadError?.userMessage ?: stringResource(R.string.error_unknown),
                        isOffline = state.initialLoadError is AppError.NetworkOffline,
                        onRetry = viewModel::retryLoadExisting,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.submitError != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = state.submitError.orEmpty(),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }

                        if (state.isPreviewMode) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    val previewCover = SafeHtmlParser.isSafeHttpsMedia(state.coverImageUrl)
                                    if (previewCover != null) {
                                        AsyncImage(
                                            model = previewCover,
                                            contentDescription = state.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .clip(MaterialTheme.shapes.small),
                                        )
                                    }
                                    Text(
                                        text = state.title.ifBlank { stringResource(R.string.composer_title_placeholder) },
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    SafeHtmlRenderer(contentHtml = state.compiledHtml)
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = state.title,
                                onValueChange = viewModel::onTitleChanged,
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.composer_title_label,
                                            ContentLimits.TITLE_MIN,
                                            ContentLimits.TITLE_MAX,
                                        ),
                                    )
                                },
                                placeholder = { Text(stringResource(R.string.composer_title_placeholder)) },
                                isError = state.titleError != null,
                                supportingText = {
                                    Text(
                                        state.titleError
                                            ?: "${state.title.trim().length} / ${ContentLimits.TITLE_MAX}",
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("composer_title_input"),
                            )

                            OutlinedTextField(
                                value = state.coverImageUrl,
                                onValueChange = viewModel::onCoverImageUrlChanged,
                                label = { Text(stringResource(R.string.composer_cover_label)) },
                                placeholder = { Text(stringResource(R.string.composer_cover_placeholder)) },
                                singleLine = true,
                                isError = state.coverError != null,
                                supportingText = state.coverError?.let { err -> { Text(err) } },
                                trailingIcon = {
                                    if (state.coverImageUrl.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onCoverImageUrlChanged("") }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.composer_cover_clear),
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("composer_cover_input"),
                            )

                            val safeCoverPreview = SafeHtmlParser.isSafeHttpsMedia(state.coverImageUrl)
                            if (safeCoverPreview != null) {
                                AsyncImage(
                                    model = safeCoverPreview,
                                    contentDescription = stringResource(R.string.composer_cover_label),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(MaterialTheme.shapes.small),
                                )
                            }

                            val tagValidation = state.hashtagValidation
                            OutlinedTextField(
                                value = state.hashtagsInput,
                                onValueChange = viewModel::onHashtagsChanged,
                                label = { Text(stringResource(R.string.composer_hashtags_label)) },
                                placeholder = { Text(stringResource(R.string.composer_hashtags_placeholder)) },
                                singleLine = true,
                                isError = tagValidation.error != null,
                                supportingText = {
                                    Text(
                                        tagValidation.error ?: stringResource(
                                            R.string.composer_hashtags_helper,
                                            ContentLimits.HASHTAG_MAX_COUNT,
                                        ),
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("composer_hashtags_input"),
                            )

                            Text(
                                text = stringResource(R.string.composer_content_label),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )

                            // Thanh công cụ chèn nhanh định dạng HTML tương thích Quill
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ToolbarFormatChip(
                                    label = stringResource(R.string.composer_toolbar_h2),
                                    onClick = { viewModel.onBodyChanged(appendSnippet(state.bodyText, "\n## ")) },
                                )
                                ToolbarFormatChip(
                                    label = stringResource(R.string.composer_toolbar_h3),
                                    onClick = { viewModel.onBodyChanged(appendSnippet(state.bodyText, "\n### ")) },
                                )
                                ToolbarFormatChip(
                                    icon = Icons.Rounded.FormatBold,
                                    label = stringResource(R.string.composer_toolbar_bold),
                                    onClick = { viewModel.onBodyChanged(appendSnippet(state.bodyText, "**văn bản đậm**")) },
                                )
                                ToolbarFormatChip(
                                    icon = Icons.Rounded.FormatItalic,
                                    label = stringResource(R.string.composer_toolbar_italic),
                                    onClick = { viewModel.onBodyChanged(appendSnippet(state.bodyText, "*văn bản nghiêng*")) },
                                )
                                ToolbarFormatChip(
                                    icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                                    label = stringResource(R.string.composer_toolbar_list),
                                    onClick = { viewModel.onBodyChanged(appendSnippet(state.bodyText, "\n- ")) },
                                )
                                ToolbarFormatChip(
                                    icon = Icons.Rounded.FormatQuote,
                                    label = stringResource(R.string.composer_toolbar_quote),
                                    onClick = { viewModel.onBodyChanged(appendSnippet(state.bodyText, "\n> ")) },
                                )
                                ToolbarFormatChip(
                                    icon = Icons.Rounded.Code,
                                    label = stringResource(R.string.composer_toolbar_code),
                                    onClick = { viewModel.onBodyChanged(appendSnippet(state.bodyText, "\n```\n\n```")) },
                                )
                                ToolbarFormatChip(
                                    icon = Icons.Rounded.Link,
                                    label = stringResource(R.string.composer_toolbar_link),
                                    onClick = { showInsertLinkDialog = true },
                                )
                                ToolbarFormatChip(
                                    icon = Icons.Rounded.Image,
                                    label = stringResource(R.string.composer_toolbar_image),
                                    onClick = { showInsertImageDialog = true },
                                )
                            }

                            val assessment = state.htmlAssessment
                            OutlinedTextField(
                                value = state.bodyText,
                                onValueChange = viewModel::onBodyChanged,
                                placeholder = { Text(stringResource(R.string.composer_content_placeholder)) },
                                minLines = 8,
                                maxLines = 16,
                                isError = assessment.severity == ContentLimits.Severity.ERROR,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("composer_body_input"),
                            )

                            if (assessment.severity != ContentLimits.Severity.OK) {
                                Text(
                                    text = assessment.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (assessment.severity == ContentLimits.Severity.ERROR) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.composer_content_chars,
                                    NumberFormatters.formatIntegerVi(assessment.chars),
                                    NumberFormatters.formatIntegerVi(ContentLimits.HTML_MAX_CHARS),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.composer_media_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        ) {
                            OutlinedButton(
                                onClick = handleAttemptBack,
                                enabled = !state.isSubmitting,
                                shape = CircleShape,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Button(
                                onClick = { viewModel.submit(emptyContentError) },
                                enabled = !state.isSubmitting &&
                                    state.htmlAssessment.severity != ContentLimits.Severity.ERROR,
                                shape = CircleShape,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("composer_submit_button"),
                            ) {
                                Text(
                                    text = when {
                                        state.isSubmitting -> stringResource(R.string.composer_submitting)
                                        state.isEditMode -> stringResource(R.string.composer_save_button)
                                        else -> stringResource(R.string.composer_publish_button)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.composer_discard_title)) },
            text = { Text(stringResource(R.string.composer_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.composer_discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showInsertLinkDialog) {
        InsertUrlSnippetDialog(
            title = stringResource(R.string.composer_insert_link_title),
            labelText = stringResource(R.string.composer_link_text_label),
            requireHttpsOnly = false,
            onDismiss = { showInsertLinkDialog = false },
            onInsert = { text, url ->
                val label = text.ifBlank { url }
                viewModel.onBodyChanged(appendSnippet(state.bodyText, "[$label]($url)"))
                showInsertLinkDialog = false
            },
        )
    }

    if (showInsertImageDialog) {
        InsertUrlSnippetDialog(
            title = stringResource(R.string.composer_insert_image_title),
            labelText = stringResource(R.string.composer_alt_text_label),
            requireHttpsOnly = true,
            onDismiss = { showInsertImageDialog = false },
            onInsert = { alt, url ->
                viewModel.onBodyChanged(appendSnippet(state.bodyText, "\n![${alt.trim()}]($url)\n"))
                showInsertImageDialog = false
            },
        )
    }
}

@Composable
private fun ToolbarFormatChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        leadingIcon = icon?.let { vector ->
            { Icon(imageVector = vector, contentDescription = null) }
        },
        label = { Text(label) },
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun InsertUrlSnippetDialog(
    title: String,
    labelText: String,
    requireHttpsOnly: Boolean,
    onDismiss: () -> Unit,
    onInsert: (String, String) -> Unit,
) {
    var caption by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidMsg = stringResource(R.string.composer_invalid_url)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text(labelText) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.composer_url_label)) },
                    placeholder = { Text("https://...") },
                    isError = error != null,
                    supportingText = error?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val safe = if (requireHttpsOnly) {
                        SafeHtmlParser.isSafeHttpsMedia(url)
                    } else {
                        SafeHtmlParser.isSafeWebLink(url)
                    }
                    if (safe == null) {
                        error = invalidMsg
                    } else {
                        onInsert(caption, safe)
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_insert))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun appendSnippet(existing: String, snippet: String): String {
    if (existing.isEmpty()) return snippet.trimStart('\n')
    val separator = if (snippet.startsWith("\n") || existing.endsWith("\n") || existing.endsWith(" ")) "" else " "
    return existing + separator + snippet.trimStart('\n').let {
        if (snippet.startsWith("\n") && !existing.endsWith("\n")) "\n$it" else it
    }
}
