package com.thatgfsj.mypackage.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thatgfsj.mypackage.data.StationEntity
import com.thatgfsj.mypackage.data.StationRepository
import com.thatgfsj.mypackage.ui.components.EmptyState
import com.thatgfsj.mypackage.ui.components.StationActionsDialog
import com.thatgfsj.mypackage.ui.components.StationCard
import com.thatgfsj.mypackage.util.Prefs
import com.thatgfsj.mypackage.util.StationLauncher
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(onGoAdd: () -> Unit, onEdit: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val repo = remember { StationRepository.get(context) }
    val stations by repo.observeAll().collectAsState(initial = null)
    val appearedIds = remember { mutableSetOf<Long>() }
    var menuStation by remember { mutableStateOf<StationEntity?>(null) }

    // 视图模式：0 = 自动（宽屏双列），1 = 单列，2 = 双列。跨启动持久化
    var viewMode by remember { mutableIntStateOf(Prefs.getInt(context, Prefs.KEY_VIEW_MODE, 0)) }

    var titleShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        titleShown = true
    }
    val titleProgress by animateFloatAsState(
        targetValue = if (titleShown) 1f else 0f,
        animationSpec = tween(400),
        label = "title"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val autoTwo = maxWidth >= 600.dp
        val cols = when (viewMode) {
            1 -> 1
            2 -> 2
            else -> if (autoTwo) 2 else 1
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 标题贴着状态栏，右上角视图切换
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            alpha = titleProgress
                            translationY = (1f - titleProgress) * 20f
                        }
                ) {
                    Text(
                        text = "My Packages",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "我的快递",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    val next = if (cols == 2) 1 else 2
                    viewMode = next
                    Prefs.putInt(context, Prefs.KEY_VIEW_MODE, next)
                }) {
                    Icon(
                        imageVector = if (cols == 2) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                        contentDescription = if (cols == 2) "切换单列" else "切换为双列",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val list = stations
            when {
                list == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                list.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Rounded.LocalShipping,
                            text = "还没有快递站，去添加一个吧",
                            actionText = "添加快递站",
                            onAction = onGoAdd
                        )
                    }
                }
                else -> {
                    Crossfade(
                        targetState = cols,
                        animationSpec = tween(220),
                        label = "cols"
                    ) { columns ->
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(list, key = { it.id }) { station ->
                                val index = list.indexOfFirst { it.id == station.id }
                                StationCard(
                                    station = station,
                                    onClick = { StationLauncher.open(context, station) },
                                    onLongClick = { menuStation = station },
                                    alreadyAppeared = station.id in appearedIds,
                                    appearDelayMs = (index * 60).coerceAtMost(360),
                                    onAppeared = { appearedIds.add(station.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    menuStation?.let { target ->
        StationActionsDialog(
            target = target,
            onDismiss = { menuStation = null },
            onEdit = onEdit
        )
    }
}
