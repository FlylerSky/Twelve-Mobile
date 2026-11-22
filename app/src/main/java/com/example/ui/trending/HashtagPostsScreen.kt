package com.example.ui.trending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.network.AppError
import com.example.core.util.ShareSheetHelper
import com.example.data.model.ContentType
import com.example.data.repository.ScoreMode
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.FeedSkeletonList
import com.example.ui.components.PostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashtagPostsScreen(
    tag: String,
    initialMode: ScoreMode,
    isGuest: Boolean,
    onBack: () -> Unit,
    onOpenDetail: (ContentType, String) -> Unit,
    onOpenProfileByUid: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onRequireLogin: () -> Unit,
    viewModel: HashtagPostsViewModel = hiltViewModel(key = "ht:${tag.lowercase()}"),
) {
    LaunchedEffect(tag, initialMode) {
        viewModel.load(tag, initialMode)
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val shareChooserTitle = stringResource(R.string.share_chooser_title)

    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is HashtagSideEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is HashtagSideEffect.OpenShareSheet -> {
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.displayTag.ifBlank { tag },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                val modes = listOf(
                    ScoreMode.CLASSIC to stringResource(R.string.trending_mode_classic),
                    ScoreMode.ADVANCED to stringResource(R.string.trending_mode_advanced),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { viewModel.selectMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.widthIn(max = 720.dp),
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                when {
                    state.isLoading -> FeedSkeletonList(
                        count = 3,
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .padding(16.dp),
                    )
                    state.error != null -> ErrorStateView(
                        message = state.error?.userMessage ?: stringResource(R.string.error_unknown),
                        isOffline = state.error is AppError.NetworkOffline,
                        onRetry = viewModel::retry,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                    state.posts.isEmpty() -> EmptyStateView(
                        message = stringResource(R.string.trending_hashtag_posts_empty),
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                    else -> LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.posts, key = { "${it.type}_${it.id}" }) { card ->
                            PostCard(
                                card = card,
                                onOpenDetail = onOpenDetail,
                                onOpenProfileByUid = onOpenProfileByUid,
                                onOpenHashtag = onOpenHashtag,
                                onRequireLogin = onRequireLogin,
                                onReact = if (isGuest) null else { c, r -> viewModel.react(c, r) },
                                onShare = if (isGuest) null else { c -> viewModel.share(c) },
                            )
                        }
                    }
                }
            }
        }
    }
}
