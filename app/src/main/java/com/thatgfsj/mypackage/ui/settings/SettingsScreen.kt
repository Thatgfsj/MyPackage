package com.thatgfsj.mypackage.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thatgfsj.mypackage.BuildConfig
import com.thatgfsj.mypackage.data.ShareCodec
import com.thatgfsj.mypackage.data.StationRepository
import com.thatgfsj.mypackage.ui.components.Capsule
import com.thatgfsj.mypackage.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onAdd: () -> Unit,
    onManage: () -> Unit,
    onShare: () -> Unit,
    onImportScan: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StationRepository.get(context) }
    var showAbout by remember { mutableStateOf(false) }
    var updateTag by remember { mutableStateOf<String?>(null) }
    var importSource by remember { mutableStateOf(false) } // true = 展示导入方式选择
    var networkUrl by remember { mutableStateOf("") }
    var networkDialog by remember { mutableStateOf(false) }

    fun checkUpdate() {
        scope.launch {
            Capsule.show("正在检查更新…", Capsule.Kind.OPENING)
            val tag = withContext(Dispatchers.IO) { UpdateChecker.latestTag() }
            if (tag == null) {
                Capsule.show("暂无发布版本或网络检查失败", Capsule.Kind.FALLBACK)
            } else if (UpdateChecker.isNewer(tag, BuildConfig.VERSION_NAME)) {
                Capsule.clear()
                updateTag = tag
            } else {
                Capsule.show("已是最新版本 v${BuildConfig.VERSION_NAME}", Capsule.Kind.SUCCESS)
            }
        }
    }

    fun doImportText(text: String, successMsg: String = "成功导入") {
        scope.launch {
            try {
                val payload = ShareCodec.tryDecodeFull(text)
                    ?: ShareCodec.tryDecodeChunk(text)?.let { ShareCodec.assemble(listOf(it)) }
                if (payload == null || payload.s.isEmpty()) {
                    Capsule.show("文件格式无法识别", Capsule.Kind.FALLBACK)
                } else {
                    withContext(Dispatchers.IO) { repo.importPayload(payload) }
                    Capsule.show("成功导入 ${payload.s.size} 个驿站", Capsule.Kind.SUCCESS)
                }
            } catch (e: Exception) {
                Capsule.show("导入失败：${e.message ?: "未知错误"}", Capsule.Kind.FALLBACK)
            }
        }
    }

    // 网络导入：从 URL 拉取 JSON（分享或备份链接）
    fun doNetworkImport(url: String) {
        if (url.isBlank()) {
            Capsule.show("请输入链接", Capsule.Kind.FALLBACK)
            return
        }
        networkDialog = false
        scope.launch {
            Capsule.show("正在从网络导入…", Capsule.Kind.OPENING)
            try {
                val text = withContext(Dispatchers.IO) {
                    val conn = java.net.URL(url.trim()).openConnection()
                    conn.connectTimeout = 10000
                    conn.readTimeout = 15000
                    conn.setRequestProperty("User-Agent", "MyPackage-App")
                    conn.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("无法获取内容")
                }
                doImportText(text)
            } catch (e: Exception) {
                Capsule.show("网络导入失败：${e.message ?: "未知错误"}", Capsule.Kind.FALLBACK)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        ShareCodec.encodeFull(repo.getAll())
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(text.toByteArray(Charsets.UTF_8))
                        }
                    }
                    Capsule.show("数据已导出", Capsule.Kind.SUCCESS)
                } catch (e: Exception) {
                    Capsule.show("导出失败：${e.message ?: "未知错误"}", Capsule.Kind.FALLBACK)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                    } ?: throw IllegalStateException("无法读取文件")
                    doImportText(text)
                } catch (e: Exception) {
                    Capsule.show("导入失败：${e.message ?: "未知错误"}", Capsule.Kind.FALLBACK)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        val items = listOf(
            SettingsRowData(Icons.Rounded.Add, "添加快递站", "创建新的快递站") { onAdd() },
            SettingsRowData(Icons.Rounded.Tune, "管理快递站", "编辑、删除、调整顺序") { onManage() },
            SettingsRowData(Icons.Rounded.Share, "分享快递站", "生成二维码分享给好友") { onShare() },
            SettingsRowData(Icons.Rounded.QrCodeScanner, "扫码导入", "扫描分享二维码恢复驿站") { onImportScan() },
            SettingsRowData(Icons.Rounded.FileDownload, "导出数据", "备份为 JSON 文件") {
                exportLauncher.launch("mypackage-backup.json")
            },
            SettingsRowData(Icons.Rounded.FileUpload, "导入数据", "从 JSON 文件导入") {
                importSource = true
            },
            SettingsRowData(Icons.Rounded.Info, "关于", "版本 " + BuildConfig.VERSION_NAME) { showAbout = true },
            SettingsRowData(Icons.Rounded.CloudDownload, "检查更新", "从 GitHub 获取最新版本") {
                checkUpdate()
            }
        )

        items.forEachIndexed { index, data ->
            SettingsRow(rowData = data, delayMs = index * 50)
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于") },
            text = {
                Column {
                    Text(
                        "我的快递 My Packages v${BuildConfig.VERSION_NAME}\n\n" +
                            "所有数据仅保存在本机，无需任何网络权限（仅在你手动「检查更新」时访问 GitHub）。"
                    )
                    Text(
                        "开源仓库：github.com/Thatgfsj/MyPackage",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable {
                                showAbout = false
                                com.thatgfsj.mypackage.util.StationLauncher.openInBrowser(
                                    context,
                                    com.thatgfsj.mypackage.util.UpdateChecker.REPO_URL
                                )
                            }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("好的") }
            }
        )
    }

    updateTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { updateTag = null },
            title = { Text("发现新版本") },
            text = {
                Text("最新版本：$tag\n当前版本：v${BuildConfig.VERSION_NAME}\n\n是否前往 GitHub 下载？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        updateTag = null
                        com.thatgfsj.mypackage.util.StationLauncher.openInBrowser(
                            context,
                            com.thatgfsj.mypackage.util.UpdateChecker.REPO_URL + "/releases/latest"
                        )
                    }
                ) { Text("前往下载") }
            },
            dismissButton = {
                TextButton(onClick = { updateTag = null }) { Text("下次再说") }
            }
        )
    }

    if (importSource) {
        com.thatgfsj.mypackage.ui.components.ActionGridDialog(
            title = "导入数据",
            subtitle = "选择导入方式",
            onDismiss = { importSource = false },
            items = listOf(
                com.thatgfsj.mypackage.ui.components.ActionItem(
                    Icons.Rounded.CloudUpload, "网络导入", onClick = {
                        importSource = false
                        networkDialog = true
                    }
                ),
                com.thatgfsj.mypackage.ui.components.ActionItem(
                    Icons.Rounded.FileUpload, "本地导入", onClick = {
                        importSource = false
                        importLauncher.launch(arrayOf("application/json", "text/plain"))
                    }
                )
            )
        )
    }

    if (networkDialog) {
        AlertDialog(
            onDismissRequest = { networkDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("网络导入") },
            text = {
                Column {
                    Text(
                        "输入包含驿站配置的 JSON 链接（分享或备份地址）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = networkUrl,
                        onValueChange = { networkUrl = it },
                        placeholder = { Text("https://…") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { doNetworkImport(networkUrl) },
                    shape = RoundedCornerShape(50)
                ) {
                    Text("导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { networkDialog = false }) { Text("取消") }
            }
        )
    }
}

private data class SettingsRowData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
private fun SettingsRow(rowData: SettingsRowData, delayMs: Int) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        appeared = true
    }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(320),
        label = "row"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(120),
        label = "press"
    )

    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 30f
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, onClick = rowData.onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        rowData.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(rowData.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    rowData.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier)
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
