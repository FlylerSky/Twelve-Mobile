package com.example.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.network.AppError
import com.example.core.util.ContentLimits
import com.example.core.util.NumberFormatters
import com.example.core.util.ShareSheetHelper
import com.example.core.util.TimeFormatter
import com.example.data.model.CommentItem
import com.example.data.model.ContentType
import com.example.data.model.FullContent
import com.example.data.model.ReactionType
import com.example.data.model.UserProfile
import com.example.ui.components.AuthorHeaderLine
import com.example.ui.components.ErrorStateView
import com.example.ui.components.FeedSkeletonList
import com.example.ui.components.PostScoreDialog
import com.example.ui.components.SafeHtmlRenderer
import com.example.ui.components.UserAvatar
import com.example.ui.components.ViewStatsDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContentDetailScreen(
    contentType: ContentType,
    contentId: String,
    sharerUid: String? = null,
    shareSignature: String? = null,
    sharedFlag: Boolean = false,
    currentUser: UserProfile?,
    onBack: () -> Unit,
    onEditContent: (ContentType, String) -> Unit,
    onOpenProfileByUid: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onRequireLogin: () -> Unit,
    viewModel: ContentDetailViewModel = hiltViewModel(key = "${contentType.name}:$contentId"),
) {
    LaunchedEffect(contentType, contentId) {
        viewModel.openContent(
            type = contentType,
            id = contentId,
            sharerUid = sharerUid,
            shareSignature = shareSignature,
            sharedFlag = sharedFlag,
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onUiStart()
                Lifecycle.Event.ON_STOP -> viewModel.onUiStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onUiStart()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onUiStop()
        }
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val shareChooserTitle = stringResource(R.string.share_chooser_title)

    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is DetailSideEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is DetailSideEffect.OpenShareSheet -> {
                    ShareSheetHelper.shareLink(
                        context = context,
                        title = effect.title,
                        shareUrl = effect.shareUrl,
                        chooserTitle = shareChooserTitle,
                    )
                }
                DetailSideEffect.ContentDeleted -> onBack()
            }
        }
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val content = state.content
    val isSignedIn = currentUser != null
    val isAuthor = content != null && currentUser?.uid == content.author?.uid
    val isSA = currentUser?.isSA == true
    val canPin = contentType == ContentType.POST && isAuthor
    val isPinned = canPin && currentUser?.pinnedPostIds?.contains(content?.id) == true
    val canEditOrDelete = content != null && when (contentType) {
        ContentType.POST -> isAuthor || isSA
        ContentType.BLOG -> isSA
    }

    var showViewStats by remember { mutableStateOf(false) }
    var showScoreStats by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeletePostConfirm by remember { mutableStateOf(false) }
    var editingComment by remember { mutableStateOf<CommentItem?>(null) }
    var deletingComment by remember { mutableStateOf<CommentItem?>(null) }

    val commentUpdatedOk = stringResource(R.string.comment_updated_ok)
    val commentDeletedOk = stringResource(R.string.comment_deleted_ok)
    val postDeletedOk = stringResource(R.string.post_deleted_ok)
    val postPinnedOk = stringResource(R.string.post_pinned_ok)
    val postUnpinnedOk = stringResource(R.string.post_unpinned_ok)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier
            .fillMaxSize()
            .testTag("content_detail_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (contentType == ContentType.BLOG) R.string.detail_blog_title else R.string.detail_post_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (content != null && canPin) {
                        IconButton(
                            onClick = {
                                viewModel.togglePin(
                                    currentlyPinned = isPinned,
                                    pinnedOkMsg = postPinnedOk,
                                    unpinnedOkMsg = postUnpinnedOk,
                                )
                            },
                            enabled = !state.isPinning,
                            modifier = Modifier.testTag("detail_pin_button"),
                        ) {
                            Icon(
                                imageVector = if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(
                                    if (isPinned) R.string.post_action_unpin else R.string.post_action_pin,
                                ),
                                tint = if (isPinned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    if (content != null && (canPin || canEditOrDelete)) {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.testTag("detail_more_menu_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.action_more_options),
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                if (canPin) {
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                                contentDescription = null,
                                            )
                                        },
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (isPinned) R.string.post_action_unpin else R.string.post_action_pin,
                                                ),
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            viewModel.togglePin(
                                                currentlyPinned = isPinned,
                                                pinnedOkMsg = postPinnedOk,
                                                unpinnedOkMsg = postUnpinnedOk,
                                            )
                                        },
                                        enabled = !state.isPinning,
                                    )
                                }
                                if (canEditOrDelete) {
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Outlined.Edit, contentDescription = null)
                                        },
                                        text = { Text(stringResource(R.string.post_action_edit)) },
                                        onClick = {
                                            showMenu = false
                                            onEditContent(contentType, content.id)
                                        },
                                    )
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        text = {
                                            Text(
                                                text = stringResource(R.string.post_action_delete),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            showDeletePostConfirm = true
                                        },
                                    )
                                }
                            }
                        }
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
                state.isLoading -> {
                    FeedSkeletonList(
                        count = 2,
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .padding(16.dp),
                    )
                }
                state.error != null || content == null -> {
                    ErrorStateView(
                        message = state.error?.userMessage ?: stringResource(R.string.error_unknown),
                        isOffline = state.error is AppError.NetworkOffline,
                        onRetry = viewModel::retry,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                }
                else -> {
                    val relTime = remember(content.createdAt) { TimeFormatter.formatRelativeTime(content.createdAt) }
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize()
                            .imePadding(),
                        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item(key = "article_header") {
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AuthorHeaderLine(
                                        displayName = content.author?.displayName,
                                        tagName = content.author?.tagName,
                                        badges = content.author?.badges.orEmpty(),
                                        avatarUrl = content.author?.avatarUrl,
                                        subtitle = relTime,
                                        onAuthorClick = content.author?.uid?.takeIf { it.isNotBlank() }?.let { uid ->
                                            { onOpenProfileByUid(uid) }
                                        },
                                        trailing = if (isPinned) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Rounded.PushPin,
                                                    contentDescription = stringResource(R.string.label_pinned),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    )

                                    val cover = content.coverImageUrl?.takeIf { it.startsWith("https://", ignoreCase = true) }
                                    if (cover != null) {
                                        AsyncImage(
                                            model = cover,
                                            contentDescription = content.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .clip(MaterialTheme.shapes.small),
                                        )
                                    }

                                    Text(
                                        text = content.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                    )

                                    SafeHtmlRenderer(contentHtml = content.contentHtml)

                                    if (content.hashtags.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            content.hashtags.forEach { tag ->
                                                AssistChip(
                                                    onClick = { onOpenHashtag(tag) },
                                                    label = { Text(tag) },
                                                    shape = CircleShape,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Lưới nút pill tương tác & thống kê giống Twelve Web (👍/👎, 👁/🔗, ★ điểm)
                        item(key = "detail_metrics_grid") {
                            DetailMetricsPillGrid(
                                content = content,
                                myReaction = state.myReaction,
                                onLike = {
                                    if (isSignedIn) viewModel.react(ReactionType.LIKE) else onRequireLogin()
                                },
                                onDislike = {
                                    if (isSignedIn) viewModel.react(ReactionType.DISLIKE) else onRequireLogin()
                                },
                                onViewsClick = { showViewStats = true },
                                onShare = {
                                    if (isSignedIn) viewModel.share() else onRequireLogin()
                                },
                                onScoreClick = { showScoreStats = true },
                            )
                        }

                        // Thẻ Bình luận & Khung soạn bình luận tích hợp giống Twelve Web
                        item(key = "comments_composer_card") {
                            CommentSectionComposerCard(
                                commentCount = content.commentCount,
                                isSignedIn = isSignedIn,
                                currentUser = currentUser,
                                commentInput = state.commentInput,
                                isSending = state.isSendingComment,
                                onInputChanged = viewModel::onCommentInputChanged,
                                onSendComment = viewModel::sendComment,
                                onRequireLogin = onRequireLogin,
                            )
                        }

                        when {
                            state.isLoadingComments -> {
                                item(key = "comments_loading") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                            state.commentsError != null && state.comments.isEmpty() -> {
                                item(key = "comments_error") {
                                    ErrorStateView(
                                        message = state.commentsError?.userMessage ?: stringResource(R.string.error_unknown),
                                        isOffline = state.commentsError is AppError.NetworkOffline,
                                        onRetry = viewModel::retry,
                                    )
                                }
                            }
                            state.comments.isEmpty() -> {
                                item(key = "comments_empty") {
                                    Text(
                                        text = stringResource(R.string.comments_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                    )
                                }
                            }
                            else -> {
                                items(state.comments, key = { it.id }) { comment ->
                                    val isCommentOwner = currentUser?.uid != null && comment.author?.uid == currentUser.uid
                                    val canDeleteComment = isCommentOwner || isSA
                                    CommentRowCard(
                                        comment = comment,
                                        canEdit = isCommentOwner,
                                        canDelete = canDeleteComment,
                                        onEdit = { editingComment = comment },
                                        onDelete = { deletingComment = comment },
                                        onOpenProfileByUid = onOpenProfileByUid,
                                    )
                                }
                                if (state.hasMoreComments || state.isLoadingMoreComments || state.commentsError != null) {
                                    item(key = "comments_footer") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            when {
                                                state.isLoadingMoreComments -> CircularProgressIndicator()
                                                state.commentsError != null -> ErrorStateView(
                                                    message = state.commentsError?.userMessage ?: stringResource(R.string.error_unknown),
                                                    isOffline = state.commentsError is AppError.NetworkOffline,
                                                    onRetry = viewModel::loadMoreComments,
                                                )
                                                else -> OutlinedButton(
                                                    onClick = viewModel::loadMoreComments,
                                                    shape = CircleShape,
                                                    modifier = Modifier.heightIn(min = 48.dp),
                                                ) {
                                                    Text(stringResource(R.string.comments_load_more))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showViewStats) {
                        ViewStatsDialog(
                            viewCount = content.viewCount,
                            aiViewCount = content.aiViewCount,
                            onDismiss = { showViewStats = false },
                        )
                    }
                    if (showScoreStats) {
                        PostScoreDialog(
                            viewCount = content.viewCount,
                            likeCount = content.likeCount,
                            dislikeCount = content.dislikeCount,
                            commentCount = content.commentCount,
                            shareCount = content.shareCount,
                            postScore = content.postScore,
                            onDismiss = { showScoreStats = false },
                        )
                    }
                }
            }
        }
    }

    if (showDeletePostConfirm) {
        AlertDialog(
            onDismissRequest = { if (!state.isDeletingContent) showDeletePostConfirm = false },
            title = { Text(stringResource(R.string.post_delete_title)) },
            text = { Text(stringResource(R.string.post_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeletePostConfirm = false
                        viewModel.deleteContent(postDeletedOk)
                    },
                    enabled = !state.isDeletingContent,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeletePostConfirm = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    editingComment?.let { target ->
        var editText by remember(target.id) { mutableStateOf(target.content) }
        AlertDialog(
            onDismissRequest = { editingComment = null },
            title = { Text(stringResource(R.string.comment_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        minLines = 3,
                        maxLines = 6,
                        isError = editText.length > ContentLimits.COMMENT_MAX,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.comment_char_counter, editText.length, ContentLimits.COMMENT_MAX),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (editText.length > ContentLimits.COMMENT_MAX) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.editComment(target.id, editText, commentUpdatedOk)
                        editingComment = null
                    },
                    enabled = editText.trim().isNotEmpty() && editText.length <= ContentLimits.COMMENT_MAX,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingComment = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    deletingComment?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingComment = null },
            title = { Text(stringResource(R.string.comment_delete_title)) },
            text = { Text(stringResource(R.string.comment_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteComment(target.id, commentDeletedOk)
                        deletingComment = null
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingComment = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CommentRowCard(
    comment: CommentItem,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenProfileByUid: (String) -> Unit,
) {
    val relTime = remember(comment.createdAt) { TimeFormatter.formatRelativeTime(comment.createdAt) }
    val editedSuffix = if (comment.isEdited) " · ${stringResource(R.string.label_edited)}" else ""
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AuthorHeaderLine(
                displayName = comment.author?.displayName,
                tagName = comment.author?.tagName,
                badges = comment.author?.badges.orEmpty(),
                avatarUrl = comment.author?.avatarUrl,
                subtitle = relTime + editedSuffix,
                onAuthorClick = comment.author?.uid?.takeIf { it.isNotBlank() }?.let { uid ->
                    { onOpenProfileByUid(uid) }
                },
                trailing = if (canEdit || canDelete) {
                    {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.action_more_options),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                if (canEdit) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                        text = { Text(stringResource(R.string.comment_action_edit)) },
                                        onClick = {
                                            menuExpanded = false
                                            onEdit()
                                        },
                                    )
                                }
                                if (canDelete) {
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        text = {
                                            Text(
                                                text = stringResource(R.string.comment_action_delete),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onDelete()
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    null
                },
            )
            // Bình luận lưu văn bản thuần (comments.controller.js) -> luôn hiển thị bằng Text thuần, KHÔNG bao giờ parse HTML.
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DetailMetricsPillGrid(
    content: FullContent,
    myReaction: ReactionType?,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onViewsClick: () -> Unit,
    onShare: () -> Unit,
    onScoreClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val likeSelected = myReaction == ReactionType.LIKE
            DetailMetricPillButton(
                icon = if (likeSelected) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                label = NumberFormatters.formatCount(content.likeCount),
                contentDesc = stringResource(R.string.metric_like),
                selected = likeSelected,
                activeColor = MaterialTheme.colorScheme.primary,
                onClick = onLike,
                modifier = Modifier
                    .weight(1f)
                    .testTag("detail_like_button"),
            )
            val dislikeSelected = myReaction == ReactionType.DISLIKE
            DetailMetricPillButton(
                icon = if (dislikeSelected) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                label = NumberFormatters.formatCount(content.dislikeCount),
                contentDesc = stringResource(R.string.metric_dislike),
                selected = dislikeSelected,
                activeColor = MaterialTheme.colorScheme.error,
                onClick = onDislike,
                modifier = Modifier
                    .weight(1f)
                    .testTag("detail_dislike_button"),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DetailMetricPillButton(
                icon = Icons.Rounded.Visibility,
                label = NumberFormatters.formatCount(content.viewCount),
                contentDesc = stringResource(R.string.metric_view),
                selected = false,
                activeColor = MaterialTheme.colorScheme.primary,
                onClick = onViewsClick,
                modifier = Modifier.weight(1f),
            )
            DetailMetricPillButton(
                icon = Icons.Outlined.Share,
                label = NumberFormatters.formatCount(content.shareCount),
                contentDesc = stringResource(R.string.metric_share),
                selected = false,
                activeColor = MaterialTheme.colorScheme.primary,
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .testTag("detail_share_button"),
            )
        }

        DetailMetricPillButton(
            icon = Icons.Rounded.Grade,
            label = NumberFormatters.formatScore(content.postScore),
            contentDesc = stringResource(R.string.metric_score),
            selected = false,
            activeColor = MaterialTheme.colorScheme.primary,
            iconTint = MaterialTheme.colorScheme.onSurface,
            onClick = onScoreClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DetailMetricPillButton(
    icon: ImageVector,
    label: String,
    contentDesc: String,
    selected: Boolean,
    activeColor: Color,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentTint = if (selected) activeColor else iconTint
    val borderColor = if (selected) {
        activeColor.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    val containerColor = if (selected) {
        activeColor.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = contentTint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) activeColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun CommentSectionComposerCard(
    commentCount: Int,
    isSignedIn: Boolean,
    currentUser: UserProfile?,
    commentInput: String,
    isSending: Boolean,
    onInputChanged: (String) -> Unit,
    onSendComment: () -> Unit,
    onRequireLogin: () -> Unit,
) {
    val canSend = !isSending &&
        commentInput.trim().isNotEmpty() &&
        commentInput.length <= ContentLimits.COMMENT_MAX
    val overLimit = commentInput.length > ContentLimits.COMMENT_MAX

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isSignedIn) {
                    UserAvatar(
                        avatarUrl = currentUser?.avatarUrl,
                        displayName = currentUser?.displayName ?: currentUser?.tagName,
                        size = 34.dp,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.comments_section_title,
                        NumberFormatters.formatCount(commentCount),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (isSignedIn) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(
                        1.dp,
                        if (overLimit) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .padding(end = 8.dp),
                        ) {
                            if (commentInput.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.comment_input_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            BasicTextField(
                                value = commentInput,
                                onValueChange = onInputChanged,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                maxLines = 6,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("comment_input_field"),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (commentInput.isNotEmpty()) {
                                Text(
                                    text = stringResource(
                                        R.string.comment_char_counter,
                                        commentInput.length,
                                        ContentLimits.COMMENT_MAX,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (overLimit) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = onSendComment,
                                enabled = canSend,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("comment_send_button"),
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = stringResource(R.string.comment_send_button),
                                        tint = if (canSend) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.comment_sign_in_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onRequireLogin,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .heightIn(min = 48.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.auth_sign_in_button),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

