package com.example.ui.settings

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.util.BioValidator
import com.example.core.util.ImageUploadProcessor
import com.example.core.util.ProfileExtrasCatalog
import com.example.core.util.TagNameNormalizer
import com.example.data.model.CoverCrop
import com.example.ui.components.ProfileCoverBox
import com.example.ui.components.UserAvatar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REAL_NAME_REVEAL_MS = 60_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountInfoScreen(
    onBack: () -> Unit,
    viewModel: AccountInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is AccountInfoSideEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    // Tự ẩn tên thật sau 60 giây đúng với RealNameRow.jsx#REVEAL_MS
    LaunchedEffect(state.revealedRealName) {
        if (state.revealedRealName != null) {
            delay(REAL_NAME_REVEAL_MS)
            viewModel.hideRealName()
        }
    }

    var avatarCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var coverUploadPending by remember { mutableStateOf<Pair<Bitmap, ImageUploadProcessor.ProcessedImage>?>(null) }
    var adjustingExistingCover by remember { mutableStateOf(false) }
    var showCoverUrlDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showRevealRealNameDialog by remember { mutableStateOf(false) }
    var showEditRealNameDialog by remember { mutableStateOf(false) }
    var newRealNameInput by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var isPreparingImage by remember { mutableStateOf(false) }
    var coverPreviewFailed by remember(state.profile?.coverUrl) { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isPreparingImage = true
        scope.launch {
            ImageUploadProcessor.decodeForPreview(context, uri)
                .onSuccess { bmp -> avatarCropBitmap = bmp }
                .onFailure { err ->
                    snackbarHostState.showSnackbar(err.message ?: context.getString(R.string.error_unknown))
                }
            isPreparingImage = false
        }
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isPreparingImage = true
        scope.launch {
            val decoded = ImageUploadProcessor.decodeForPreview(context, uri)
            decoded.onFailure { err ->
                isPreparingImage = false
                snackbarHostState.showSnackbar(err.message ?: context.getString(R.string.error_unknown))
                return@launch
            }
            val bmp = decoded.getOrThrow()
            val prepared = ImageUploadProcessor.prepareCoverForUpload(bmp)
            isPreparingImage = false
            prepared
                .onSuccess { img -> coverUploadPending = bmp to img }
                .onFailure { err ->
                    bmp.recycle()
                    snackbarHostState.showSnackbar(err.message ?: context.getString(R.string.error_unknown))
                }
        }
    }

    val avatarUpdatedMsg = stringResource(R.string.account_avatar_updated)
    val coverUpdatedMsg = stringResource(R.string.account_cover_updated)
    val coverCropSavedMsg = stringResource(R.string.account_cover_crop_saved)
    val coverRemovedMsg = stringResource(R.string.account_cover_removed)
    val tagUpdatedMsg = stringResource(R.string.account_tag_updated)
    val realNameEmptyVal = stringResource(R.string.account_real_name_empty_value)
    val realNameUpdatedMsg = stringResource(R.string.account_real_name_updated)
    val savedProfileMsg = stringResource(R.string.account_saved_ok)
    val extrasSaveErrorMsg = stringResource(R.string.account_extras_save_error)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("account_info_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_info_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("account_info_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val profile = state.profile
        if (profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 1. Ảnh đại diện
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            UserAvatar(
                                avatarUrl = profile.avatarUrl,
                                displayName = profile.displayName,
                                size = 72.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.account_avatar_section),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                OutlinedButton(
                                    onClick = {
                                        avatarPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    },
                                    enabled = !isPreparingImage && !state.isUploadingAvatar,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("pick_avatar_button"),
                                ) {
                                    Text(
                                        stringResource(
                                            if (isPreparingImage || state.isUploadingAvatar) {
                                                R.string.account_image_processing
                                            } else {
                                                R.string.account_avatar_button
                                            },
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    // 2. Ảnh bìa
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
                                text = stringResource(R.string.account_cover_section),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            ProfileCoverBox(
                                coverUrl = profile.coverUrl,
                                coverCrop = profile.coverCrop,
                                showEmptyPlaceholder = true,
                                onLoadError = { coverPreviewFailed = true },
                            )
                            if (!profile.coverUrl.isNullOrBlank() && coverPreviewFailed) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Text(
                                        text = stringResource(R.string.account_cover_load_error),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        coverPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    },
                                    enabled = !isPreparingImage && !state.isUpdatingCover,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("cover_upload_button"),
                                ) {
                                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.account_cover_upload))
                                }
                                OutlinedButton(
                                    onClick = { showCoverUrlDialog = true },
                                    enabled = !state.isUpdatingCover,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("cover_url_button"),
                                ) {
                                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.account_cover_url))
                                }
                                if (!profile.coverUrl.isNullOrBlank() && !coverPreviewFailed) {
                                    OutlinedButton(
                                        onClick = { adjustingExistingCover = true },
                                        enabled = !state.isUpdatingCover,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("cover_adjust_button"),
                                    ) {
                                        Icon(Icons.Outlined.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(stringResource(R.string.account_cover_adjust))
                                    }
                                }
                                if (!profile.coverUrl.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = { viewModel.removeCover(coverRemovedMsg) },
                                        enabled = !state.isUpdatingCover,
                                        shape = CircleShape,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("cover_remove_button"),
                                    ) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(stringResource(R.string.account_cover_remove))
                                    }
                                }
                            }
                        }
                    }

                    // 3. Tên thật (reauth) & Tag Name Search
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
                                text = stringResource(R.string.account_real_name_label),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = state.revealedRealName ?: "••••••••••",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.testTag("real_name_value"),
                            )
                            Text(
                                text = stringResource(R.string.account_real_name_helper),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (state.revealedRealName == null) {
                                    OutlinedButton(
                                        onClick = { showRevealRealNameDialog = true },
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("reveal_real_name_button"),
                                    ) {
                                        Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(stringResource(R.string.account_real_name_reveal))
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = viewModel::hideRealName,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("hide_real_name_button"),
                                    ) {
                                        Icon(Icons.Outlined.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(stringResource(R.string.account_real_name_hide))
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        newRealNameInput = ""
                                        showEditRealNameDialog = true
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("edit_real_name_button"),
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.account_real_name_edit))
                                }
                            }

                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.account_tag_label),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = TagNameNormalizer.toDisplayTag(profile.tagName)
                                            ?: stringResource(R.string.settings_account_tag_unset),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("account_tag_value"),
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.resetTagInput()
                                        showTagDialog = true
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("change_tag_button"),
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.account_real_name_edit))
                                }
                            }
                        }
                    }

                    // 4. Thông tin hồ sơ (displayName, birthday, gender, country, bio)
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
                                text = stringResource(R.string.account_profile_section),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )

                            OutlinedTextField(
                                value = state.form.displayName,
                                onValueChange = viewModel::onDisplayNameChanged,
                                label = { Text(stringResource(R.string.account_display_name_label)) },
                                isError = state.displayNameError != null,
                                supportingText = state.displayNameError?.let { msg -> { Text(msg) } },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_display_name_input"),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                OutlinedTextField(
                                    value = state.form.birthday,
                                    onValueChange = viewModel::onBirthdayChanged,
                                    label = { Text(stringResource(R.string.account_birthday_label)) },
                                    isError = state.birthdayError != null,
                                    supportingText = state.birthdayError?.let { msg -> { Text(msg) } },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_birthday_input"),
                                )
                                OutlinedButton(
                                    onClick = { showDatePicker = true },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .heightIn(min = 48.dp)
                                        .testTag("profile_birthday_pick_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarToday,
                                        contentDescription = stringResource(R.string.account_birthday_pick),
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.account_gender_label),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val genderLabels = listOf(
                                    "male" to R.string.account_gender_male,
                                    "female" to R.string.account_gender_female,
                                    "other" to R.string.account_gender_other,
                                )
                                genderLabels.forEach { (code, labelRes) ->
                                    FilterChip(
                                        selected = state.form.gender == code,
                                        onClick = { viewModel.onGenderChanged(code) },
                                        label = { Text(stringResource(labelRes)) },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("profile_gender_$code"),
                                    )
                                }
                            }

                            CountrySelectorField(
                                selectedCode = state.form.country,
                                countries = state.countries,
                                onCountrySelected = viewModel::onCountryChanged,
                            )

                            val stats = state.bioStats
                            OutlinedTextField(
                                value = state.form.bio,
                                onValueChange = viewModel::onBioChanged,
                                label = { Text(stringResource(R.string.account_bio_label)) },
                                placeholder = { Text(stringResource(R.string.account_bio_placeholder)) },
                                minLines = 3,
                                maxLines = 6,
                                isError = !stats.valid,
                                supportingText = {
                                    Text(
                                        text = stringResource(
                                            R.string.account_bio_counter,
                                            stats.chars,
                                            BioValidator.BIO_MAX,
                                            stats.lines,
                                            BioValidator.BIO_MAX_LINES,
                                        ),
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_bio_input"),
                            )

                            state.profileSaveError?.let { err ->
                                Text(
                                    text = err,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            Button(
                                onClick = { viewModel.saveProfileChanges(savedProfileMsg) },
                                enabled = state.canSaveProfile,
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("save_profile_button"),
                            ) {
                                if (state.isSavingProfile) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.account_save_changes))
                                }
                            }
                        }
                    }

                    // 5. Thông tin mở rộng (9 công tắc áp dụng ngay)
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.account_extras_section),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.account_extras_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            ProfileExtrasCatalog.EXTRA_FIELDS.forEachIndexed { idx, meta ->
                                if (idx > 0) HorizontalDivider()
                                val checked = state.extras[meta.key] ?: meta.defaultOn
                                val busy = meta.key in state.busyExtraKeys
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = meta.label,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            if (meta.isPrivate) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.account_extras_private_tag),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = meta.hint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = checked,
                                        enabled = !busy,
                                        onCheckedChange = { next ->
                                            viewModel.toggleExtra(meta.key, next, extrasSaveErrorMsg)
                                        },
                                        modifier = Modifier.testTag("extra_switch_${meta.key}"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    avatarCropBitmap?.let { bmp ->
        AvatarCropDialog(
            sourceBitmap = bmp,
            onConfirmJpeg = { bytes -> viewModel.uploadAvatarBytes(bytes, avatarUpdatedMsg) },
            onDismiss = {
                bmp.recycle()
                avatarCropBitmap = null
            },
        )
    }

    coverUploadPending?.let { (bmp, processed) ->
        CoverAdjustDialog(
            localBitmap = bmp,
            existingUrl = null,
            initialCrop = null,
            imageAspect = processed.width.toFloat() / processed.height.toFloat().coerceAtLeast(1f),
            title = stringResource(R.string.account_cover_new_title),
            saveLabel = stringResource(R.string.account_cover_save_image),
            onSaveCrop = { crop -> viewModel.uploadCoverWithCrop(processed.bytes, crop, coverUpdatedMsg) },
            onDismiss = {
                bmp.recycle()
                coverUploadPending = null
            },
        )
    }

    if (adjustingExistingCover && !state.profile?.coverUrl.isNullOrBlank()) {
        CoverAdjustDialog(
            localBitmap = null,
            existingUrl = state.profile?.coverUrl,
            initialCrop = state.profile?.coverCrop,
            imageAspect = 2.5f,
            title = stringResource(R.string.account_cover_adjust_title),
            saveLabel = stringResource(R.string.account_cover_save_position),
            onSaveCrop = { crop -> viewModel.setCoverCrop(crop, coverCropSavedMsg) },
            onDismiss = { adjustingExistingCover = false },
        )
    }

    if (showCoverUrlDialog) {
        CoverUrlDialog(
            onSubmitUrl = { url ->
                val res = viewModel.setCoverUrl(url, coverUpdatedMsg)
                if (res.isSuccess) {
                    adjustingExistingCover = true
                }
                res
            },
            onDismiss = { showCoverUrlDialog = false },
        )
    }

    if (showTagDialog) {
        TagNameDialog(
            currentTag = state.profile?.tagName,
            input = state.tagInput,
            status = state.tagCheckStatus,
            onInputChanged = viewModel::onTagInputChanged,
            onConfirmSave = { viewModel.confirmUpdateTagName(tagUpdatedMsg) },
            onDismiss = { showTagDialog = false },
        )
    }

    if (showRevealRealNameDialog) {
        PasswordConfirmDialog(
            title = stringResource(R.string.account_real_name_reveal_title),
            description = stringResource(R.string.account_real_name_reveal_desc),
            confirmText = stringResource(R.string.account_real_name_reveal),
            onConfirmWithPassword = { pwd -> viewModel.revealRealNameWithPassword(pwd, realNameEmptyVal) },
            onDismiss = { showRevealRealNameDialog = false },
        )
    }

    if (showEditRealNameDialog) {
        PasswordConfirmDialog(
            title = stringResource(R.string.account_real_name_edit_title),
            description = stringResource(R.string.account_real_name_edit_desc),
            confirmText = stringResource(R.string.action_save),
            extraValid = newRealNameInput.trim().isNotEmpty(),
            extraContent = {
                OutlinedTextField(
                    value = newRealNameInput,
                    onValueChange = { newRealNameInput = it },
                    label = { Text(stringResource(R.string.account_real_name_new_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_real_name_input"),
                )
            },
            onConfirmWithPassword = { pwd ->
                viewModel.updateRealNameWithPassword(newRealNameInput, pwd, realNameUpdatedMsg)
            },
            onDismiss = { showEditRealNameDialog = false },
        )
    }

    if (showDatePicker) {
        val initialMillis = remember(state.form.birthday) {
            runCatching {
                LocalDate.parse(state.form.birthday).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            viewModel.onBirthdayChanged(picked.toString())
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySelectorField(
    selectedCode: String,
    countries: List<com.example.data.model.CountryItem>,
    onCountrySelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = remember(selectedCode, countries) {
        countries.firstOrNull { it.code.equals(selectedCode, ignoreCase = true) }?.name
            ?: selectedCode
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (countries.isNotEmpty()) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.account_country_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("profile_country_field"),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            countries.forEach { item ->
                DropdownMenuItem(
                    text = { Text("${item.name} (${item.code})") },
                    onClick = {
                        onCountrySelected(item.code)
                        expanded = false
                    },
                )
            }
        }
    }
}
