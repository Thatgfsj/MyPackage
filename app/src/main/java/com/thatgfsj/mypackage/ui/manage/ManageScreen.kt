package com.thatgfsj.mypackage.ui.manage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import com.thatgfsj.mypackage.data.StationEntity
import com.thatgfsj.mypackage.data.StationRepository
import com.thatgfsj.mypackage.ui.components.Capsule
import com.thatgfsj.mypackage.ui.components.EmptyState
import com.thatgfsj.mypackage.ui.components.ScreenHeader
import com.thatgfsj.mypackage.ui.components.StationActionsDialog
import com.thatgfsj.mypackage.ui.components.platformLabel
import com.thatgfsj.mypackage.util.ImageUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ManageScreen(onEdit: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { StationRepository.get(context) }
    val stations by repo.observeAll().collectAsState(initial = emptyList())
    var toDelete by remember { mutableStateOf<StationEntity?>(null) }
    var sortTarget by remember { mutableStateOf<StationEntity?>(null) }
    val appearedIds = remember { mutableSetOf<Long>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ScreenHeader(title = "管理快递站")

        if (stations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.Storefront,
                    text = "还没有快递站，先去添加吧"
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(stations, key = { it.id }) { station ->
                    val index = stations.indexOfFirst { it.id == station.id }
                    ManageRow(
                        station = station,
                        alreadyAppeared = station.id in appearedIds,
                        appearDelayMs = (index * 50).coerceAtMost(300),
                        onAppeared = { appearedIds.add(station.id) },
                        canUp = station.id != stations.first().id,
                        canDown = station.id != stations.last().id,
                        onUp = { scope.launch { repo.moveBy(station.id, -1) } },
                        onDown = { scope.launch { repo.moveBy(station.id, 1) } },
                        onEdit = { onEdit(station.id) },
                        onDelete = { toDelete = station },
                        onLongClick = { sortTarget = station },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    // 长按操作菜单（居中宫格）
    val sortTargetIndex = sortTarget?.let { t -> stations.indexOfFirst { it.id == t.id } + 1 }
    StationActionsDialog(
        target = sortTarget,
        position = sortTargetIndex,
        total = stations.size,
        onDismiss = { sortTarget = null },
        onEdit = onEdit
    )

    toDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("删除快递站？") },
            text = { Text("「${target.name}」及其照片将被删除，此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            ImageUtils.deleteImage(target.imagePath)
                            repo.delete(target)
                            Capsule.show("已删除「${target.name}」", Capsule.Kind.SUCCESS)
                        }
                        toDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManageRow(
    station: StationEntity,
    alreadyAppeared: Boolean,
    appearDelayMs: Int,
    onAppeared: () -> Unit,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
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
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "appear"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "press"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationX = (1f - progress) * 40f
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onEdit,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp, 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (station.imagePath.isNotBlank() && File(station.imagePath).exists()) {
                    AsyncImage(
                        model = File(station.imagePath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    station.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(platformLabel(station))
                        if (station.lockerRange.isNotBlank()) {
                            append(" · ")
                            append(station.lockerRange)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onUp, enabled = canUp) {
                Icon(
                    Icons.Rounded.ArrowUpward,
                    contentDescription = "上移",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDown, enabled = canDown) {
                Icon(
                    Icons.Rounded.ArrowDownward,
                    contentDescription = "下移",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
