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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import kotlin.math.ceil
import java.io.File

private data class QrItem(
    val text: String,
    val index: Int,
    val total: Int,
    val fromName: String,
    val toName: String
)

@Composable
fun ShareScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StationRepository.get(context) }
    val stations by repo.observeAll().collectAsState(initial = emptyList())

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var qrs by remember { mutableStateOf<List<QrItem>?>(null) }
    var generating by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ScreenHeader(title = "分享快递站")

        val qrList = qrs
        if (qrList == null) {
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
                                    val encoded = withContext(Dispatchers.IO) {
                                        ShareCodec.encode(chosen)
                                    }
                                    qrs = encoded.mapIndexed { i, text ->
                                        QrItem(
                                            text = text,
                                            index = i + 1,
                                            total = encoded.size,
                                            fromName = chosen.first().name,
                                            toName = chosen.last().name
                                        )
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
            ShareQrView(items = qrList, onBack = { qrs = null })
        }
    }
}

@Composable
private fun ShareQrView(items: List<QrItem>, onBack: () -> Unit) {
    val multi = items.size > 1
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

        AnimatedVisibility(
            visible = multi,
            enter = slideInHorizontally(tween(250)) { it / 2 } + fadeIn(),
            exit = slideOutHorizontally(tween(200)) { it / 2 } + fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "信息太多，一个二维码装不下，已生成 ${items.size} 个，将自动轮播，请依次扫描。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "对方请使用本应用「设置 → 扫码导入」依次扫描，全部扫完自动合并。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        if (!multi) {
            Text(
                "对方请使用本应用「设置 → 扫码导入」扫描即可恢复（驿站图片已一并打包）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        if (multi) {
            DynamicQrPlayer(items = items, modifier = Modifier.weight(1f))
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                QrCard(item = items.first())
            }
        }
    }
}

/** 动态二维码播放器：多片自动循环轮播，独立区域展示切换倒计时 */
@Composable
private fun DynamicQrPlayer(items: List<QrItem>, modifier: Modifier = Modifier) {
    val total = items.size
    var index by remember { mutableIntStateOf(0) }
    val holdMillis = 3200
    val progress = remember { Animatable(0f) }

    LaunchedEffect(index, total) {
        if (total > 1) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
            index = (index + 1) % total
        }
    }

    val remainSeconds = (((1f - progress.value) * holdMillis) / 1000f).coerceIn(0f, 10f)

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
                    QrImage(
                        text = items[i].text,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                }
                Text(
                    text = "第 ${index + 1}/$total 页",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = "从 ${items[index].fromName} 到 ${items[index].toName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 时间展示区：倒计时提示下一页切换
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(top = 14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (total > 1) "下一页 ${ceil(remainSeconds).toInt()} 秒" else "单页展示",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Text(
            text = "多页将按顺序自动循环播放，对端依次扫描即可",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun QrCard(item: QrItem) {
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
            QrImage(text = item.text, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            Text(
                text = "驿站配置二维码",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                text = "从 ${item.fromName} 到 ${item.toName}",
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
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator()
        }
    }
}
