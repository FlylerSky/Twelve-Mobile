package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.network.AppError
import com.example.core.util.TimeFormatter
import com.example.ui.components.AuthorHeaderLine
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.FeedSkeletonList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorsScreen(
    onBack: () -> Unit,
    onOpenProfileByUid: (String) -> Unit,
    viewModel: VisitorsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("visitors_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.visitors_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("visitors_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.load(isRefresh = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                when {
                    state.isLoading -> FeedSkeletonList(
                        count = 4,
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .padding(16.dp),
                    )
                    state.error != null -> ErrorStateView(
                        message = state.error?.userMessage ?: stringResource(R.string.error_unknown),
                        isOffline = state.error is AppError.NetworkOffline,
                        onRetry = { viewModel.load(isRefresh = false) },
                        modifier = Modifier.widthIn(max = 640.dp),
                    )
                    state.visitors.isEmpty() -> EmptyStateView(
                        message = stringResource(R.string.visitors_empty),
                        modifier = Modifier.widthIn(max = 640.dp),
                    )
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.visitors, key = { it.uid }) { visitor ->
                                Card(
                                    onClick = { onOpenProfileByUid(visitor.uid) },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("visitor_card_${visitor.uid}"),
                                ) {
                                    Box(modifier = Modifier.padding(14.dp)) {
                                        AuthorHeaderLine(
                                            displayName = visitor.displayName,
                                            tagName = visitor.tagName,
                                            badges = visitor.badges,
                                            avatarUrl = visitor.avatarUrl,
                                            subtitle = TimeFormatter.formatRelativeTime(visitor.visitedAt)
                                                .takeIf { it.isNotBlank() },
                                        )
                                    }
                                }
                            }
                            item(key = "visitors_footer") {
                                Text(
                                    text = stringResource(R.string.visitors_footer_note, state.max),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
