package com.thatgfsj.mypackage.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 灵动岛风格的状态胶囊：点击卡片跳转时在屏幕顶部展示操作状态 */
object Capsule {

    enum class Kind { OPENING, SUCCESS, FALLBACK }

    data class State(val text: String, val kind: Kind, val seq: Long)

    private val _state = mutableStateOf<State?>(null)
    val state: State? get() = _state.value

    fun show(text: String, kind: Kind) {
        _state.value = State(text, kind, System.nanoTime())
    }

    fun clear() {
        _state.value = null
    }
}

@Composable
fun CapsuleOverlay(modifier: Modifier = Modifier) {
    val st = Capsule.state
    var last by remember { mutableStateOf<Capsule.State?>(null) }
    LaunchedEffect(st) {
        if (st != null) {
            last = st
            delay(2600)
            Capsule.clear()
        }
    }

    AnimatedVisibility(
        visible = st != null,
        modifier = modifier,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            initialOffsetY = { -it }
        ) + scaleIn(
            initialScale = 0.6f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(tween(120)),
        exit = scaleOut(
            targetScale = 0.85f,
            animationSpec = tween(180)
        ) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)) +
            fadeOut(tween(150))
    ) {
        val shown = st ?: last
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xF0101010),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (shown?.kind == Capsule.Kind.OPENING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF35E0C8)
                    )
                } else {
                    val icon = when (shown?.kind) {
                        Capsule.Kind.FALLBACK -> Icons.Rounded.Link
                        else -> Icons.Rounded.Check
                    }
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color(0xFF35E0C8),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    shown?.text.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
