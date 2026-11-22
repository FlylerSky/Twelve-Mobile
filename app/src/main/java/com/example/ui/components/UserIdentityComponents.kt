package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.util.BadgeCatalog
import com.example.core.util.BadgeColorRole
import com.example.core.util.CoverCropMath
import com.example.core.util.TagNameNormalizer
import com.example.data.model.CoverCrop

@Composable
fun UserAvatar(
    avatarUrl: String?,
    displayName: String?,
    size: Dp = 40.dp,
    border: BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    modifier: Modifier = Modifier,
) {
    val cleanUrl = avatarUrl?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) }
    val initial = displayName?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
    var loadFailed by remember(cleanUrl) { mutableStateOf(false) }

    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = border,
    ) {
        if (cleanUrl != null && !loadFailed) {
            AsyncImage(
                model = cleanUrl,
                contentDescription = displayName ?: stringResource(R.string.nav_profile),
                contentScale = ContentScale.Crop,
                onError = { loadFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (initial != null) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
fun badgeTintColor(role: BadgeColorRole) = when (role) {
    BadgeColorRole.PRIMARY -> MaterialTheme.colorScheme.primary
    BadgeColorRole.ERROR -> MaterialTheme.colorScheme.error
    BadgeColorRole.SECONDARY -> MaterialTheme.colorScheme.secondary
    BadgeColorRole.TERTIARY -> MaterialTheme.colorScheme.primary
    BadgeColorRole.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun UserBadgeRow(
    badgeIds: List<String>,
    compact: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val distinctBadges = remember(badgeIds) { badgeIds.distinct() }
    if (distinctBadges.isEmpty()) return
    var showDialog by remember { mutableStateOf(false) }
    val badgesDesc = stringResource(R.string.profile_badges_title)
    val iconSize = if (compact) 16.dp else 18.dp

    Row(
        modifier = modifier
            .clip(CircleShape)
            .clickable(role = Role.Button) { showDialog = true }
            .semantics { contentDescription = badgesDesc }
            .padding(horizontal = if (compact) 2.dp else 6.dp, vertical = if (compact) 2.dp else 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        distinctBadges.forEach { id ->
            val meta = BadgeCatalog.getBadgeInfo(id)
            Icon(
                imageVector = meta.icon,
                contentDescription = meta.label,
                tint = badgeTintColor(meta.colorRole),
                modifier = Modifier.size(iconSize),
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.profile_badges_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    distinctBadges.forEach { id ->
                        val meta = BadgeCatalog.getBadgeInfo(id)
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = meta.icon,
                                contentDescription = null,
                                tint = badgeTintColor(meta.colorRole),
                                modifier = Modifier.size(22.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = meta.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = meta.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
fun UserTagPill(
    rawTagName: String?,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val display = TagNameNormalizer.toDisplayTag(rawTagName) ?: return
    Surface(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Text(
            text = display,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun AuthorHeaderLine(
    displayName: String?,
    tagName: String?,
    badges: List<String>,
    avatarUrl: String?,
    subtitle: String? = null,
    onAuthorClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val fallbackAnon = stringResource(R.string.label_anonymous_author)
    val safeName = displayName?.takeIf { it.isNotBlank() } ?: TagNameNormalizer.toDisplayTag(tagName) ?: fallbackAnon
    val clickableMod = if (onAuthorClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onAuthorClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableMod),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UserAvatar(
            avatarUrl = avatarUrl,
            displayName = safeName,
            size = 40.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = safeName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                UserBadgeRow(badgeIds = badges, compact = true)
                UserTagPill(rawTagName = tagName)
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Hiển thị ảnh bìa hồ sơ tỉ lệ 3:1 áp dụng đúng tham số `coverCrop` (%) giống `ProfileCover.jsx`.
 * Dùng `.layout` với `Constraints.fixed` để ảnh phóng to/dời vị trí không bị khung cha 3:1 kẹp lại.
 */
@Composable
fun ProfileCoverBox(
    coverUrl: String?,
    coverCrop: CoverCrop?,
    modifier: Modifier = Modifier,
    showEmptyPlaceholder: Boolean = false,
    onLoadError: (() -> Unit)? = null,
) {
    val cleanUrl = coverUrl?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) }
    var failed by remember(cleanUrl) { mutableStateOf(false) }
    var isLoading by remember(cleanUrl) { mutableStateOf(cleanUrl != null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CoverCropMath.COVER_ASPECT)
            .clip(RoundedCornerShape(12.dp))
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        if (cleanUrl != null && !failed) {
            val placement = CoverCropMath.computePixelPlacement(
                containerWidthPx = constraints.maxWidth,
                containerHeightPx = constraints.maxHeight,
                crop = coverCrop,
            )
            if (placement != null) {
                AsyncImage(
                    model = cleanUrl,
                    contentDescription = stringResource(R.string.account_cover_section),
                    contentScale = ContentScale.FillBounds,
                    onLoading = { isLoading = true },
                    onSuccess = { isLoading = false },
                    onError = {
                        isLoading = false
                        failed = true
                        onLoadError?.invoke()
                    },
                    modifier = Modifier.layout { measurable, _ ->
                        val placeable = measurable.measure(
                            Constraints.fixed(placement.widthPx, placement.heightPx),
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.placeRelative(placement.offsetXPx, placement.offsetYPx)
                        }
                    },
                )
            } else {
                AsyncImage(
                    model = cleanUrl,
                    contentDescription = stringResource(R.string.account_cover_section),
                    contentScale = ContentScale.Crop,
                    onLoading = { isLoading = true },
                    onSuccess = { isLoading = false },
                    onError = {
                        isLoading = false
                        failed = true
                        onLoadError?.invoke()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (isLoading) {
                val transition = rememberInfiniteTransition(label = "cover_loading")
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 0.55f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "cover_loading_alpha",
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)),
                )
            }
        } else if (showEmptyPlaceholder) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = stringResource(R.string.account_cover_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
