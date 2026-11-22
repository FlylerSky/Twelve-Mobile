package com.example.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.core.util.TagNameNormalizer
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.core.util.TwelveDeepLink
import com.example.data.model.ContentType
import com.example.data.model.UserProfile
import com.example.data.repository.ScoreMode
import com.example.data.repository.ServerWakeState
import com.example.ui.composer.PostComposerScreen
import com.example.ui.detail.ContentDetailScreen
import com.example.ui.feed.FeedScreen
import com.example.ui.profile.ProfileLookupKey
import com.example.ui.profile.PublicProfileScreen
import com.example.ui.settings.AccountInfoScreen
import com.example.ui.settings.SecurityScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.VisitorsScreen
import com.example.ui.theme.ThemeMode
import com.example.ui.trending.HashtagPostsScreen
import com.example.ui.trending.TrendingScreen

private data class TopLevelTab(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwelveAppShell(
    profile: UserProfile?,
    serverWakeState: ServerWakeState,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    onCheckHealth: () -> Unit,
    onSignOut: () -> Unit,
    onOpenLogin: () -> Unit,
    pendingDeepLink: TwelveDeepLink? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    LaunchedEffect(pendingDeepLink, profile?.uid) {
        val link = pendingDeepLink ?: return@LaunchedEffect
        when (link) {
            is TwelveDeepLink.Content -> {
                navController.navigate(
                    ContentDetailRoute(
                        contentType = link.contentType.name,
                        contentId = link.id,
                        sharerUid = link.sharerUid,
                        shareSignature = link.shareSignature,
                        sharedFlag = link.sharedFlag,
                    ),
                ) {
                    launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
            is TwelveDeepLink.PublicProfile -> {
                navController.navigate(
                    PublicProfileRoute(
                        uidOrTag = link.uidOrTag,
                        isTag = link.isTag,
                    ),
                ) {
                    launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
            TwelveDeepLink.OwnProfile -> {
                if (profile != null) {
                    navController.navigate(MyProfileTabRoute) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                    onDeepLinkConsumed()
                } else {
                    onDeepLinkConsumed()
                    onOpenLogin()
                }
            }
        }
    }

    val topLevelTabs = listOf(
        TopLevelTab(
            route = FeedTabRoute,
            labelRes = R.string.nav_feed,
            selectedIcon = Icons.Rounded.Home,
            unselectedIcon = Icons.Outlined.Home,
            testTag = "nav_tab_feed",
        ),
        TopLevelTab(
            route = BlogTabRoute,
            labelRes = R.string.nav_blog,
            selectedIcon = Icons.AutoMirrored.Rounded.Article,
            unselectedIcon = Icons.AutoMirrored.Outlined.Article,
            testTag = "nav_tab_blog",
        ),
        TopLevelTab(
            route = TrendingTabRoute,
            labelRes = R.string.nav_trending,
            selectedIcon = Icons.Rounded.LocalFireDepartment,
            unselectedIcon = Icons.Outlined.LocalFireDepartment,
            testTag = "nav_tab_trending",
        ),
        TopLevelTab(
            route = MyProfileTabRoute,
            labelRes = R.string.nav_profile,
            selectedIcon = Icons.Rounded.Person,
            unselectedIcon = Icons.Outlined.Person,
            testTag = "nav_tab_profile",
        ),
        TopLevelTab(
            route = SettingsTabRoute,
            labelRes = R.string.nav_settings,
            selectedIcon = Icons.Rounded.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            testTag = "nav_tab_settings",
        ),
    )

    val activeTopLevelTab = topLevelTabs.firstOrNull { tab ->
        when (tab.route) {
            is FeedTabRoute -> currentDestination?.hasRoute<FeedTabRoute>() == true
            is BlogTabRoute -> currentDestination?.hasRoute<BlogTabRoute>() == true
            is TrendingTabRoute -> currentDestination?.hasRoute<TrendingTabRoute>() == true
            is MyProfileTabRoute -> currentDestination?.hasRoute<MyProfileTabRoute>() == true
            is SettingsTabRoute -> currentDestination?.hasRoute<SettingsTabRoute>() == true
            else -> false
        }
    }
    val showTopLevelChrome = activeTopLevelTab != null || currentDestination == null

    val selectTopLevelTab: (TopLevelTab) -> Unit = { tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openDetail: (ContentType, String) -> Unit = { type, id ->
        navController.navigate(
            ContentDetailRoute(
                contentType = type.name,
                contentId = id,
            ),
        )
    }

    val openProfileByUid: (String) -> Unit = { uid ->
        navController.navigate(
            PublicProfileRoute(
                uidOrTag = uid,
                isTag = false,
            ),
        )
    }

    val openHashtag: (String, ScoreMode) -> Unit = { tag, mode ->
        navController.navigate(
            HashtagPostsRoute(
                tag = tag,
                mode = mode.name,
            ),
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag("twelve_app_shell"),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (showTopLevelChrome) {
                    val context = LocalContext.current
                    @Suppress("DEPRECATION")
                    val clipboard = LocalClipboardManager.current
                    val linkCopiedMsg = stringResource(R.string.profile_link_copied)
                    val isOwnProfileTab = activeTopLevelTab?.route is MyProfileTabRoute
                    Column {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            actions = {
                                if (isOwnProfileTab && profile != null) {
                                    IconButton(
                                        onClick = { navController.navigate(AccountInfoRoute) },
                                        modifier = Modifier.testTag("profile_edit_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = stringResource(R.string.profile_edit_button),
                                        )
                                    }
                                    IconButton(
                                        onClick = { navController.navigate(VisitorsRoute) },
                                        modifier = Modifier.testTag("profile_visitors_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Visibility,
                                            contentDescription = stringResource(R.string.profile_visitors_button),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val url = BuildConfig.WEB_BASE_URL +
                                                TagNameNormalizer.buildWebProfilePath(profile.tagName, profile.uid)
                                            clipboard.setText(AnnotatedString(url))
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
                                if (profile != null) {
                                    IconButton(
                                        onClick = onSignOut,
                                        modifier = Modifier.testTag("topbar_sign_out_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                                            contentDescription = stringResource(R.string.action_sign_out),
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = onOpenLogin,
                                        modifier = Modifier.testTag("topbar_sign_in_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Login,
                                            contentDescription = stringResource(R.string.auth_sign_in_button),
                                        )
                                    }
                                }
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            },
            bottomBar = {
                if (showTopLevelChrome && !useRail) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        NavigationBar(tonalElevation = 0.dp) {
                            topLevelTabs.forEach { tab ->
                                val selected = activeTopLevelTab == tab
                                val label = stringResource(tab.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectTopLevelTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = label,
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    modifier = Modifier.testTag(tab.testTag),
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (showTopLevelChrome && useRail) {
                    NavigationRail {
                        topLevelTabs.forEach { tab ->
                            val selected = activeTopLevelTab == tab
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTopLevelTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = FeedTabRoute,
                    ) {
                        composable<FeedTabRoute> {
                            FeedScreen(
                                contentType = ContentType.POST,
                                currentUser = profile,
                                onOpenDetail = openDetail,
                                onOpenProfileByUid = openProfileByUid,
                                onOpenHashtag = { tag -> openHashtag(tag, ScoreMode.CLASSIC) },
                                onOpenComposer = { type ->
                                    navController.navigate(ComposerRoute(contentType = type.name))
                                },
                                onRequireLogin = onOpenLogin,
                            )
                        }

                        composable<BlogTabRoute> {
                            FeedScreen(
                                contentType = ContentType.BLOG,
                                currentUser = profile,
                                onOpenDetail = openDetail,
                                onOpenProfileByUid = openProfileByUid,
                                onOpenHashtag = { tag -> openHashtag(tag, ScoreMode.CLASSIC) },
                                onOpenComposer = { type ->
                                    navController.navigate(ComposerRoute(contentType = type.name))
                                },
                                onRequireLogin = onOpenLogin,
                            )
                        }

                        composable<TrendingTabRoute> {
                            TrendingScreen(
                                isGuest = profile == null,
                                onOpenProfileByUid = openProfileByUid,
                                onOpenHashtagPosts = openHashtag,
                                onOpenDetail = openDetail,
                                onRequireLogin = onOpenLogin,
                            )
                        }

                        composable<MyProfileTabRoute> {
                            PublicProfileScreen(
                                lookupKey = profile?.uid?.takeIf { it.isNotBlank() }?.let { ProfileLookupKey.ByUid(it) },
                                viewerUid = profile?.uid,
                                showTopBack = false,
                                onBack = { navController.popBackStack() },
                                onOpenDetail = openDetail,
                                onOpenProfileByUid = openProfileByUid,
                                onOpenHashtag = { tag -> openHashtag(tag, ScoreMode.CLASSIC) },
                                onRequireLogin = onOpenLogin,
                                onOpenAccountInfo = if (profile != null) {
                                    { navController.navigate(AccountInfoRoute) }
                                } else {
                                    null
                                },
                                onOpenVisitors = if (profile != null) {
                                    { navController.navigate(VisitorsRoute) }
                                } else {
                                    null
                                },
                            )
                        }

                        composable<SettingsTabRoute> {
                            SettingsScreen(
                                profile = profile,
                                serverWakeState = serverWakeState,
                                themeMode = themeMode,
                                onSetThemeMode = onSetThemeMode,
                                onCheckHealth = onCheckHealth,
                                onSignOut = onSignOut,
                                onOpenLogin = onOpenLogin,
                                onOpenAccountInfo = if (profile != null) {
                                    { navController.navigate(AccountInfoRoute) }
                                } else {
                                    null
                                },
                                onOpenVisitors = if (profile != null) {
                                    { navController.navigate(VisitorsRoute) }
                                } else {
                                    null
                                },
                                onOpenSecurity = if (profile != null) {
                                    { navController.navigate(SecurityRoute) }
                                } else {
                                    null
                                },
                            )
                        }

                        composable<ContentDetailRoute> { entry ->
                            val route = entry.toRoute<ContentDetailRoute>()
                            val type = if (route.contentType == ContentType.BLOG.name) ContentType.BLOG else ContentType.POST
                            ContentDetailScreen(
                                contentType = type,
                                contentId = route.contentId,
                                sharerUid = route.sharerUid,
                                shareSignature = route.shareSignature,
                                sharedFlag = route.sharedFlag,
                                currentUser = profile,
                                onBack = { navController.popBackStack() },
                                onEditContent = { editType, editId ->
                                    navController.navigate(
                                        ComposerRoute(
                                            contentType = editType.name,
                                            editContentId = editId,
                                        ),
                                    )
                                },
                                onOpenProfileByUid = openProfileByUid,
                                onOpenHashtag = { tag -> openHashtag(tag, ScoreMode.CLASSIC) },
                                onRequireLogin = onOpenLogin,
                            )
                        }

                        composable<PublicProfileRoute> { entry ->
                            val route = entry.toRoute<PublicProfileRoute>()
                            val lookupKey = if (route.isTag) {
                                ProfileLookupKey.ByTag(route.uidOrTag)
                            } else {
                                ProfileLookupKey.ByUid(route.uidOrTag)
                            }
                            PublicProfileScreen(
                                lookupKey = lookupKey,
                                viewerUid = profile?.uid,
                                showTopBack = true,
                                onBack = { navController.popBackStack() },
                                onOpenDetail = openDetail,
                                onOpenProfileByUid = openProfileByUid,
                                onOpenHashtag = { tag -> openHashtag(tag, ScoreMode.CLASSIC) },
                                onRequireLogin = onOpenLogin,
                                onOpenAccountInfo = if (profile != null) {
                                    { navController.navigate(AccountInfoRoute) }
                                } else {
                                    null
                                },
                                onOpenVisitors = if (profile != null) {
                                    { navController.navigate(VisitorsRoute) }
                                } else {
                                    null
                                },
                            )
                        }

                        composable<HashtagPostsRoute> { entry ->
                            val route = entry.toRoute<HashtagPostsRoute>()
                            val mode = if (route.mode == ScoreMode.ADVANCED.name) ScoreMode.ADVANCED else ScoreMode.CLASSIC
                            HashtagPostsScreen(
                                tag = route.tag,
                                initialMode = mode,
                                isGuest = profile == null,
                                onBack = { navController.popBackStack() },
                                onOpenDetail = openDetail,
                                onOpenProfileByUid = openProfileByUid,
                                onOpenHashtag = { tag -> openHashtag(tag, mode) },
                                onRequireLogin = onOpenLogin,
                            )
                        }

                        composable<ComposerRoute> { entry ->
                            val route = entry.toRoute<ComposerRoute>()
                            val type = if (route.contentType == ContentType.BLOG.name) ContentType.BLOG else ContentType.POST
                            PostComposerScreen(
                                contentType = type,
                                editContentId = route.editContentId,
                                onBack = { navController.popBackStack() },
                                onCreated = { createdType, newId ->
                                    navController.navigate(
                                        ContentDetailRoute(
                                            contentType = createdType.name,
                                            contentId = newId,
                                        ),
                                    ) {
                                        popUpTo<ComposerRoute> { inclusive = true }
                                    }
                                },
                                onUpdated = { _, _ ->
                                    navController.popBackStack()
                                },
                            )
                        }

                        composable<AccountInfoRoute> {
                            AccountInfoScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable<VisitorsRoute> {
                            VisitorsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenProfileByUid = openProfileByUid,
                            )
                        }

                        composable<SecurityRoute> {
                            SecurityScreen(
                                isSA = profile?.isSA == true,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
