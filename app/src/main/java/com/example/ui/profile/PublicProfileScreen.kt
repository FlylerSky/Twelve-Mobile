package com.example.ui.profile

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.core.network.AppError
import com.example.core.util.CoverCropMath
import com.example.core.util.NumberFormatters
import com.example.core.util.ProfileExtrasCatalog
import com.example.core.util.ShareSheetHelper
import com.example.core.util.TagNameNormalizer
import com.example.core.util.TimeFormatter
import com.example.data.model.AchievementsData
import com.example.data.model.ContentType
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.FeedSkeletonList
import com.example.ui.components.PostCard
import com.example.ui.components.ProfileCoverBox
import com.example.ui.components.UserAvatar
import com.example.ui.components.UserBadgeRow
import com.example.ui.components.UserTagPill

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PublicProfileScreen(
    lookupKey: ProfileLookupKey?,
    viewerUid: String?,
    showTopBack: Boolean,
    onBack: () -> Unit,
    onOpenDetail: (ContentType, String) -> Unit,
    onOpenProfileByUid: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onRequireLogin: () -> Unit,
    onOpenAccountInfo: (() -> Unit)? = null,
    onOpenVisitors: (() -> Unit)? = null,
) {
    if (lookupKey == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_sign_in_prompt),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = onRequireLogin,
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.auth_sign_in_button))
                }
            }
        }
        return
    }

    val vmKey = when (lookupKey) {
        is ProfileLookupKey.ByUid -> "uid:${lookupKey.uid}"
        is ProfileLookupKey.ByTag -> "tag:${TagNameNormalizer.toBareTag(lookupKey.tag)}"
    }
    val viewModel: PublicProfileViewModel = hiltViewModel(key = vmKey)

    LaunchedEffect(vmKey, viewerUid) {
        viewModel.load(lookupKey, viewerUid)
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val shareChooserTitle = stringResource(R.string.share_chooser_title)
    val isGuest = viewerUid.isNullOrBlank()

    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is ProfileSideEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is ProfileSideEffect.OpenShareSheet -> {
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

    val content: @Composable (PaddingValues) -> Unit = { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                state.isLoadingProfile -> {
                    FeedSkeletonList(
                        count = 2,
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .padding(16.dp),
                    )
                }
                state.profileError != null || state.profile == null -> {
                    ErrorStateView(
                        message = state.profileError?.userMessage ?: stringResource(R.string.error_unknown),
                        isOffline = state.profileError is AppError.NetworkOffline,
                        onRetry = viewModel::retryProfile,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                }
                else -> {
                    val profile = state.profile!!
                    val tabs = remember(profile.isSA) {
                        if (profile.isSA) {
                            listOf(ProfileTab.HOME, ProfileTab.POSTS, ProfileTab.BLOG)
                        } else {
                            listOf(ProfileTab.HOME, ProfileTab.POSTS)
                        }
                    }
                    val selectedIndex = tabs.indexOf(state.selectedTab).coerceAtLeast(0)

                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize()
                            .testTag("public_profile_screen"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "profile_header_card") {
                            val hasCover = !profile.coverUrl.isNullOrBlank() &&
                                profile.coverUrl.trim().startsWith("https://", ignoreCase = true)
                            val displayNameText = profile.displayName?.takeIf { it.isNotBlank() }
                                ?: TagNameNormalizer.toDisplayTag(profile.tagName)
                                ?: profile.uid
                            val countryCode = profile.country?.trim()?.takeIf { it.isNotEmpty() }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // Khối ảnh bìa 3:1 + ảnh đại diện tròn chồng lên cạnh dưới giống Twelve Web
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 42.dp),
                                    ) {
                                        if (hasCover) {
                                            ProfileCoverBox(
                                                coverUrl = profile.coverUrl,
                                                coverCrop = profile.coverCrop,
                                                modifier = Modifier.clip(MaterialTheme.shapes.medium),
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(CoverCropMath.COVER_ASPECT)
                                                    .clip(MaterialTheme.shapes.medium)
                                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)),
                                            )
                                        }
                                    }
                                    UserAvatar(
                                        avatarUrl = profile.avatarUrl,
                                        displayName = profile.displayName,
                                        size = 84.dp,
                                        border = BorderStroke(3.5.dp, MaterialTheme.colorScheme.background),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(start = 16.dp),
                                    )
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = displayNameText,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        UserBadgeRow(
                                            badgeIds = profile.badges,
                                            compact = false,
                                        )
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        UserTagPill(
                                            rawTagName = profile.tagName,
                                            modifier = Modifier.align(Alignment.CenterVertically),
                                        )
                                        if (countryCode != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.align(Alignment.CenterVertically),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Public,
                                                    contentDescription = stringResource(R.string.account_country_label),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Text(
                                                    text = countryCode.uppercase(),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }

                                    // Tiểu sử lưu văn bản thuần (nhiều dòng, tối đa 6 dòng), KHÔNG bao giờ parse HTML.
                                    if (profile.bio.isNotBlank()) {
                                        Text(
                                            text = profile.bio,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = viewModel::openExtraInfoDialog,
                                        shape = CircleShape,
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .heightIn(min = 48.dp)
                                            .testTag("profile_more_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Text(
                                            text = stringResource(R.string.profile_more_button),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "profile_tabs") {
                            PrimaryTabRow(selectedTabIndex = selectedIndex) {
                                tabs.forEach { tab ->
                                    val (tabIcon, labelRes) = when (tab) {
                                        ProfileTab.HOME -> Icons.Outlined.Home to R.string.profile_tab_home
                                        ProfileTab.POSTS -> Icons.AutoMirrored.Outlined.Article to R.string.profile_tab_posts
                                        ProfileTab.BLOG -> Icons.Outlined.Newspaper to R.string.profile_tab_blog
                                    }
                                    val label = stringResource(labelRes)
                                    Tab(
                                        selected = state.selectedTab == tab,
                                        onClick = { viewModel.selectTab(tab) },
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Icon(
                                                    imageVector = tabIcon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Text(
                                                    text = label,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        when (state.selectedTab) {
                            ProfileTab.HOME -> {
                                when {
                                    state.isLoadingPinned -> item("pinned_loading") { FeedSkeletonList(count = 2) }
                                    state.pinnedError != null -> item("pinned_error") {
                                        ErrorStateView(
                                            message = state.pinnedError?.userMessage ?: stringResource(R.string.error_unknown),
                                            isOffline = state.pinnedError is AppError.NetworkOffline,
                                            onRetry = { viewModel.loadPinned(profile.uid) },
                                        )
                                    }
                                    state.pinnedPosts.isEmpty() -> item("pinned_empty") {
                                        EmptyStateView(message = stringResource(R.string.profile_pinned_empty))
                                    }
                                    else -> {
                                        item("pinned_section_header") {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(top = 4.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PushPin,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Text(
                                                    text = stringResource(R.string.profile_pinned_section_title),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }
                                        items(state.pinnedPosts, key = { "pin_${it.id}" }) { card ->
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

                            ProfileTab.POSTS -> {
                                when {
                                    state.isLoadingUserPosts -> item("posts_loading") { FeedSkeletonList(count = 2) }
                                    state.userPostsError != null && state.userPosts.isEmpty() -> item("posts_error") {
                                        ErrorStateView(
                                            message = state.userPostsError?.userMessage ?: stringResource(R.string.error_unknown),
                                            isOffline = state.userPostsError is AppError.NetworkOffline,
                                            onRetry = { viewModel.loadUserPostsInitial(profile.uid) },
                                        )
                                    }
                                    state.userPosts.isEmpty() -> item("posts_empty") {
                                        EmptyStateView(message = stringResource(R.string.profile_posts_empty))
                                    }
                                    else -> {
                                        items(state.userPosts, key = { "up_${it.id}" }) { card ->
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
                                        if (state.userPostsNextCursor != null || state.isLoadingMorePosts || state.userPostsError != null) {
                                            item("posts_footer") {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    when {
                                                        state.isLoadingMorePosts -> CircularProgressIndicator()
                                                        state.userPostsError != null -> ErrorStateView(
                                                            message = state.userPostsError?.userMessage ?: stringResource(R.string.error_unknown),
                                                            isOffline = state.userPostsError is AppError.NetworkOffline,
                                                            onRetry = viewModel::loadMoreUserPosts,
                                                        )
                                                        else -> OutlinedButton(
                                                            onClick = viewModel::loadMoreUserPosts,
                                                            shape = CircleShape,
                                                            modifier = Modifier.heightIn(min = 48.dp),
                                                        ) {
                                                            Text(stringResource(R.string.action_load_more))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            ProfileTab.BLOG -> {
                                when {
                                    state.isLoadingUserBlogs -> item("blogs_loading") { FeedSkeletonList(count = 2) }
                                    state.userBlogsError != null && state.userBlogs.isEmpty() -> item("blogs_error") {
                                        ErrorStateView(
                                            message = state.userBlogsError?.userMessage ?: stringResource(R.string.error_unknown),
                                            isOffline = state.userBlogsError is AppError.NetworkOffline,
                                            onRetry = { viewModel.loadUserBlogsInitial(profile.uid) },
                                        )
                                    }
                                    state.userBlogs.isEmpty() -> item("blogs_empty") {
                                        EmptyStateView(message = stringResource(R.string.profile_blogs_empty))
                                    }
                                    else -> {
                                        items(state.userBlogs, key = { "ub_${it.id}" }) { card ->
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
                                        if (state.userBlogsNextCursor != null || state.isLoadingMoreBlogs || state.userBlogsError != null) {
                                            item("blogs_footer") {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    when {
                                                        state.isLoadingMoreBlogs -> CircularProgressIndicator()
                                                        state.userBlogsError != null -> ErrorStateView(
                                                            message = state.userBlogsError?.userMessage ?: stringResource(R.string.error_unknown),
                                                            isOffline = state.userBlogsError is AppError.NetworkOffline,
                                                            onRetry = viewModel::loadMoreUserBlogs,
                                                        )
                                                        else -> OutlinedButton(
                                                            onClick = viewModel::loadMoreUserBlogs,
                                                            shape = CircleShape,
                                                            modifier = Modifier.heightIn(min = 48.dp),
                                                        ) {
                                                            Text(stringResource(R.string.action_load_more))
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
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (showTopBack) {
                @Suppress("DEPRECATION")
                val topClipboard = LocalClipboardManager.current
                val linkCopiedMsg = stringResource(R.string.profile_link_copied)
                val loadedProfile = state.profile
                TopAppBar(
                    title = {
                        Text(
                            text = loadedProfile?.displayName?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.profile_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                    actions = {
                        if (loadedProfile != null) {
                            if (state.isOwnProfile) {
                                onOpenAccountInfo?.let { openEdit ->
                                    IconButton(
                                        onClick = openEdit,
                                        modifier = Modifier.testTag("profile_edit_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = stringResource(R.string.profile_edit_button),
                                        )
                                    }
                                }
                                onOpenVisitors?.let { openVisitors ->
                                    IconButton(
                                        onClick = openVisitors,
                                        modifier = Modifier.testTag("profile_visitors_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Visibility,
                                            contentDescription = stringResource(R.string.profile_visitors_button),
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    val url = BuildConfig.WEB_BASE_URL +
                                        TagNameNormalizer.buildWebProfilePath(
                                            loadedProfile.tagName,
                                            loadedProfile.uid,
                                        )
                                    topClipboard.setText(AnnotatedString(url))
                                    Toast.makeText(context, linkCopiedMsg, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("profile_copy_link_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = stringResource(R.string.profile_copy_link_desc),
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        content(innerPadding)
    }

    if (state.extraDialog.isOpen) {
        ProfileMoreDialog(
            dialogState = state.extraDialog,
            isOwn = state.isOwnProfile,
            onRetry = viewModel::openExtraInfoDialog,
            onDismiss = viewModel::closeExtraInfoDialog,
        )
    }
}

@Composable
private fun ProfileMoreDialog(
    dialogState: ExtraInfoDialogState,
    isOwn: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val uidCopiedMsg = stringResource(R.string.profile_uid_copied)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .heightIn(max = 640.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.profile_more_button),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        dialogState.isLoading -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                        dialogState.error != null -> ErrorStateView(
                            message = dialogState.error.userMessage,
                            isOffline = dialogState.error is AppError.NetworkOffline,
                            onRetry = onRetry,
                        )
                        dialogState.data != null -> {
                            val rows = remember(dialogState.data) {
                                ProfileExtrasCatalog.buildExtraRows(dialogState.data.fields)
                            }
                            val achievements = dialogState.data.achievements

                            if (rows.isNotEmpty()) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column {
                                        rows.forEachIndexed { idx, row ->
                                            if (idx > 0) HorizontalDivider()
                                            val isUid = row.key == "uid"
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = row.label,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(0.45f),
                                                )
                                                if (isUid) {
                                                    Row(
                                                        modifier = Modifier.weight(0.55f),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        Text(
                                                            text = row.value,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                clipboard.setText(AnnotatedString(row.value))
                                                                Toast.makeText(
                                                                    context,
                                                                    uidCopiedMsg,
                                                                    Toast.LENGTH_SHORT,
                                                                ).show()
                                                            },
                                                            modifier = Modifier.size(48.dp),
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.ContentCopy,
                                                                contentDescription = stringResource(R.string.profile_copy_uid_desc),
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(18.dp),
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    Text(
                                                        text = row.value,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.weight(0.55f),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (achievements != null) {
                                AchievementsSection(achievements)
                            }

                            if (rows.isEmpty() && achievements == null) {
                                Text(
                                    text = stringResource(
                                        if (isOwn) R.string.profile_more_empty_own else R.string.profile_more_empty_other,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (isOwn) {
                                Text(
                                    text = stringResource(R.string.profile_more_own_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementsSection(data: AchievementsData) {
    val weekly = data.weekly
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.profile_achievements_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AchievementStatBox(
                    label = stringResource(R.string.profile_stat_score),
                    value = NumberFormatters.formatIntegerVi(data.accountScore),
                    modifier = Modifier.weight(1f),
                )
                AchievementStatBox(
                    label = stringResource(R.string.profile_stat_top10),
                    value = if (weekly.weeksRecorded > 0) "${weekly.top10Count}/${weekly.weeksRecorded}" else "${weekly.top10Count}",
                    modifier = Modifier.weight(1f),
                )
            }

            if (weekly.top10Count > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AchievementStatBox(
                        label = stringResource(R.string.profile_stat_top3),
                        value = "${weekly.top3Count}",
                        modifier = Modifier.weight(1f),
                    )
                    AchievementStatBox(
                        label = stringResource(R.string.profile_stat_first),
                        value = "${weekly.firstPlaceCount}",
                        modifier = Modifier.weight(1f),
                    )
                }
                weekly.best?.let { best ->
                    Text(
                        text = stringResource(
                            R.string.profile_best_week,
                            best.rank,
                            TimeFormatter.formatWeekLabel(best.weekCode),
                            formatGrowth(best.weeklyGrowth),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (weekly.recent.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_recent_weeks),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    weekly.recent.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = TimeFormatter.formatWeekLabel(entry.weekCode),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "Hạng ${entry.rank} · ${formatGrowth(entry.weeklyGrowth)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.profile_no_weekly),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AchievementStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun formatGrowth(n: Int): String =
    "${if (n > 0) "+" else ""}${NumberFormatters.formatIntegerVi(n)} điểm"
