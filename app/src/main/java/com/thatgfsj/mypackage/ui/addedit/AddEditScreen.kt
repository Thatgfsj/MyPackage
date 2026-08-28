package com.thatgfsj.mypackage.ui.addedit

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.thatgfsj.mypackage.data.Platform
import com.thatgfsj.mypackage.data.StationEntity
import com.thatgfsj.mypackage.data.StationRepository
import com.thatgfsj.mypackage.qr.QrDecoder
import com.thatgfsj.mypackage.ui.components.Capsule
import com.thatgfsj.mypackage.ui.components.ScreenHeader
import com.thatgfsj.mypackage.util.ImageUtils
import kotlinx.coroutines.launch
import java.io.File

private data class Snapshot(
    val name: String,
    val platform: Platform,
    val rawLink: String,
    val range: String,
    val imagePath: String,
    val customPackage: String
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(stationId: Long?, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StationRepository.get(context) }

    var name by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf(Platform.PDD) }
    var rawLink by remember { mutableStateOf("") }
    var range by remember { mutableStateOf("") }
    var imagePath by remember { mutableStateOf("") }
    var customPackage by remember { mutableStateOf("") }
    var originalSort by remember { mutableStateOf(0) }
    var originalSnapshot by remember { mutableStateOf<Snapshot?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showQrSource by remember { mutableStateOf(false) }

    LaunchedEffect(stationId) {
        if (stationId != null) {
            repo.getById(stationId)?.let { s ->
                name = s.name
                platform = Platform.from(s.platform)
                rawLink = s.rawLink
                range = s.lockerRange
                imagePath = s.imagePath
                customPackage = s.customPackage
                originalSort = s.sortOrder
            }
        }
        originalSnapshot = Snapshot(name, platform, rawLink, range, imagePath, customPackage)
        loaded = true
    }

    val dirty = loaded && originalSnapshot != null &&
        Snapshot(name, platform, rawLink, range, imagePath, customPackage) != originalSnapshot
    BackHandler(enabled = dirty) { showDiscard = true }

    // 相机复用：拍摄驿站照片 / 拍摄二维码图片
    var cameraJob by remember { mutableStateOf<String?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCameraFile
        val job = cameraJob
        if (ok && f != null && f.exists()) {
            when (job) {
                "photo" -> {
                    ImageUtils.downscale(f)
                    imagePath = f.absolutePath
                }
                "qr" -> extractQrFromFile(scope, f.absolutePath, { rawLink = it }, onFail = {
                    ImageUtils.deleteImage(f.absolutePath)
                })
            }
        }
        cameraJob = null
    }

    // 相册复用：选驿站照片 / 选二维码图片
    var galleryJob by remember { mutableStateOf<String?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val job = galleryJob
        if (uri != null) {
            when (job) {
                "photo" -> ImageUtils.importFromUri(context, uri)?.let { imagePath = it }
                "qr" -> ImageUtils.importFromUri(context, uri)?.let { path ->
                    extractQrFromFile(scope, path, { rawLink = it }, onFail = {
                        ImageUtils.deleteImage(path)
                    })
                }
            }
        }
        galleryJob = null
    }

    fun startCamera(job: String) {
        val f = ImageUtils.newCameraFile(context)
        pendingCameraFile = f
        cameraJob = job
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
        cameraLauncher.launch(uri)
    }

    fun discardChanges() {
        if (imagePath != originalSnapshot?.imagePath && imagePath.isNotBlank()) {
            ImageUtils.deleteImage(imagePath)
        }
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenHeader(
                title = if (stationId == null) "添加快递站" else "编辑快递站",
                onBack = { if (dirty) showDiscard = true else onDone() }
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { if (dirty) showDiscard = true else onDone() },
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("快递站的名字") },
                        placeholder = { Text("例如：家楼下") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("快递平台", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Platform.entries.forEach { p ->
                            FilterChip(
                                selected = platform == p,
                                onClick = { platform = p },
                                label = { Text(p.label) }
                            )
                        }
                    }

                    if (platform == Platform.CUSTOM) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("跳转应用", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (customPackage.isBlank()) "未选择，点击首页卡片时无法自动跳转"
                                    else appLabel(context, customPackage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            OutlinedButton(onClick = { showAppPicker = true }) {
                                Text(if (customPackage.isBlank()) "选择应用" else "更换")
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("取件码原始链接", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = rawLink,
                                onValueChange = { rawLink = it },
                                placeholder = { Text("上传二维码图片或粘贴链接") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                onClick = { showQrSource = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("上传图片识别")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = range,
                        onValueChange = { range = it },
                        label = { Text("柜号范围（可选）") },
                        placeholder = { Text("例如：1-20") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("驿站照片（可选）", style = MaterialTheme.typography.titleMedium)
                        if (imagePath.isNotBlank() && File(imagePath).exists()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(16.dp))
                            ) {
                                AsyncImage(
                                    model = File(imagePath),
                                    contentDescription = "驿站照片",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = {
                                        ImageUtils.deleteImage(imagePath)
                                        imagePath = ""
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "移除照片",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { startCamera("photo") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("拍摄")
                            }
                            OutlinedButton(
                                onClick = {
                                    galleryJob = "photo"
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("相册")
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val entity = StationEntity(
                                id = stationId ?: 0L,
                                name = name.trim(),
                                platform = platform.name,
                                rawLink = rawLink.trim(),
                                lockerRange = range.trim(),
                                imagePath = imagePath,
                                customPackage = customPackage,
                                // 编辑时保留原排序值，保证保存后仍在原位置
                                sortOrder = originalSort
                            )
                            scope.launch {
                                val saved = repo.save(entity)
                                // 换图后清理旧图
                                val oldPath = originalSnapshot?.imagePath.orEmpty()
                                if (saved.imagePath != oldPath && oldPath.isNotBlank()) {
                                    ImageUtils.deleteImage(oldPath)
                                }
                                Capsule.show("已保存", Capsule.Kind.SUCCESS)
                                onDone()
                            }
                        },
                        enabled = name.isNotBlank(),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(if (stationId == null) "保存" else "保存修改")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showQrSource) {
        ModalBottomSheet(onDismissRequest = { showQrSource = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            ) {
                Text("识别取件码二维码", style = MaterialTheme.typography.titleLarge)
                Text(
                    "上传一张包含取件码二维码的图片，自动提取原始链接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                SheetAction(Icons.Rounded.PhotoLibrary, "从相册提取") {
                    showQrSource = false
                    galleryJob = "qr"
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                SheetAction(Icons.Rounded.PhotoCamera, "拍摄") {
                    showQrSource = false
                    startCamera("qr")
                }
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("放弃修改？") },
            text = { Text("未保存的内容将丢失，原始数据保持不变。") },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; discardChanges() }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("继续编辑") }
            }
        )
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onPick = { pkg ->
                customPackage = pkg
                showAppPicker = false
            }
        )
    }
}

private fun extractQrFromFile(
    scope: kotlinx.coroutines.CoroutineScope,
    path: String,
    onResult: (String) -> Unit,
    onFail: () -> Unit
) {
    Capsule.show("正在识别二维码…", Capsule.Kind.OPENING)
    scope.launch {
        val text = QrDecoder.decodeFile(path)
        if (text != null) {
            onResult(text)
            Capsule.show("已提取原始链接", Capsule.Kind.SUCCESS)
        } else {
            onFail()
            Capsule.show("未识别到二维码，请换个角度重拍", Capsule.Kind.FALLBACK)
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun appLabel(context: android.content.Context, pkg: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (e: Exception) {
    pkg
}

@Composable
private fun AppPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val apps = remember {
        try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { ri ->
                    ri.loadLabel(pm)?.toString()?.let { label -> Triple(label, ri.activityInfo.packageName, ri.activityInfo.name) }
                }
                .distinctBy { it.second }
                .sortedBy { it.first }
        } catch (e: Exception) {
            emptyList()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择要跳转的应用") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                items(apps.size) { i ->
                    val (label, pkg, _) = apps[i]
                    TextButton(
                        onClick = { onPick(pkg) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
