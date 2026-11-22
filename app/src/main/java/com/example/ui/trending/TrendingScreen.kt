package com.example.ui.trending

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.network.AppError
import com.example.core.util.NumberFormatters
import com.example.core.util.ShareSheetHelper
import com.example.core.util.TagNameNormalizer
import com.example.data.model.ContentType
import com.example.data.model.HashtagRankingItem
import com.example.data.model.RankingUser
import com.example.data.model.SearchHashtagItem
import com.example.data.repository.ScoreMode
import com.example.data.repository.SearchSort
import com.example.data.repository.SearchType
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.FeedSkeletonList
import com.example.ui.components.PostCard
import com.example.ui.components.UserAvatar
import com.example.ui.components.UserBadgeRow
import com.example.ui.components.UserTagPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingScreen(
    isGuest: Boolean,
    onOpenProfileByUid: (String) -> Unit,
    onOpenHashtagPosts: (String, ScoreMode) -> Unit,
    onOpenDetail: (ContentType, String) -> Unit,
    onRequireLogin: () -> Unit,
    viewModel: TrendingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val shareChooserTitle = stringResource(R.string.share_chooser_title)

    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is TrendingSideEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is TrendingSideEffect.OpenShareSheet -> {
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
        modifier = Modifier
            .fillMaxSize()
            .testTag("trending_screen"),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                PrimaryTabRow(selectedTabIndex = state.mainTab.ordinal) {
                    Tab(
                        selected = state.mainTab == TrendingMainTab.USERS,
                        onClick = { viewModel.selectMainTab(TrendingMainTab.USERS) },
                        text = { Text(stringResource(R.string.trending_tab_users), maxLines = 1) },
                    )
                    Tab(
                        selected = state.mainTab == TrendingMainTab.HASHTAGS,
                        onClick = { viewModel.selectMainTab(TrendingMainTab.HASHTAGS) },
                        text = { Text(stringResource(R.string.trending_tab_hashtag), maxLines = 1) },
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.mainTab == TrendingMainTab.USERS) {
                        val userPeriods = listOf(
                            UsersPeriodTab.WEEKLY to stringResource(R.string.trending_users_weekly),
                            UsersPeriodTab.ALLTIME to stringResource(R.string.trending_users_alltime),
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            userPeriods.forEachIndexed { index, (period, label) ->
                                SegmentedButton(
                                    selected = state.usersPeriod == period,
                                    onClick = { viewModel.selectUsersPeriod(period) },
                                    shape = SegmentedButtonDefaults.itemShape(index, userPeriods.size),
                                    label = {
                                        Text(
                                            text = label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                    } else {
                        val sections = listOf(
                            HashtagSectionTab.WEEKLY to stringResource(R.string.trending_section_weekly),
                            HashtagSectionTab.EXPLORE to stringResource(R.string.trending_section_explore),
                            HashtagSectionTab.SEARCH to stringResource(R.string.trending_section_search),
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            sections.forEachIndexed { index, (section, label) ->
                                SegmentedButton(
                                    selected = state.hashtagSection == section,
                                    onClick = { viewModel.selectHashtagSection(section) },
                                    shape = SegmentedButtonDefaults.itemShape(index, sections.size),
                                    label = {
                                        Text(
                                            text = label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = state.hashtagMode == ScoreMode.CLASSIC,
                                onClick = { viewModel.selectHashtagMode(ScoreMode.CLASSIC) },
                                label = {
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = stringResource(R.string.trending_mode_classic),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            )
                            FilterChip(
                                selected = state.hashtagMode == ScoreMode.ADVANCED,
                                onClick = { viewModel.selectHashtagMode(ScoreMode.ADVANCED) },
                                label = {
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = stringResource(R.string.trending_mode_advanced),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (state.mainTab) {
                    TrendingMainTab.USERS -> {
                        if (state.usersPeriod == UsersPeriodTab.WEEKLY) {
                            state.weeklyUsers?.weekCode?.let { weekCode ->
                                item("week_chip_users") { WeekCodeBadge(weekCode) }
                            }
                            when {
                                state.isLoadingWeeklyUsers -> item("u_w_skel") { FeedSkeletonList(count = 4) }
                                state.weeklyUsersError != null -> item("u_w_err") {
                                    ErrorStateView(
                                        message = state.weeklyUsersError?.userMessage ?: stringResource(R.string.error_unknown),
                                        isOffline = state.weeklyUsersError is AppError.NetworkOffline,
                                        onRetry = viewModel::retryCurrent,
                                    )
                                }
                                state.weeklyUsers?.ranking.isNullOrEmpty() -> item("u_w_empty") {
                                    EmptyStateView(stringResource(R.string.trending_empty_users_weekly))
                                }
                                else -> {
                                    items(state.weeklyUsers!!.ranking, key = { "uw_${it.uid}" }) { user ->
                                        RankingUserRow(
                                            user = user,
                                            scoreValue = user.weeklyGrowth ?: 0,
                                            scoreLabel = stringResource(R.string.trending_growth_label),
                                            onClick = { onOpenProfileByUid(user.uid) },
                                        )
                                    }
                                }
                            }
                        } else {
                            when {
                                state.isLoadingAllTimeUsers -> item("u_a_skel") { FeedSkeletonList(count = 4) }
                                state.allTimeUsersError != null -> item("u_a_err") {
                                    ErrorStateView(
                                        message = state.allTimeUsersError?.userMessage ?: stringResource(R.string.error_unknown),
                                        isOffline = state.allTimeUsersError is AppError.NetworkOffline,
                                        onRetry = viewModel::retryCurrent,
                                    )
                                }
                                state.allTimeUsers?.ranking.isNullOrEmpty() -> item("u_a_empty") {
                                    EmptyStateView(stringResource(R.string.trending_empty_users_alltime))
                                }
                                else -> {
                                    items(state.allTimeUsers!!.ranking, key = { "ua_${it.uid}" }) { user ->
                                        RankingUserRow(
                                            user = user,
                                            scoreValue = user.accountScore,
                                            scoreLabel = stringResource(R.string.trending_account_score_label),
                                            onClick = { onOpenProfileByUid(user.uid) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TrendingMainTab.HASHTAGS -> {
                        when (state.hashtagSection) {
                            HashtagSectionTab.WEEKLY -> {
                                val data = state.hashtagWeeklyByMode[state.hashtagMode]
                                data?.weekCode?.let { weekCode ->
                                    item("week_chip_tags") { WeekCodeBadge(weekCode) }
                                }
                                when {
                                    state.isLoadingHashtagWeekly -> item("h_w_skel") { FeedSkeletonList(count = 4) }
                                    state.hashtagWeeklyError != null -> item("h_w_err") {
                                        ErrorStateView(
                                            message = state.hashtagWeeklyError?.userMessage ?: stringResource(R.string.error_unknown),
                                            isOffline = state.hashtagWeeklyError is AppError.NetworkOffline,
                                            onRetry = viewModel::retryCurrent,
                                        )
                                    }
                                    data?.ranking.isNullOrEmpty() -> item("h_w_empty") {
                                        EmptyStateView(stringResource(R.string.trending_empty_hashtag_weekly))
                                    }
                                    else -> {
                                        items(data!!.ranking, key = { "hw_${it.tag}" }) { item ->
                                            HashtagRankingRow(
                                                item = item,
                                                scoreValue = item.growth ?: 0,
                                                scoreLabel = stringResource(R.string.trending_growth_label),
                                                onClick = { onOpenHashtagPosts(item.tag, state.hashtagMode) },
                                            )
                                        }
                                    }
                                }
                            }

                            HashtagSectionTab.EXPLORE -> {
                                val data = state.hashtagAllTimeByMode[state.hashtagMode]
                                when {
                                    state.isLoadingHashtagAllTime -> item("h_a_skel") { FeedSkeletonList(count = 4) }
                                    state.hashtagAllTimeError != null -> item("h_a_err") {
                                        ErrorStateView(
                                            message = state.hashtagAllTimeError?.userMessage ?: stringResource(R.string.error_unknown),
                                            isOffline = state.hashtagAllTimeError is AppError.NetworkOffline,
                                            onRetry = viewModel::retryCurrent,
                                        )
                                    }
                                    data?.ranking.isNullOrEmpty() -> item("h_a_empty") {
                                        EmptyStateView(stringResource(R.string.trending_empty_hashtag_alltime))
                                    }
                                    else -> {
                                        items(data!!.ranking, key = { "ha_${it.tag}" }) { item ->
                                            HashtagRankingRow(
                                                item = item,
                                                scoreValue = item.score ?: 0,
                                                scoreLabel = stringResource(R.string.trending_hashtag_score_label),
                                                onClick = { onOpenHashtagPosts(item.tag, state.hashtagMode) },
                                            )
                                        }
                                    }
                                }
                            }

                            HashtagSectionTab.SEARCH -> {
                                item("search_controls") {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = state.searchQuery,
                                            onValueChange = viewModel::onSearchQueryChanged,
                                            placeholder = { Text(stringResource(R.string.trending_search_placeholder)) },
                                            singleLine = true,
                                            shape = MaterialTheme.shapes.medium,
                                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                            trailingIcon = {
                                                if (state.searchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("trending_search_input"),
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.trending_search_scope_label),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.widthIn(min = 64.dp),
                                            )
                                            val scopeOptions = listOf(
                                                SearchType.POST to stringResource(R.string.trending_search_type_post),
                                                SearchType.HASHTAG to stringResource(R.string.trending_search_type_hashtag),
                                            )
                                            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                                                scopeOptions.forEachIndexed { index, (type, label) ->
                                                    SegmentedButton(
                                                        selected = state.searchType == type,
                                                        onClick = { viewModel.onSearchTypeChanged(type) },
                                                        shape = SegmentedButtonDefaults.itemShape(index, scopeOptions.size),
                                                        label = {
                                                            Text(
                                                                text = label,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.trending_search_sort_label),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.widthIn(min = 64.dp),
                                            )
                                            val sortOptions = listOf(
                                                SearchSort.NEWEST to stringResource(R.string.trending_search_sort_newest),
                                                SearchSort.TRENDING to stringResource(R.string.trending_search_sort_trending),
                                            )
                                            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                                                sortOptions.forEachIndexed { index, (sort, label) ->
                                                    SegmentedButton(
                                                        selected = state.searchSort == sort,
                                                        onClick = { viewModel.onSearchSortChanged(sort) },
                                                        shape = SegmentedButtonDefaults.itemShape(index, sortOptions.size),
                                                        label = {
                                                            Text(
                                                                text = label,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                when {
                                    state.searchQuery.isBlank() -> item("search_hint") {
                                        EmptyStateView(stringResource(R.string.trending_search_hint))
                                    }
                                    state.isSearching -> item("search_skel") { FeedSkeletonList(count = 3) }
                                    state.searchError != null -> item("search_err") {
                                        ErrorStateView(
                                            message = state.searchError?.userMessage ?: stringResource(R.string.error_unknown),
                                            isOffline = state.searchError is AppError.NetworkOffline,
                                            onRetry = viewModel::retryCurrent,
                                        )
                                    }
                                    state.searchType == SearchType.HASHTAG -> {
                                        val tags = state.searchResult?.hashtags.orEmpty()
                                        if (tags.isEmpty()) {
                                            item("search_empty_tags") {
                                                EmptyStateView(stringResource(R.string.trending_search_empty_hashtags))
                                            }
                                        } else {
                                            items(tags, key = { "st_${it.tag}" }) { item ->
                                                SearchHashtagRow(
                                                    item = item,
                                                    onClick = { onOpenHashtagPosts(item.tag, state.hashtagMode) },
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        val posts = state.searchResult?.posts.orEmpty()
                                        if (posts.isEmpty()) {
                                            item("search_empty_posts") {
                                                EmptyStateView(stringResource(R.string.trending_search_empty_posts))
                                            }
                                        } else {
                                            items(posts, key = { "sp_${it.type}_${it.id}" }) { card ->
                                                PostCard(
                                                    card = card,
                                                    onOpenDetail = onOpenDetail,
                                                    onOpenProfileByUid = onOpenProfileByUid,
                                                    onOpenHashtag = { tag -> onOpenHashtagPosts(tag, state.hashtagMode) },
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
                }
            }
        }
        }
    }
}

@Composable
private fun WeekCodeBadge(weekCode: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = stringResource(R.string.trending_week_chip, weekCode),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun RankingUserRow(
    user: RankingUser,
    scoreValue: Int,
    scoreLabel: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "#${user.rank}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (user.rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 28.dp),
            )
            UserAvatar(
                avatarUrl = user.avatarUrl,
                displayName = user.displayName,
                size = 42.dp,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = user.displayName?.takeIf { it.isNotBlank() }
                            ?: TagNameNormalizer.toDisplayTag(user.tagName)
                            ?: user.uid,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    UserBadgeRow(badgeIds = user.badges, compact = true)
                }
                UserTagPill(rawTagName = user.tagName)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = NumberFormatters.formatScore(scoreValue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = scoreLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun HashtagRankingRow(
    item: HashtagRankingItem,
    scoreValue: Int,
    scoreLabel: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "#${item.rank}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (item.rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 28.dp),
            )
            Icon(
                imageVector = Icons.Rounded.Tag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.tag,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.trending_usage_count, NumberFormatters.formatCount(item.usageCount)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = NumberFormatters.formatScore(scoreValue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = scoreLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun SearchHashtagRow(
    item: SearchHashtagItem,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Tag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.tag,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.trending_usage_count, NumberFormatters.formatCount(item.usageCount)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = NumberFormatters.formatScore(item.score),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
