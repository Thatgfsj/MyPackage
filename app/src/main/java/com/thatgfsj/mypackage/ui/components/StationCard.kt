package com.thatgfsj.mypackage.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.thatgfsj.mypackage.data.Platform
import com.thatgfsj.mypackage.data.StationEntity
import kotlinx.coroutines.delay
import java.io.File

fun platformLabel(station: StationEntity): String =
    when (val p = Platform.from(station.platform)) {
        Platform.CUSTOM -> if (station.customPackage.isNotBlank()) "自定义应用" else "自定义"
        else -> p.label
    }

/**
 * 首页快递站卡片（沉浸式）：
 * 图片铺满卡片，名称叠在图片左上角（渐变压暗保证可读），
 * 图片下方一行：平台徽章 + 柜号范围。无白色底板。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationCard(
    station: StationEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alreadyAppeared: Boolean = false,
    appearDelayMs: Int = 0,
    onAppeared: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    var appeared by remember { mutableStateOf(alreadyAppeared) }
    LaunchedEffect(Unit) {
        if (!appeared) {
            delay(appearDelayMs.toLong())
            appeared = true
            onAppeared()
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "appear"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "press"
    )

    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 48f
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.1f)
                .shadow(elevation = 4.dp, shape = shape, clip = true)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val path = station.imagePath
            if (path.isNotBlank() && File(path).exists()) {
                coil.compose.AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(path))
                        .crossfade(300)
                        .build(),
                    contentDescription = station.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // 顶部多段式柔和渐变，保证名称可读且过渡自然
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.38f)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black.copy(alpha = 0.48f),
                                0.4f to Color.Black.copy(alpha = 0.26f),
                                0.75f to Color.Black.copy(alpha = 0.09f),
                                1f to Color.Transparent
                            )
                        )
                    )
            )

            Text(
                text = station.name,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = platformLabel(station),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (station.lockerRange.isBlank()) "未设置柜号" else "取件柜 ${station.lockerRange}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
