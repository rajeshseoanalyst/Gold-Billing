package com.digiglobal.goldbill.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ---------------- screen frame ----------------

@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = fab,
        containerColor = MaterialTheme.colorScheme.background,
        content = content
    )
}

@Composable
fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
fun Loading() = Centered { CircularProgressIndicator() }

// ---------------- small building blocks ----------------

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    trailing()
                }
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun KpiTile(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(text: String, icon: ImageVector = Icons.Filled.Inbox) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Decodes a stored base64 picture once and keeps it while the value doesn't change. */
@Composable
fun rememberPicture(b64: String): androidx.compose.ui.graphics.ImageBitmap? =
    remember(b64) { com.digiglobal.goldbill.util.Images.decode(b64)?.asImageBitmap() }

@Composable
fun Avatar(name: String, size: Int = 40, photo: String = "") {
    val pic = rememberPicture(photo)
    if (pic != null) {
        Image(pic, contentDescription = name, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape))
        return
    }
    val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

/** Company logo in a rounded white tile; falls back to the company's initials. */
@Composable
fun CompanyLogo(name: String, logo: String, size: Int = 40) {
    val pic = rememberPicture(logo)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)).background(
            if (pic != null) Color.White else MaterialTheme.colorScheme.primary
        ),
        contentAlignment = Alignment.Center
    ) {
        if (pic != null) {
            Image(pic, contentDescription = name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding((size / 12).dp))
        } else {
            val initials = name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
            Text(initials, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold,
                style = if (size >= 56) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Tap-to-pick picture with a small camera badge. Uses Android's photo picker, so no extra permission.
 * [onPicked] receives the shrunk base64 picture (or null if the picture couldn't be read).
 */
@Composable
fun PicturePicker(isLogo: Boolean, onPicked: (String?) -> Unit, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(if (isLogo) com.digiglobal.goldbill.util.Images.logo(ctx, uri) else com.digiglobal.goldbill.util.Images.photo(ctx, uri))
    }
    Box(Modifier.clickable {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }) {
        content()
        Box(
            Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PhotoCamera, "Change picture", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(13.dp)) }
    }
}

/** A read-only text field that opens a menu of options. */
@Composable
fun DropdownField(label: String, value: String, options: List<String>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onSelect(o) }) }
        }
    }
}


/** Which size a picked picture is saved at. */
enum class PicKind { PRODUCT, SIGNATURE, DOCUMENT }

/**
 * "Camera" and "Gallery" buttons. The camera opens the phone's camera app (no camera permission needed);
 * the result is shrunk and handed back as base64, or null if it couldn't be read.
 */
@Composable
fun PhotoButtons(kind: PicKind, onPicked: (String?) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val read = { uri: android.net.Uri ->
        when (kind) {
            PicKind.PRODUCT -> com.digiglobal.goldbill.util.Images.product(ctx, uri)
            PicKind.SIGNATURE -> com.digiglobal.goldbill.util.Images.signature(ctx, uri)
            PicKind.DOCUMENT -> com.digiglobal.goldbill.util.Images.document(ctx, uri)
        }
    }
    var shotUri by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = shotUri
        if (ok && u != null) onPicked(read(u))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(read(uri))
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            runCatching {
                val dir = java.io.File(ctx.cacheDir, "camera").apply { mkdirs() }
                val file = java.io.File(dir, "shot_${System.currentTimeMillis()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
                shotUri = uri
                camera.launch(uri)
            }.onFailure { onPicked(null) }
        }) { Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Camera") }
        OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Gallery")
        }
    }
}

/** A product photo by id: loads it once (from memory, the editor's unsaved pictures, or the database). */
@Composable
fun rememberProductPhoto(id: String, unsaved: Map<String, String> = emptyMap()): androidx.compose.ui.graphics.ImageBitmap? {
    var b64 by remember(id) { mutableStateOf(unsaved[id] ?: "") }
    LaunchedEffect(id) {
        if (b64.isBlank() && id.isNotBlank()) b64 = com.digiglobal.goldbill.data.Repo.photo(id) ?: ""
    }
    return rememberPicture(b64)
}

/** Small rounded product thumbnail; shows a placeholder icon while loading. */
@Composable
fun ProductThumb(id: String, size: Int = 48, unsaved: Map<String, String> = emptyMap(), onClick: (() -> Unit)? = null) {
    if (id.isBlank()) return
    val pic = rememberProductPhoto(id, unsaved)
    val m = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .let { if (onClick != null) it.clickable { onClick() } else it }
    Box(m, contentAlignment = Alignment.Center) {
        if (pic != null) Image(pic, "Product photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size((size / 2).dp))
    }
}

/** Full-screen look at a product photo. */
@Composable
fun PhotoViewer(id: String, unsaved: Map<String, String> = emptyMap(), onDismiss: () -> Unit) {
    val pic = rememberProductPhoto(id, unsaved)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (pic != null) Image(pic, "Product photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp))
                else CircularProgressIndicator()
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

/**
 * Full-width pad where the customer signs with a finger. Returns the signature as base64 JPEG
 * (cropped to the ink, on white), or nothing if they cancel.
 */
@Composable
fun SignaturePadDialog(title: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val strokes = remember { mutableStateListOf<List<androidx.compose.ui.geometry.Offset>>() }
    var current by remember { mutableStateOf<List<androidx.compose.ui.geometry.Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val ink = Color(0xFF14286E)

    fun render(): String? {
        val all = strokes.filter { it.isNotEmpty() }
        if (all.isEmpty() || padSize.width == 0) return null
        val pts = all.flatten()
        val pad = 16f
        val left = (pts.minOf { it.x } - pad).coerceAtLeast(0f); val top = (pts.minOf { it.y } - pad).coerceAtLeast(0f)
        val right = (pts.maxOf { it.x } + pad).coerceAtMost(padSize.width.toFloat()); val bottom = (pts.maxOf { it.y } + pad).coerceAtMost(padSize.height.toFloat())
        val w = (right - left).toInt().coerceAtLeast(1); val h = (bottom - top).toInt().coerceAtLeast(1)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(android.graphics.Color.WHITE)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(0x14, 0x28, 0x6E); style = android.graphics.Paint.Style.STROKE
            strokeWidth = 6f; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
        }
        all.forEach { s ->
            val path = android.graphics.Path()
            path.moveTo(s[0].x - left, s[0].y - top)
            s.drop(1).forEach { o -> path.lineTo(o.x - left, o.y - top) }
            if (s.size == 1) path.lineTo(s[0].x - left + 0.5f, s[0].y - top)
            c.drawPath(path, p)
        }
        return com.digiglobal.goldbill.util.Images.fromBitmap(bmp)
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(0.96f)) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Sign inside the box with your finger.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                        .then(Modifier.background(Color.White))
                ) {
                    androidx.compose.foundation.Canvas(
                        Modifier.fillMaxSize()
                            .onSizeChanged { padSize = it }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { o -> current = listOf(o) },
                                    onDrag = { change, _ -> current = current + change.position },
                                    onDragEnd = { strokes.add(current); current = emptyList() },
                                    onDragCancel = { strokes.add(current); current = emptyList() }
                                )
                            }
                    ) {
                        // signing line
                        drawLine(Color(0xFFBDBDBD), androidx.compose.ui.geometry.Offset(24f, size.height * 0.78f),
                            androidx.compose.ui.geometry.Offset(size.width - 24f, size.height * 0.78f), strokeWidth = 2f)
                        (strokes + listOf(current)).forEach { s ->
                            if (s.size == 1) drawCircle(ink, 3f, s[0])
                            for (i in 1 until s.size) drawLine(ink, s[i - 1], s[i], strokeWidth = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        }
                    }
                    androidx.compose.foundation.layout.Box(Modifier.matchParentSize().padding(8.dp)) {
                        Text("✕", color = Color(0xFFBDBDBD), modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 40.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { strokes.clear(); current = emptyList() }) { Text("Clear") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(enabled = strokes.isNotEmpty(), onClick = { render()?.let(onDone) }) { Text("Done") }
                }
            }
        }
    }
}
