package com.thatgfsj.mypackage.ui.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.thatgfsj.mypackage.data.ShareCodec
import com.thatgfsj.mypackage.data.StationRepository
import com.thatgfsj.mypackage.qr.QrGenerator
import com.thatgfsj.mypackage.ui.components.ScreenHeader
import com.thatgfsj.mypackage.ui.components.platformLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 分享产物：小配置直接单码；大配置走喷泉码动态轮播 */
sealed class ShareBundle {
    data class Single(val text: String, val fromName: String, val toName: String) : ShareBundle()
    data class Fountain(
        val prepared: ShareCodec.FountainPrepared,
        val fromName: String,
        val toName: String
    ) : ShareBundle()
}

@Composable
fun ShareScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StationRepository.get(context) }
    val stations by repo.observeAll().collectAsState(initial = emptyList())

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var bundle by remember { mutableStateOf<ShareBundle?>(null) }
    var generating by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ScreenHeader(title = "分享快递站")

        val b = bundle
        if (b == null) {
            if (stations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.thatgfsj.mypackage.ui.components.EmptyState(
                        icon = Icons.Rounded.Storefront,
                        text = "还没有快递站可分享"
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "选择要分享的驿站（已选 ${selectedIds.size} 个）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            selectedIds = if (selectedIds.size == stations.size) emptySet()
                            else stations.map { it.id }.toSet()
                        }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(if (selectedIds.size == stations.size) "取消全选" else "全选")
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(stations, key = { it.id }) { station ->
                            val checked = station.id in selectedIds
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            selectedIds = if (checked) selectedIds - station.id
                                            else selectedIds + station.id
                                        }
                                    )
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedIds = if (checked) selectedIds - station.id
                                                else selectedIds + station.id
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp, 36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            if (station.imagePath.isNotBlank() && File(station.imagePath).exists()) {
                                                AsyncImage(
                                                    model = File(station.imagePath),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        Spacer(Modifier.size(10.dp))
                                        Column {
                                            Text(
                                                station.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                platformLabel(station) +
                                                    if (station.lockerRange.isNotBlank()) " · ${station.lockerRange}" else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = {
                                val chosen = stations.filter { it.id in selectedIds }
                                if (chosen.isEmpty()) return@Button
                                generating = true
                                scope.launch {
                                    val text = withContext(Dispatchers.IO) { ShareCodec.encodeSmall(chosen) }
                                    bundle = if (text.toByteArray(Charsets.UTF_8).size <= ShareCodec.QR_BYTE_LIMIT) {
                                        ShareBundle.Single(text, chosen.first().name, chosen.last().name)
                                    } else {
                                        val prep = withContext(Dispatchers.IO) { ShareCodec.prepareFountain(text) }
                                        ShareBundle.Fountain(prep, chosen.first().name, chosen.last().name)
                                    }
                                    generating = false
                                }
                            },
                            enabled = selectedIds.isNotEmpty() && !generating,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                        ) {
                            Text(if (generating) "正在生成…" else "生成二维码")
                        }
                    }
                }
            }
        } else {
            when (b) {
                is ShareBundle.Single -> ShareQrView(
                    text = b.text,
                    fromName = b.fromName,
                    toName = b.toName,
                    onBack = { bundle = null }
                )
                is ShareBundle.Fountain -> FountainShareView(
                    prep = b.prepared,
                    fromName = b.fromName,
                    toName = b.toName,
                    onBack = { bundle = null }
                )
            }
        }
    }
}

@Composable
private fun ShareQrView(text: String, fromName: String, toName: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenHeader(title = "分享二维码")
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onBack,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Text("重新选择")
            }
        }
        Text(
            "接收手机请使用本应用「设置>扫码导入」扫描即可恢复（驿站图片已一并打包）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            QrCard(text = text, fromName = fromName, toName = toName)
        }
    }
}

/** 喷泉码动态轮播：每一轮产生全新随机帧，持续对准即可，扫到的帧自动累计 */
@Composable
private fun FountainShareView(
    prep: ShareCodec.FountainPrepared,
    fromName: String,
    toName: String,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenHeader(title = "分享二维码")
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onBack,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Text("重新选择")
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "动态二维码，接收手机请使用本应用「设置>扫码导入」对准镜头保持不动，全部扫完自动合并。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(14.dp)
            )
        }
        DynamicQrPlayer(prep = prep, fromName = fromName, toName = toName, modifier = Modifier.weight(1f))
    }
}

/** 动态二维码播放器：喷泉帧自动循环轮播，每轮全新随机帧，支持加减速 */
@Composable
private fun DynamicQrPlayer(
    prep: ShareCodec.FountainPrepared,
    fromName: String,
    toName: String,
    modifier: Modifier = Modifier
) {
    var index by remember(prep) { mutableIntStateOf(0) }
    var round by remember(prep) { mutableIntStateOf(0) }
    var holdMillis by remember(prep) { mutableStateOf(500) }
    val progress = remember(prep) { Animatable(0f) }

    val frames by produceState<List<String>>(initialValue = emptyList(), prep, round) {
        value = withContext(Dispatchers.IO) {
            (0 until prep.frameCount).map { ShareCodec.frameText(prep, it, round) }
        }
    }
    val ready = frames.size == prep.frameCount

    LaunchedEffect(ready, index, holdMillis, frames) {
        if (ready) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
            val next = index + 1
            if (next >= prep.frameCount) {
                index = 0
                round++
            } else {
                index = next
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Crossfade(
                    targetState = index,
                    animationSpec = tween(260),
                    label = "qrCross"
                ) { i ->
                    val f = frames.getOrNull(i)
                    if (f != null) {
                        QrImage(text = f, modifier = Modifier.fillMaxWidth())
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        Text(
            text = "从 $fromName 到 $toName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { holdMillis = (holdMillis + 300).coerceAtMost(3000) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f)
            ) {
                Text("减速播放")
            }
            Text(
                text = "${holdMillis / 1000.0}",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.1f)
            )
            OutlinedButton(
                onClick = { holdMillis = (holdMillis - 300).coerceAtLeast(200) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f)
            ) {
                Text("加速播放")
            }
        }
    }
}

@Composable
private fun QrCard(text: String, fromName: String, toName: String) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        appeared = true
    }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "qrIn"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                scaleX = 0.92f + 0.08f * progress
                scaleY = 0.92f + 0.08f * progress
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            QrImage(text = text, modifier = Modifier.fillMaxWidth())
            Text(
                text = "驿站配置二维码",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                text = "从 $fromName 到 $toName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun QrImage(text: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, text) {
        value = QrGenerator.generate(text)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "分享二维码",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            CircularProgressIndicator()
        }
    }
}
