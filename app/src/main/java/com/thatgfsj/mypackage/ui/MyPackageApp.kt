package com.thatgfsj.mypackage.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thatgfsj.mypackage.ui.addedit.AddEditScreen
import com.thatgfsj.mypackage.ui.components.CapsuleOverlay
import com.thatgfsj.mypackage.ui.home.HomeScreen
import com.thatgfsj.mypackage.ui.importscan.ImportScreen
import com.thatgfsj.mypackage.ui.manage.ManageScreen
import com.thatgfsj.mypackage.ui.settings.SettingsScreen
import com.thatgfsj.mypackage.ui.share.ShareScreen

@Composable
fun MyPackageApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val topLevel = currentRoute == "home" || currentRoute == "settings"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 各页面自行处理状态栏留白（statusBarsPadding），Scaffold 不再叠加，避免标题距状态栏过远
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            AnimatedVisibility(
                visible = topLevel,
                enter = slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it } +
                    fadeIn(tween(200)),
                exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it } +
                    fadeOut(tween(150))
            ) {
                // 两个独立圆角按钮；上 8dp / 下 12dp（约 150%）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BottomTabButton(
                        icon = Icons.Rounded.LocalShipping,
                        label = "快递站",
                        selected = currentRoute == "home",
                        modifier = Modifier.weight(1f)
                    ) {
                        navController.navigate("home") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    BottomTabButton(
                        icon = Icons.Rounded.Settings,
                        label = "设置",
                        selected = currentRoute == "settings",
                        modifier = Modifier.weight(1f)
                    ) {
                        navController.navigate("settings") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = "home",
                enterTransition = {
                    fadeIn(tween(240)) +
                        slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 5 }
                },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(240)) },
                popExitTransition = {
                    fadeOut(tween(180)) +
                        slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 5 }
                }
            ) {
                composable("home") {
                    HomeScreen(
                        onGoAdd = { navController.navigate("add") },
                        onEdit = { navController.navigate("edit/$it") }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        onAdd = { navController.navigate("add") },
                        onManage = { navController.navigate("manage") },
                        onShare = { navController.navigate("share") },
                        onImportScan = { navController.navigate("import") }
                    )
                }
                composable("add") {
                    AddEditScreen(stationId = null, onDone = { navController.popBackStack() })
                }
                composable(
                    route = "edit/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { entry ->
                    AddEditScreen(
                        stationId = entry.arguments?.getLong("id"),
                        onDone = { navController.popBackStack() }
                    )
                }
                composable("manage") {
                    ManageScreen(onEdit = { navController.navigate("edit/$it") })
                }
                composable("share") {
                    ShareScreen()
                }
                composable("import") {
                    ImportScreen()
                }
            }
            CapsuleOverlay(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp)
            )
        }
    }
}

/** 底部导航按钮：独立胶囊造型，选中/未选中颜色与阴影平滑过渡 */
@Composable
private fun BottomTabButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200),
        label = "bg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "fg"
    )
    val shadow by animateDpAsState(
        targetValue = if (selected) 4.dp else 2.dp,
        label = "shadow"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "scale"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        shadowElevation = shadow,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
        }
    }
}
