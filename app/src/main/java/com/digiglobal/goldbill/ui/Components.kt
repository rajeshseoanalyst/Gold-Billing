package com.digiglobal.goldbill.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

