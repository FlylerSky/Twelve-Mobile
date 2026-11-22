package com.example.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.network.AppError
import com.example.core.util.ShareSheetHelper
import com.example.data.model.ContentType
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.FeedSkeletonList
import com.example.ui.components.OfflineNoticeBanner
import com.example.ui.components.PostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    contentType: ContentType,
    currentUser: UserProfile?,
    onOpenDetail: (ContentType, String) -> Unit,
    onOpenProfileByUid: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenComposer: (ContentType) -> Unit,
    onRequireLogin: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel(key = contentType.name),
) {
    LaunchedEffect(contentType) {
        viewModel.setContentType(contentType)
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val shareChooserTitle = stringResource(R.string.share_chooser_title)
    val isGuest = currentUser == null
    val canCreate = when (contentType) {
        ContentType.POST -> true // Guest bấm FAB sẽ chuyển sang màn hình đăng nhập
        ContentType.BLOG -> currentUser?.isSA == true
    }

    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is FeedSideEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is FeedSideEffect.OpenShareSheet -> {
                    ShareSheetHelper.shareLink(
                        context = context,
                        title = effect.title,
                        shareUrl = effect.shareUrl,
                        chooserTitle = shareChooserTitle,
                    )
                }
            }
        }
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore, state.hasMore, state.isLoadingMore) {
        if (shouldLoadMore && state.hasMore && !state.isLoadingMore && state.loadMoreError == null) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (canCreate) {
                FloatingActionButton(
                    onClick = {
                        if (isGuest) onRequireLogin() else onOpenComposer(contentType)
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag(
                        if (contentType == ContentType.BLOG) "create_blog_fab" else "create_post_fab",
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.fab_create_post),
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag(if (contentType == ContentType.BLOG) "blog_feed_screen" else "home_feed_screen"),
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = if (canCreate) 88.dp else 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (isGuest) {
                        item(key = "guest_notice") {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                ),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Login,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.guest_banner_notice),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Button(
                                        onClick = onRequireLogin,
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                        modifier = Modifier.heightIn(min = 48.dp),
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

                    when {
                        state.isInitialLoading -> {
                            item(key = "skeleton") {
                                FeedSkeletonList(count = 4)
                            }
                        }
                        state.error != null && state.items.isEmpty() -> {
                            item(key = "error") {
                                ErrorStateView(
                                    message = state.error?.userMessage ?: stringResource(R.string.error_unknown),
                                    isOffline = state.error is AppError.NetworkOffline,
                                    onRetry = viewModel::loadInitial,
                                )
                            }
                        }
                        state.items.isEmpty() -> {
                            item(key = "empty") {
                                EmptyStateView(
                                    message = stringResource(
                                        if (contentType == ContentType.BLOG) R.string.feed_empty_blogs else R.string.feed_empty_posts,
                                    ),
                                )
                            }
                        }
                        else -> {
                            if (state.loadMoreError is AppError.NetworkOffline) {
                                item(key = "offline_banner") {
                                    OfflineNoticeBanner()
                                }
                            }
                            items(state.items, key = { it.id }) { card ->
                                PostCard(
                                    card = card,
                                    onOpenDetail = onOpenDetail,
                                    onOpenProfileByUid = onOpenProfileByUid,
                                    onOpenHashtag = onOpenHashtag,
                                    onRequireLogin = onRequireLogin,
                                    onReact = if (isGuest) {
                                        null
                                    } else {
                                        { c, r -> viewModel.react(c, r) }
                                    },
                                    onShare = if (isGuest) {
                                        null
                                    } else {
                                        { c -> viewModel.share(c) }
                                    },
                                )
                            }
                            item(key = "footer") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when {
                                        state.isLoadingMore -> CircularProgressIndicator()
                                        state.loadMoreError != null -> ErrorStateView(
                                            message = state.loadMoreError?.userMessage ?: stringResource(R.string.error_unknown),
                                            isOffline = state.loadMoreError is AppError.NetworkOffline,
                                            onRetry = viewModel::loadMore,
                                        )
                                        state.hasMore -> OutlinedButton(
                                            onClick = viewModel::loadMore,
                                            shape = CircleShape,
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        ) {
                                            Text(stringResource(R.string.action_load_more))
                                        }
                                        else -> Text(
                                            text = stringResource(R.string.label_no_more_items),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
