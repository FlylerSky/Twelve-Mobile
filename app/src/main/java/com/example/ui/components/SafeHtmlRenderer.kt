package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.util.CustomTabHelper
import com.example.core.util.SafeHtmlParser

/**
 * Kết xuất `contentHtml` hoàn toàn bằng Jetpack Compose native (không WebView, không JS).
 * - Văn bản thường (tên, bio, bình luận) TUYỆT ĐỐI không đi qua composable này.
 * - Mọi liên kết ngoài / iframe / video / audio chỉ mở URL `http(s)://` qua Chrome Custom Tab.
 */
@Composable
fun SafeHtmlRenderer(
    contentHtml: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val blocks = remember(contentHtml) { SafeHtmlParser.parse(contentHtml) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is SafeHtmlParser.HtmlBlock.Paragraph -> {
                    val indentDp = (block.indent * 16).dp
                    val annotated = buildInlineAnnotatedString(
                        spans = block.spans,
                        linkColor = MaterialTheme.colorScheme.primary,
                        codeBg = MaterialTheme.colorScheme.surfaceVariant,
                        onOpenLink = { url -> openExternalLink(context, url) },
                    )
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textAlign = mapAlign(block.align),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indentDp),
                    )
                }

                is SafeHtmlParser.HtmlBlock.Heading -> {
                    val baseStyle = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    val annotated = buildInlineAnnotatedString(
                        spans = block.spans,
                        linkColor = MaterialTheme.colorScheme.primary,
                        codeBg = MaterialTheme.colorScheme.surfaceVariant,
                        onOpenLink = { url -> openExternalLink(context, url) },
                    )
                    Text(
                        text = annotated,
                        style = baseStyle.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = mapAlign(block.align),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is SafeHtmlParser.HtmlBlock.Quote -> {
                    val annotated = buildInlineAnnotatedString(
                        spans = block.spans,
                        linkColor = MaterialTheme.colorScheme.primary,
                        codeBg = MaterialTheme.colorScheme.surfaceVariant,
                        onOpenLink = { url -> openExternalLink(context, url) },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                MaterialTheme.shapes.small,
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is SafeHtmlParser.HtmlBlock.CodeBlock -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = block.code,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.5.sp,
                                lineHeight = 21.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                        )
                    }
                }

                is SafeHtmlParser.HtmlBlock.BulletListItem -> {
                    val indentDp = (block.indent * 16).dp
                    val annotated = buildInlineAnnotatedString(
                        spans = block.spans,
                        linkColor = MaterialTheme.colorScheme.primary,
                        codeBg = MaterialTheme.colorScheme.surfaceVariant,
                        onOpenLink = { url -> openExternalLink(context, url) },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indentDp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = block.marker,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is SafeHtmlParser.HtmlBlock.Image -> {
                    AsyncImage(
                        model = block.url,
                        contentDescription = block.alt.ifBlank { null },
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small),
                    )
                }

                is SafeHtmlParser.HtmlBlock.EmbedCard -> {
                    EmbedLinkCard(
                        url = block.url,
                        kind = block.kind,
                        onOpen = { openExternalLink(context, block.url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmbedLinkCard(
    url: String,
    kind: SafeHtmlParser.EmbedKind,
    onOpen: () -> Unit,
) {
    val icon = when (kind) {
        SafeHtmlParser.EmbedKind.VIDEO -> Icons.Rounded.PlayCircleOutline
        SafeHtmlParser.EmbedKind.AUDIO -> Icons.Rounded.AudioFile
        SafeHtmlParser.EmbedKind.IFRAME -> Icons.Rounded.Public
    }
    OutlinedCard(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.detail_embed_card_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.detail_embed_open_short),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.detail_embed_open_link),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.width(15.dp),
                    )
                }
            }
        }
    }
}

private fun openExternalLink(context: Context, url: String) {
    val opened = CustomTabHelper.openUrl(context, url)
    if (!opened) {
        Toast.makeText(context, context.getString(R.string.detail_invalid_link), Toast.LENGTH_SHORT).show()
    }
}

private fun mapAlign(align: SafeHtmlParser.TextAlign): TextAlign = when (align) {
    SafeHtmlParser.TextAlign.START -> TextAlign.Start
    SafeHtmlParser.TextAlign.CENTER -> TextAlign.Center
    SafeHtmlParser.TextAlign.RIGHT -> TextAlign.End
    SafeHtmlParser.TextAlign.JUSTIFY -> TextAlign.Justify
}

@Composable
private fun buildInlineAnnotatedString(
    spans: List<SafeHtmlParser.InlineSpan>,
    linkColor: androidx.compose.ui.graphics.Color,
    codeBg: androidx.compose.ui.graphics.Color,
    onOpenLink: (String) -> Unit,
): AnnotatedString {
    return remember(spans, linkColor, codeBg) {
        buildAnnotatedString {
            fun appendSpans(items: List<SafeHtmlParser.InlineSpan>) {
                items.forEach { item ->
                    when (item) {
                        is SafeHtmlParser.InlineSpan.Text -> append(item.text)
                        SafeHtmlParser.InlineSpan.LineBreak -> append('\n')
                        is SafeHtmlParser.InlineSpan.Styled -> {
                            val spanStyle = when (item.style) {
                                SafeHtmlParser.InlineStyleType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                                SafeHtmlParser.InlineStyleType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                                SafeHtmlParser.InlineStyleType.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                                SafeHtmlParser.InlineStyleType.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                                SafeHtmlParser.InlineStyleType.CODE -> SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = codeBg,
                                )
                                SafeHtmlParser.InlineStyleType.SMALL -> SpanStyle(fontSize = 13.sp)
                                SafeHtmlParser.InlineStyleType.LARGE -> SpanStyle(fontSize = 19.sp)
                                SafeHtmlParser.InlineStyleType.HUGE -> SpanStyle(fontSize = 23.sp)
                                SafeHtmlParser.InlineStyleType.SERIF -> SpanStyle(fontFamily = FontFamily.Serif)
                                SafeHtmlParser.InlineStyleType.MONOSPACE -> SpanStyle(fontFamily = FontFamily.Monospace)
                            }
                            pushStyle(spanStyle)
                            appendSpans(item.children)
                            pop()
                        }
                        is SafeHtmlParser.InlineSpan.Link -> {
                            val link = LinkAnnotation.Url(
                                url = item.url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ),
                                linkInteractionListener = { onOpenLink(item.url) },
                            )
                            pushLink(link)
                            appendSpans(item.children)
                            pop()
                        }
                    }
                }
            }
            appendSpans(spans)
        }
    }
}
