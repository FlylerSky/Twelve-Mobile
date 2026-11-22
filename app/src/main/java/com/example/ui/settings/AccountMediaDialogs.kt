package com.example.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.core.network.AppError
import com.example.core.util.AccountValidators
import com.example.core.util.CoverCropMath
import com.example.core.util.ImageUploadProcessor
import com.example.core.util.TagNameNormalizer
import com.example.data.model.CoverCrop
import kotlin.math.min
import kotlinx.coroutines.launch

@Composable
fun AvatarCropDialog(
    sourceBitmap: Bitmap,
    onConfirmJpeg: suspend (ByteArray) -> Result<String>,
    onDismiss: () -> Unit,
) {
    var panX by remember(sourceBitmap) { mutableFloatStateOf(0f) }
    var panY by remember(sourceBitmap) { mutableFloatStateOf(0f) }
    var zoom by remember(sourceBitmap) { mutableFloatStateOf(1f) }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val imageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.account_avatar_crop_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clipToBounds()
                            .pointerInput(sourceBitmap) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    panX = (panX + dragAmount.x / (w * 0.4f)).coerceIn(-1f, 1f)
                                    panY = (panY + dragAmount.y / (h * 0.4f)).coerceIn(-1f, 1f)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = stringResource(R.string.account_avatar_section),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    val shiftLimitX = size.width * 0.35f * zoom
                                    val shiftLimitY = size.height * 0.35f * zoom
                                    translationX = panX * shiftLimitX
                                    translationY = panY * shiftLimitY
                                },
                        )
                        val borderColor = MaterialTheme.colorScheme.primary
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val r = min(size.width, size.height) / 2f - 4.dp.toPx()
                            drawCircle(
                                color = borderColor,
                                radius = r,
                                center = Offset(size.width / 2f, size.height / 2f),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.account_cover_zoom_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        valueRange = 1f..3f,
                        enabled = !saving,
                        modifier = Modifier.testTag("avatar_zoom_slider"),
                    )

                    errorMsg?.let { msg ->
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !saving,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            if (saving) return@Button
                            saving = true
                            errorMsg = null
                            scope.launch {
                                val processed = ImageUploadProcessor.cropAvatarToJpeg(sourceBitmap, panX, panY, zoom)
                                processed.onFailure { err ->
                                    saving = false
                                    errorMsg = err.message
                                    return@launch
                                }
                                val bytes = processed.getOrThrow()
                                onConfirmJpeg(bytes)
                                    .onSuccess {
                                        saving = false
                                        onDismiss()
                                    }
                                    .onFailure { err ->
                                        saving = false
                                        errorMsg = (err as? AppError)?.userMessage ?: err.message
                                    }
                            }
                        },
                        enabled = !saving,
                        shape = CircleShape,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("avatar_save_button"),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.account_avatar_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoverAdjustDialog(
    localBitmap: Bitmap?,
    existingUrl: String?,
    initialCrop: CoverCrop?,
    imageAspect: Float,
    title: String,
    saveLabel: String,
    onSaveCrop: suspend (CoverCrop) -> Result<Any>,
    onDismiss: () -> Unit,
) {
    val initialControls = remember(localBitmap, existingUrl, initialCrop, imageAspect) {
        ImageUploadProcessor.estimateControlsFromCrop(initialCrop, imageAspect)
    }
    var panX by remember(initialControls) { mutableFloatStateOf(initialControls.first) }
    var panY by remember(initialControls) { mutableFloatStateOf(initialControls.second) }
    var zoom by remember(initialControls) { mutableFloatStateOf(initialControls.third) }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var urlLoadFailed by remember(existingUrl) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val currentCrop = remember(panX, panY, zoom, imageAspect) {
        ImageUploadProcessor.computeCoverCropFromViewport(
            imageAspect = imageAspect,
            panX = panX,
            panY = panY,
            zoom = zoom,
        )
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.account_cover_adjust_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (urlLoadFailed) {
                        Text(
                            text = stringResource(R.string.account_cover_load_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(CoverCropMath.COVER_ASPECT)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .clipToBounds()
                                .pointerInput(localBitmap, existingUrl) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        panX = (panX + dragAmount.x / (w * 0.45f)).coerceIn(-1f, 1f)
                                        panY = (panY + dragAmount.y / (h * 0.45f)).coerceIn(-1f, 1f)
                                    }
                                },
                        ) {
                            val placement = CoverCropMath.computePixelPlacement(
                                containerWidthPx = constraints.maxWidth,
                                containerHeightPx = constraints.maxHeight,
                                crop = currentCrop,
                            )

                            if (placement != null) {
                                val layoutMod = Modifier.layout { measurable, _ ->
                                    val placeable = measurable.measure(
                                        Constraints.fixed(placement.widthPx, placement.heightPx),
                                    )
                                    layout(constraints.maxWidth, constraints.maxHeight) {
                                        placeable.placeRelative(placement.offsetXPx, placement.offsetYPx)
                                    }
                                }
                                if (localBitmap != null) {
                                    val bmp = remember(localBitmap) { localBitmap.asImageBitmap() }
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = stringResource(R.string.account_cover_section),
                                        contentScale = ContentScale.FillBounds,
                                        modifier = layoutMod,
                                    )
                                } else {
                                    val cleanExisting = existingUrl?.trim()
                                    if (!cleanExisting.isNullOrBlank()) {
                                        AsyncImage(
                                            model = cleanExisting,
                                            contentDescription = stringResource(R.string.account_cover_section),
                                            contentScale = ContentScale.FillBounds,
                                            onError = { urlLoadFailed = true },
                                            modifier = layoutMod,
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.account_cover_zoom_label),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Slider(
                            value = zoom,
                            onValueChange = { zoom = it },
                            valueRange = 1f..3f,
                            enabled = !saving,
                            modifier = Modifier.testTag("cover_zoom_slider"),
                        )
                    }

                    errorMsg?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            panX = 0f
                            panY = 0f
                            zoom = 1f
                        },
                        enabled = !saving && !urlLoadFailed,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.account_cover_reset))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onDismiss,
                            enabled = !saving,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Button(
                            onClick = {
                                if (saving || urlLoadFailed) return@Button
                                saving = true
                                errorMsg = null
                                scope.launch {
                                    onSaveCrop(currentCrop)
                                        .onSuccess {
                                            saving = false
                                            onDismiss()
                                        }
                                        .onFailure { err ->
                                            saving = false
                                            errorMsg = (err as? AppError)?.userMessage ?: err.message
                                        }
                                }
                            },
                            enabled = !saving && !urlLoadFailed,
                            shape = CircleShape,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("cover_save_button"),
                        ) {
                            if (saving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(saveLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoverUrlDialog(
    onSubmitUrl: suspend (String) -> Result<com.example.data.model.CoverResponse>,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.account_cover_url_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        errorMsg = null
                    },
                    label = { Text(stringResource(R.string.account_cover_url_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cover_url_input"),
                )
                Text(
                    text = stringResource(R.string.account_cover_url_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                errorMsg?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = url.trim()
                    if (!AccountValidators.isValidCoverUrlInput(trimmed)) {
                        errorMsg = "Vui lòng nhập đường dẫn https:// hợp lệ."
                        return@Button
                    }
                    busy = true
                    errorMsg = null
                    scope.launch {
                        onSubmitUrl(trimmed)
                            .onSuccess {
                                busy = false
                                onDismiss()
                            }
                            .onFailure { err ->
                                busy = false
                                errorMsg = (err as? AppError)?.userMessage ?: err.message
                            }
                    }
                },
                enabled = !busy && url.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("cover_url_submit_button"),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.account_cover_save_image))
                }
            }
        },
    )
}

@Composable
fun TagNameDialog(
    currentTag: String?,
    input: String,
    status: TagCheckStatus,
    onInputChanged: (String) -> Unit,
    onConfirmSave: suspend () -> Result<Unit>,
    onDismiss: () -> Unit,
) {
    var confirmStep by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val oldBare = TagNameNormalizer.toBareTag(currentTag)
    val newBare = TagNameNormalizer.toBareTag(input)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (confirmStep) R.string.account_tag_confirm_title else R.string.account_tag_dialog_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!confirmStep) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            errorMsg = null
                            onInputChanged(it)
                        },
                        label = { Text(stringResource(R.string.account_tag_new_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tag_name_input"),
                    )
                    val statusText = when (status) {
                        TagCheckStatus.Idle -> null
                        TagCheckStatus.Invalid -> stringResource(R.string.account_tag_invalid)
                        TagCheckStatus.SameAsCurrent -> stringResource(R.string.account_tag_same)
                        TagCheckStatus.Checking -> stringResource(R.string.account_tag_checking)
                        TagCheckStatus.Available -> stringResource(R.string.account_tag_available)
                        TagCheckStatus.Taken -> stringResource(R.string.account_tag_taken)
                        TagCheckStatus.Reserved -> stringResource(R.string.account_tag_reserved)
                        is TagCheckStatus.Error -> status.message
                    }
                    val statusColor = when (status) {
                        TagCheckStatus.Available -> MaterialTheme.colorScheme.primary
                        TagCheckStatus.Checking, TagCheckStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.error
                    }
                    statusText?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = statusColor)
                    }
                    errorMsg?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = stringResource(R.string.account_tag_confirm_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    if (oldBare.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.account_tag_old_link,
                                "${BuildConfig.WEB_BASE_URL}/u/@$oldBare",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.account_tag_new_link,
                            "${BuildConfig.WEB_BASE_URL}/u/@$newBare",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (confirmStep) {
                        confirmStep = false
                    } else {
                        onDismiss()
                    }
                },
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(if (confirmStep) R.string.action_back else R.string.action_cancel))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!confirmStep) {
                        confirmStep = true
                        return@Button
                    }
                    busy = true
                    errorMsg = null
                    scope.launch {
                        onConfirmSave()
                            .onSuccess {
                                busy = false
                                onDismiss()
                            }
                            .onFailure { err ->
                                busy = false
                                errorMsg = (err as? AppError)?.userMessage ?: err.message
                                confirmStep = false
                            }
                    }
                },
                enabled = !busy && status == TagCheckStatus.Available,
                shape = CircleShape,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("tag_name_confirm_button"),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(
                            if (confirmStep) R.string.account_tag_confirm_button else R.string.account_tag_next,
                        ),
                    )
                }
            }
        },
    )
}

@Composable
fun PasswordConfirmDialog(
    title: String,
    description: String,
    confirmText: String,
    isDestructive: Boolean = false,
    extraValid: Boolean = true,
    extraContent: (@Composable () -> Unit)? = null,
    onConfirmWithPassword: suspend (String) -> Result<Any>,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                extraContent?.invoke()
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMsg = null
                    },
                    label = { Text(stringResource(R.string.auth_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reauth_password_input"),
                )
                errorMsg?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (busy || password.isEmpty() || !extraValid) return@Button
                    busy = true
                    errorMsg = null
                    scope.launch {
                        onConfirmWithPassword(password)
                            .onSuccess {
                                busy = false
                                onDismiss()
                            }
                            .onFailure { err ->
                                busy = false
                                errorMsg = (err as? AppError)?.userMessage ?: err.message
                            }
                    }
                },
                enabled = !busy && password.isNotEmpty() && extraValid,
                shape = CircleShape,
                colors = if (isDestructive) {
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.buttonColors()
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("reauth_confirm_button"),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(confirmText)
                }
            }
        },
    )
}
