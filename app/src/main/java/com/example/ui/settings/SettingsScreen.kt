package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.util.NumberFormatters
import com.example.core.util.TagNameNormalizer
import com.example.data.model.UserProfile
import com.example.data.repository.ServerWakeState
import com.example.ui.components.UserAvatar
import com.example.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    profile: UserProfile?,
    serverWakeState: ServerWakeState,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    onCheckHealth: () -> Unit,
    onSignOut: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenAccountInfo: (() -> Unit)? = null,
    onOpenVisitors: (() -> Unit)? = null,
    onOpenSecurity: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_account_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (profile != null) {
                        val displayName = profile.displayName?.takeIf { it.isNotBlank() }
                            ?: TagNameNormalizer.toDisplayTag(profile.tagName)
                            ?: profile.uid
                        val displayTag = TagNameNormalizer.toDisplayTag(profile.tagName)
                            ?: stringResource(R.string.settings_account_tag_unset)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            UserAvatar(
                                avatarUrl = profile.avatarUrl,
                                displayName = displayName,
                                size = 56.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = displayTag,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.settings_account_status, profile.status.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.settings_account_group, profile.accountGroup.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_account_score,
                                NumberFormatters.formatIntegerVi(profile.accountScore),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = onSignOut,
                            shape = CircleShape,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .heightIn(min = 48.dp)
                                .testTag("settings_sign_out_button"),
                        ) {
                            Text(stringResource(R.string.action_sign_out))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.guest_banner_notice),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = onOpenLogin,
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("settings_sign_in_button"),
                        ) {
                            Text(stringResource(R.string.auth_sign_in_button))
                        }
                    }
                }
            }

            if (profile != null && (onOpenAccountInfo != null || onOpenVisitors != null || onOpenSecurity != null)) {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_manage_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        var hasPrevious = false
                        onOpenAccountInfo?.let { openInfo ->
                            SettingsNavigationRow(
                                icon = Icons.Outlined.ManageAccounts,
                                title = stringResource(R.string.settings_account_info_title),
                                subtitle = stringResource(R.string.settings_account_info_subtitle),
                                testTag = "settings_account_info_item",
                                onClick = openInfo,
                            )
                            hasPrevious = true
                        }
                        onOpenVisitors?.let { openVisitors ->
                            if (hasPrevious) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsNavigationRow(
                                icon = Icons.Outlined.Visibility,
                                title = stringResource(R.string.settings_visitors_title),
                                subtitle = stringResource(R.string.settings_visitors_subtitle),
                                testTag = "settings_visitors_item",
                                onClick = openVisitors,
                            )
                            hasPrevious = true
                        }
                        onOpenSecurity?.let { openSecurity ->
                            if (hasPrevious) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsNavigationRow(
                                icon = Icons.Outlined.Lock,
                                title = stringResource(R.string.settings_security_title),
                                subtitle = stringResource(R.string.settings_security_subtitle),
                                testTag = "settings_security_item",
                                onClick = openSecurity,
                            )
                        }
                    }
                }
            }

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
                    Text(
                        text = stringResource(R.string.settings_appearance_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    val themeOptions = listOf(
                        Triple(ThemeMode.SYSTEM, R.string.theme_system, Icons.Rounded.SettingsBrightness to "theme_chip_system"),
                        Triple(ThemeMode.LIGHT, R.string.theme_light, Icons.Rounded.LightMode to "theme_chip_light"),
                        Triple(ThemeMode.DARK, R.string.theme_dark, Icons.Rounded.DarkMode to "theme_chip_dark"),
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themeOptions.forEachIndexed { index, (mode, labelRes, iconAndTag) ->
                            val (icon, tag) = iconAndTag
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, themeOptions.size),
                                icon = {
                                    SegmentedButtonDefaults.Icon(active = themeMode == mode) {
                                        Icon(imageVector = icon, contentDescription = null)
                                    }
                                },
                                label = {
                                    Text(
                                        text = stringResource(labelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.testTag(tag),
                            )
                        }
                    }
                }
            }

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
                    Text(
                        text = stringResource(R.string.settings_connection_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    when (serverWakeState) {
                        ServerWakeState.Idle -> Unit
                        ServerWakeState.Waking -> Text(
                            text = stringResource(R.string.status_waking_server),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        is ServerWakeState.Ready -> Text(
                            text = stringResource(R.string.m0_health_ok, serverWakeState.version ?: "-"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        is ServerWakeState.Unreachable -> Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = serverWakeState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onCheckHealth,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("check_health_button"),
                    ) {
                        Text(stringResource(R.string.m0_check_health_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
