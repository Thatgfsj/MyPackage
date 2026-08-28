package com.thatgfsj.mypackage.ui.importscan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.thatgfsj.mypackage.data.ChunkAssembler
import com.thatgfsj.mypackage.data.ShareCodec
import com.thatgfsj.mypackage.data.StationRepository
import com.thatgfsj.mypackage.ui.components.Capsule
import com.thatgfsj.mypackage.ui.components.ScreenHeader
import kotlinx.coroutines.launch

@Composable
fun ImportScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StationRepository.get(context) }
    val assembler = remember { ChunkAssembler() }

    var status by remember { mutableStateOf("扫描其他设备分享的二维码\n多码时会自动合并分片") }
    var importedCount by remember { mutableStateOf<Int?>(null) }
    var scanning by remember { mutableStateOf(false) }

    val scanOptions = remember {
        ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("对准分享二维码")
            setBeepEnabled(false)
            // 跟随手机方向（默认会锁横屏，不符合竖屏使用习惯）
            setOrientationLocked(false)
        }
    }

    fun doImport(payload: com.thatgfsj.mypackage.data.StationPayload) {
        scope.launch {
            repo.importPayload(payload)
            importedCount = payload.s.size
            status = "已导入 ${payload.s.size} 个驿站"
            Capsule.show("成功导入 ${payload.s.size} 个驿站", Capsule.Kind.SUCCESS)
            assembler.reset()
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        scanning = false
        val text = result.contents
        if (text.isNullOrBlank()) return@rememberLauncherForActivityResult

        val full = ShareCodec.tryDecodeFull(text)
        if (full != null) {
            doImport(full)
            return@rememberLauncherForActivityResult
        }

        val chunk = ShareCodec.tryDecodeChunk(text)
        if (chunk != null) {
            val done = assembler.add(chunk)
            if (done != null) {
                val payload = ShareCodec.assemble(done)
                if (payload != null) {
                    doImport(payload)
                } else {
                    status = "分片数据损坏，请重新扫描"
                    assembler.reset()
                }
            } else {
                val got = assembler.received()
                val missing = (0 until chunk.t).filter { it !in got }
                status = "已收集 ${got.size}/${chunk.t} 片\n待扫描页码：${
                    missing.map { it + 1 }.joinToString("、")
                }"
            }
        } else {
            status = "无法识别的二维码\n请扫描本应用生成的分享码"
        }
    }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (granted) {
            scanning = true
            scanLauncher.launch(scanOptions)
        } else {
            Capsule.show("需要相机权限才能扫码", Capsule.Kind.FALLBACK)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ScreenHeader(title = "扫码导入")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(28.dp)
                ) {
                    Icon(
                        imageVector = if (importedCount != null) Icons.Rounded.CheckCircle
                        else Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (importedCount != null) {
                        Button(
                            onClick = { importedCount = null; status = "可以继续扫描导入" },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("继续扫描")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (cameraGranted) {
                                    scanning = true
                                    scanLauncher.launch(scanOptions)
                                } else {
                                    permLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (scanning) "扫描中…" else "开始扫码")
                        }
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = "提示：分片二维码需要按顺序全部扫描完成才能合并。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
