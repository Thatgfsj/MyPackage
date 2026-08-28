package com.thatgfsj.mypackage.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.thatgfsj.mypackage.data.StationEntity
import com.thatgfsj.mypackage.data.StationRepository
import com.thatgfsj.mypackage.util.ImageUtils
import com.thatgfsj.mypackage.util.StationLauncher
import kotlinx.coroutines.launch
import java.io.File

data class ActionItem(
    val icon: ImageVector,
    val label: String,
    val tint: Color? = null,
    val onClick: () -> Unit
)

/** 居中弹出的两列宫格操作菜单 */
@Composable
fun ActionGridDialog(
    title: String,
    subtitle: String? = null,
    items: List<ActionItem>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        },
        text = {
            Column {
                items.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        row.forEach { item ->
                            GridButton(item = item, modifier = Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Text(
                    "取消",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun GridButton(item: ActionItem, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier.clickable {
            item.onClick()
        }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp)
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = item.tint ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                item.label,
                style = MaterialTheme.typography.labelLarge,
                color = item.tint ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 快递站长按操作菜单（首页 / 管理页共用）：
 * 两列宫格：更改站点名称 · 更改站点图片 · 向前/向后移动一位 · 移动到最前/最后面 · 复制链接 · 在浏览器中打开
 */
@Composable
fun StationActionsDialog(
    target: StationEntity?,
    position: Int? = null,
    total: Int? = null,
    onDismiss: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StationRepository.get(context) }
    var renameTarget by remember { mutableStateOf<StationEntity?>(null) }
    var imageTarget by remember { mutableStateOf<StationEntity?>(null) }

    if (target != null) {
        ActionGridDialog(
            title = target.name,
            subtitle = if (position != null && total != null) {
                "当前位置：第 $position 个，共 $total 个"
            } else {
                "选择要执行的操作"
            },
            onDismiss = onDismiss,
            items = listOf(
                ActionItem(Icons.Rounded.DriveFileRenameOutline, "更改站点名称") {
                    renameTarget = target
                },
                ActionItem(Icons.Rounded.Image, "更改站点图片") {
                    imageTarget = target
                },
                ActionItem(Icons.Rounded.ArrowUpward, "向前移动一位") {
                    scope.launch { repo.moveBy(target.id, -1) }
                },
                ActionItem(Icons.Rounded.ArrowDownward, "向后移动一位") {
                    scope.launch { repo.moveBy(target.id, 1) }
                },
                ActionItem(Icons.Rounded.VerticalAlignTop, "移动到最前面") {
                    scope.launch { repo.moveTo(target.id, 0) }
                },
                ActionItem(Icons.Rounded.VerticalAlignBottom, "移动到最后面") {
                    scope.launch { repo.moveTo(target.id, Int.MAX_VALUE) }
                },
                ActionItem(Icons.Rounded.ContentCopy, "复制链接") {
                    StationLauncher.copyLink(context, target.rawLink)
                    Capsule.show("链接已复制", Capsule.Kind.SUCCESS)
                },
                ActionItem(Icons.Rounded.Language, "在浏览器中打开") {
                    StationLauncher.openInBrowser(context, target.rawLink)
                }
            )
        )
    }

    renameTarget?.let { station ->
        RenameDialog(station = station, onDismiss = { renameTarget = null })
    }
    imageTarget?.let { station ->
        ImageSourceDialog(station = station, onDismiss = { imageTarget = null })
    }
}

@Composable
private fun RenameDialog(station: StationEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StationRepository.get(context) }
    var text by remember(station.id) { mutableStateOf(station.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("更改站点名称") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && text.trim() != station.name,
                onClick = {
                    scope.launch {
                        repo.rename(station.id, text)
                        Capsule.show("名称已更新", Capsule.Kind.SUCCESS)
                    }
                    onDismiss()
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ImageSourceDialog(station: StationEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StationRepository.get(context) }

    fun update(path: String) {
        scope.launch {
            repo.updateImage(station.id, path)
            Capsule.show("图片已更新", Capsule.Kind.SUCCESS)
        }
    }

    var pendingFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingFile
        if (ok && f != null && f.exists()) {
            ImageUtils.downscale(f)
            update(f.absolutePath)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            ImageUtils.importFromUri(context, uri)?.let { update(it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("更改站点图片") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("从相册更换")
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        val f = ImageUtils.newCameraFile(context)
                        pendingFile = f
                        cameraLauncher.launch(
                            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("拍摄更换")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
