package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.util.NumberFormatters
import com.example.core.util.TimeFormatter
import com.example.data.model.ContentCard
import com.example.data.model.ContentType
import com.example.data.model.ReactionType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostCard(
    card: ContentCard,
    onOpenDetail: (ContentType, String) -> Unit,
    onOpenProfileByUid: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onRequireLogin: () -> Unit,
    onReact: ((ContentCard, ReactionType) -> Unit)? = null,
    onShare: ((ContentCard) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showViewStats by remember { mutableStateOf(false) }
    var showScoreStats by remember { mutableStateOf(false) }
    val relTime = remember(card.createdAt) { TimeFormatter.formatRelativeTime(card.createdAt) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("post_card_${card.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AuthorHeaderLine(
                displayName = card.author?.displayName,
                tagName = card.author?.tagName,
                badges = card.author?.badges.orEmpty(),
                avatarUrl = card.author?.avatarUrl,
                subtitle = relTime,
                onAuthorClick = card.author?.uid?.takeIf { it.isNotBlank() }?.let { uid ->
                    { onOpenProfileByUid(uid) }
                },
                trailing = if (card.isPinned) {
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

            val coverUrl = card.coverImageUrl?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) }
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onOpenDetail(card.contentType, card.id) },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onOpenDetail(card.contentType, card.id) },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card.excerpt.isNotBlank()) {
                    Text(
                        text = card.excerpt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (card.hashtags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    card.hashtags.forEach { tag ->
                        AssistChip(
                            onClick = { onOpenHashtag(tag) },
                            label = {
                                Text(
                                    text = tag,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            shape = CircleShape,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

            PostMetricsActionBar(
                likeCount = card.likeCount,
                dislikeCount = card.dislikeCount,
                commentCount = card.commentCount,
                viewCount = card.viewCount,
                shareCount = card.shareCount,
                postScore = card.postScore,
                myReaction = card.reaction,
                onLike = { if (onReact != null) onReact(card, ReactionType.LIKE) else onRequireLogin() },
                onDislike = { if (onReact != null) onReact(card, ReactionType.DISLIKE) else onRequireLogin() },
                onComment = { onOpenDetail(card.contentType, card.id) },
                onShare = { if (onShare != null) onShare(card) else onRequireLogin() },
                onViewsClick = { showViewStats = true },
                onScoreClick = { showScoreStats = true },
            )
            TextButton(
                onClick = { onOpenDetail(card.contentType, card.id) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.action_view_post))
            }
        }
    }

    if (showViewStats) {
        ViewStatsDialog(
            viewCount = card.viewCount,
            aiViewCount = card.aiViewCount,
            onDismiss = { showViewStats = false },
        )
    }
    if (showScoreStats) {
        PostScoreDialog(
            viewCount = card.viewCount,
            likeCount = card.likeCount,
            dislikeCount = card.dislikeCount,
            commentCount = card.commentCount,
            shareCount = card.shareCount,
            postScore = card.postScore,
            onDismiss = { showScoreStats = false },
        )
    }
}

/**
 * Thanh chỉ số & tương tác một hàng duy nhất, không xuống dòng thừa.
 * Giữ vùng chạm tối thiểu 48dp theo tiêu chuẩn tiếp cận Android.
 */
@Composable
fun PostMetricsActionBar(
    likeCount: Int,
    dislikeCount: Int,
    commentCount: Int,
    viewCount: Int,
    shareCount: Int,
    postScore: Int,
    myReaction: ReactionType?,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onViewsClick: () -> Unit,
    onScoreClick: () -> Unit,
    likeTestTag: String? = null,
    dislikeTestTag: String? = null,
    shareTestTag: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            CompactMetricAction(
                icon = if (myReaction == ReactionType.LIKE) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                label = NumberFormatters.formatCount(likeCount),
                contentDesc = stringResource(R.string.metric_like),
                tint = if (myReaction == ReactionType.LIKE) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                selected = myReaction == ReactionType.LIKE,
                onClick = onLike,
                modifier = if (likeTestTag != null) Modifier.testTag(likeTestTag) else Modifier,
            )
            CompactMetricAction(
                icon = if (myReaction == ReactionType.DISLIKE) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                label = NumberFormatters.formatCount(dislikeCount),
                contentDesc = stringResource(R.string.metric_dislike),
                tint = if (myReaction == ReactionType.DISLIKE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                selected = myReaction == ReactionType.DISLIKE,
                onClick = onDislike,
                modifier = if (dislikeTestTag != null) Modifier.testTag(dislikeTestTag) else Modifier,
            )
            CompactMetricAction(
                icon = Icons.Outlined.ChatBubbleOutline,
                label = NumberFormatters.formatCount(commentCount),
                contentDesc = stringResource(R.string.metric_comment),
                onClick = onComment,
            )
            CompactMetricAction(
                icon = Icons.Outlined.Share,
                label = NumberFormatters.formatCount(shareCount),
                contentDesc = stringResource(R.string.metric_share),
                onClick = onShare,
                modifier = if (shareTestTag != null) Modifier.testTag(shareTestTag) else Modifier,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CompactMetricAction(
                icon = Icons.Rounded.Visibility,
                label = NumberFormatters.formatCount(viewCount),
                contentDesc = stringResource(R.string.metric_view),
                onClick = onViewsClick,
            )
            Surface(
                onClick = onScoreClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Grade,
                        contentDescription = stringResource(R.string.metric_score),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = NumberFormatters.formatScore(postScore),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactMetricAction(
    icon: ImageVector,
    label: String,
    contentDesc: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) {
            tint.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        },
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = tint,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
fun MetricPill(
    icon: ImageVector,
    label: String,
    contentDesc: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
fun ViewStatsDialog(
    viewCount: Int,
    aiViewCount: Int,
    onDismiss: () -> Unit,
) {
    val humanViews = (viewCount - aiViewCount).coerceAtLeast(0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.view_stats_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatDialogRow(
                    icon = Icons.Rounded.Visibility,
                    label = stringResource(R.string.view_stats_total),
                    value = NumberFormatters.formatCount(viewCount),
                    bold = true,
                )
                HorizontalDivider()
                StatDialogRow(
                    icon = Icons.Rounded.Person,
                    label = stringResource(R.string.view_stats_human),
                    value = NumberFormatters.formatCount(humanViews),
                )
                StatDialogRow(
                    icon = Icons.Rounded.SmartToy,
                    label = stringResource(R.string.view_stats_ai),
                    value = NumberFormatters.formatCount(aiViewCount),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
fun PostScoreDialog(
    viewCount: Int,
    likeCount: Int,
    dislikeCount: Int,
    commentCount: Int,
    shareCount: Int,
    postScore: Int,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.score_stats_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatDialogRow(
                    icon = Icons.Rounded.Visibility,
                    label = stringResource(R.string.score_weight_view),
                    value = NumberFormatters.formatCount(viewCount),
                )
                StatDialogRow(
                    icon = Icons.Rounded.ThumbUp,
                    label = stringResource(R.string.score_weight_like),
                    value = NumberFormatters.formatCount(likeCount),
                )
                StatDialogRow(
                    icon = Icons.Rounded.ThumbDown,
                    label = stringResource(R.string.score_weight_dislike),
                    value = NumberFormatters.formatCount(dislikeCount),
                )
                StatDialogRow(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    label = stringResource(R.string.score_weight_comment),
                    value = NumberFormatters.formatCount(commentCount),
                )
                StatDialogRow(
                    icon = Icons.Outlined.Share,
                    label = stringResource(R.string.score_weight_share),
                    value = NumberFormatters.formatCount(shareCount),
                )
                HorizontalDivider()
                StatDialogRow(
                    icon = null,
                    label = stringResource(R.string.score_stats_total),
                    value = NumberFormatters.formatScore(postScore),
                    bold = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun StatDialogRow(
    icon: ImageVector?,
    label: String,
    value: String,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}
