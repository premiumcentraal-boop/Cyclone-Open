/*
 * Adapted from premiumcentraal-boop/Cyclone @ 1ad783823c79960d9cc09935e8f46253c3823dcc
 * apps/mobile/app/src/main/java/com/cyclone/mobile/ui/overlay/glass/TiltGlass.kt
 * apps/mobile/app/src/main/java/com/cyclone/mobile/ui/overlay/glass/GlassKit.kt
 * apps/mobile/app/src/main/java/com/cyclone/mobile/ui/overlay/glass/GlassTools.kt
 * Proprietary source, owner-authorized reuse. Keeps the glass rim, contrast and tool semantics.
 * Also adapted from premiumcentraal-boop/Cyclone-Cloak @ 62a05e4a966119908125016211096983cb258529
 * android/app/src/main/java/dev/cyclone/cloak/ui/CloakTheme.kt
 * android/app/src/main/java/dev/cyclone/cloak/ui/CloakDesign.kt
 * MIT, Copyright (c) 2026 Cyclone Cloak contributors. See docs/licenses/Cyclone-Cloak-MIT.txt.
 * Uses a single app palette and honors Android's animator duration scale.
 */
package com.cyclone.open.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal object OpenGlass {
    val Mint = Color(0xFFA8E5D2)
    val Background = Color(0xFF10151B)
    val Panel = Color(0xFF202B35)
    val Ink = Color(0xFFEAF5F1)
    val Muted = Color(0xFFBDC9D4)
    private val colors = darkColorScheme(
        primary = Mint,
        onPrimary = Color(0xFF122D26),
        background = Background,
        onBackground = Ink,
        surface = Panel,
        onSurface = Ink,
        surfaceVariant = Color(0xFF283647),
        onSurfaceVariant = Muted,
        secondary = Color(0xFFBECBE9)
    )

    @Composable
    fun Theme(content: @Composable () -> Unit) {
        val resolver = LocalContext.current.contentResolver
        val setting = Settings.Global.ANIMATOR_DURATION_SCALE
        fun reducedMotion() = Settings.Global.getFloat(resolver, setting, 1f) == 0f
        var reduceMotion by remember(resolver) { mutableStateOf(reducedMotion()) }
        DisposableEffect(resolver) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { reduceMotion = reducedMotion() }
            }
            resolver.registerContentObserver(Settings.Global.getUriFor(setting), false, observer)
            onDispose { resolver.unregisterContentObserver(observer) }
        }
        CompositionLocalProvider(LocalReducedMotion provides reduceMotion) {
            MaterialTheme(colorScheme = colors, content = content)
        }
    }
}

internal val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val restShine = GlassOptics.power(GlassOptics.REST_X, GlassOptics.REST_Y)
    val shine by animateFloatAsState(
        if (selected) 1f else 0f,
        animationSpec = if (LocalReducedMotion.current) snap() else tween(180),
        label = "Selection highlight"
    )
    val accent = lerp(OpenGlass.Mint.copy(alpha = restShine * .36f), OpenGlass.Mint, shine)
    Column(
        modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF293843), OpenGlass.Panel, OpenGlass.Background)), shape)
            .border(1.dp, Brush.linearGradient(listOf(accent, Color(0xFF3D4C57))), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
internal fun GlassTool(label: String, target: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = "$label $target" }
    ) {
        Text(label, Modifier.clearAndSetSemantics { })
    }
}
