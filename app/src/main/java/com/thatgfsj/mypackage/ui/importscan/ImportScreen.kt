package com.thatgfsj.mypackage.ui.importscan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.thatgfsj.mypackage.data.ChunkAssembler
import com.thatgfsj.mypackage.data.ShareCodec
import com.thatgfsj.mypackage.data.StationPayload
import com.thatgfsj.mypackage.data.StationRepository
import com.thatgfsj.mypackage.ui.components.Capsule
import com.thatgfsj.mypackage.ui.components.ScreenHeader
import kotlinx.coroutines.launch

/** 扫码导入：持续扫码，自动收集分片并按百分比展示进度，集齐后自动导入 */
@Composable
fun ImportScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StationRepository.get(context) }
    val assembler = remember { ChunkAssembler() }
    val barcodeRef = remember { mutableStateOf<BarcodeView?>(null) }

    var status by remember { mutableStateOf("将镜头对准分享二维码\n自动连续扫描") }
    var importedCount by remember { mutableStateOf<Int?>(null) }
    var total by remember { mutableStateOf<Int?>(null) }
    var received by remember { mutableIntStateOf(0) }
    var staleCount by remember { mutableStateOf(0) }
    var speedHint by remember { mutableStateOf(false) }
    var fountain by remember { mutableStateOf<ShareCodec.FountainAssembler?>(null) }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            asked = true
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun resetScan() {
        assembler.reset()
        fountain = null
        total = null
        received = 0
        staleCount = 0
        speedHint = false
        status = "将镜头对准分享二维码\n自动连续扫描"
    }

    fun doImport(payload: StationPayload) {
        scope.launch {
            barcodeRef.value?.pause()
            repo.importPayload(payload)
            importedCount = payload.s.size
            speedHint = false
            Capsule.show("成功导入 ${payload.s.size} 个驿站", Capsule.Kind.SUCCESS)
        }
    }

    var lastText by remember { mutableStateOf("") }
    var lastTime by remember { mutableStateOf(0L) }

    fun processScan(text: String) {
        val now = System.currentTimeMillis()
        if (text == lastText && now - lastTime < 600) return
        lastText = text
        lastTime = now

        val full = ShareCodec.tryDecodeFull(text)
        if (full != null) {
            doImport(full)
            return
        }

        // 旧版顺序分片（兼容老版本分享）
        val chunk = ShareCodec.tryDecodeChunk(text)
        if (chunk != null) {
            if (total == null) total = chunk.t
            if (assembler.has(chunk.i)) {
                // 扫到已收过的页：大概率对端轮播过快
                staleCount++
                if (staleCount >= 3) speedHint = true
            } else {
                staleCount = 0
                speedHint = false
                val done = assembler.add(chunk)
                received = assembler.count()
                // 集齐所有分片：立即合并并自动保存
                if (done != null) {
                    val payload = ShareCodec.assemble(done)
                    if (payload != null) {
                        doImport(payload)
                    } else {
                        status = "分片数据损坏，请重新扫描"
                        assembler.reset()
                        total = null
                        received = 0
                    }
                    return
                }
            }
            status = "已收集 $received 片"
            return
        }

        // 新版喷泉码帧
        val frame = ShareCodec.decodeFrame(text)
        if (frame != null) {
            val info = ShareCodec.frameInfo(frame)
            if (info != null) {
                val (k, origLen, seed) = info
                var asm = fountain
                if (asm == null || asm.k != k || asm.origLen != origLen) {
                    asm = ShareCodec.FountainAssembler(k, origLen).also { fountain = it }
                    staleCount = 0
                    speedHint = false
                }
                total = k
                if (asm.isSeen(seed)) {
                    // 同一帧重复读取，正常现象
                } else {
                    val progressed = asm.addFrame(frame)
                    received = asm.recovered
                    if (!progressed) {
                        staleCount++
                        if (staleCount >= 6) speedHint = true
                    } else {
                        staleCount = 0
                        speedHint = false
                    }
                }
                if (asm.isComplete()) {
                    val payloadText = asm.combine()
                    val payload = payloadText?.let { ShareCodec.tryDecodeFull(it) }
                    if (payload != null) {
                        doImport(payload)
                    } else {
                        status = "数据校验失败，请重新开始扫描"
                        fountain = null
                        total = null
                        received = 0
                        speedHint = false
                    }
                } else if (staleCount >= 6) {
                    status = "长时间无新内容\n建议对方调整播放速度"
                }
                return
            }
        }

        status = "非本应用的分享码，已忽略"
    }

    // 相机随页面生命周期启停
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, cameraGranted) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    if (importedCount == null) barcodeRef.value?.resume()
                Lifecycle.Event.ON_PAUSE -> barcodeRef.value?.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ScreenHeader(title = "扫码导入")

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (cameraGranted) {
                AndroidView(
                    factory = { ctx ->
                        BarcodeView(ctx).apply {
                            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                            decodeContinuous(object : BarcodeCallback {
                                override fun barcodeResult(result: BarcodeResult) {
                                    val text = result.text ?: return
                                    scope.launch { processScan(text) }
                                }

                                override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>) {}
                            })
                            resume()
                        }.also { bv -> barcodeRef.value = bv }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // 取景框：压暗四周并描边，方便用户定位二维码
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val side = minOf(
                                260.dp.toPx(),
                                size.width - 32.dp.toPx(),
                                size.height - 160.dp.toPx()
                            ).coerceAtLeast(120f)
                            val left = (size.width - side) / 2f
                            val top = (size.height - side) / 2f - 20.dp.toPx()
                            val radius = 20.dp.toPx()
                            val frame = RoundRect(
                                rect = Rect(left, top, left + side, top + side),
                                cornerRadius = CornerRadius(radius)
                            )
                            val path = Path().apply {
                                addRect(Rect(0f, 0f, size.width, size.height))
                                addRoundRect(frame)
                            }
                            clipPath(path, ClipOp.Difference) {
                                drawRect(Color(0x59000000))
                            }
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.9f),
                                topLeft = Offset(left, top),
                                size = Size(side, side),
                                cornerRadius = CornerRadius(radius),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.size(16.dp))
                    Text(
                        text = if (asked) "相机权限被拒绝，无法持续扫码" else "需要相机权限进行持续扫码",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.size(16.dp))
                    Button(
                        onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("授予相机权限")
                    }
                }
            }

            if (cameraGranted && importedCount == null) {
                // 进度浮层：只展示百分比
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xCC101010))
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "对准动态二维码，自动连续扫描",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${if (total != null) received * 100 / (total ?: 1) else 0}%",
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall
                    )
                    if (total != null) {
                        Text(
                            text = "已收集 $received/$total 片",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (speedHint) {
                        Text(
                            text = "进展缓慢，建议对方调整播放速度",
                            color = Color(0xFFFFB4AB),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            if (importedCount != null) {
                // 导入完成面板
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xE6101010))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.size(16.dp))
                    Text(
                        text = "已导入 $importedCount 个驿站",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(Modifier.size(20.dp))
                    Button(
                        onClick = {
                            importedCount = null
                            resetScan()
                            barcodeRef.value?.resume()
                        },
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("继续扫描")
                    }
                }
            }
        }
    }
}
