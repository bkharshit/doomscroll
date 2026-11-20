package com.doomscroll.android.ui.components

import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shows the real launcher icon of an installed app, read from its APK through [PackageManager].
 *
 * This works whether or not the app is running (force-stopped included): it only reads the
 * package's resources and needs package visibility, which the manifest's `<queries>` grants.
 * While the icon loads, or if the app isn't installed, [fallback] is drawn instead.
 */
@Composable
fun InstalledAppIcon(
    packageName: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val key = "$packageName@$sizePx"
    val icon by produceState(initialValue = iconCache.get(key), key) {
        if (value == null && packageName != null) {
            value = withContext(Dispatchers.IO) {
                loadIcon(context.packageManager, packageName, sizePx)
            }?.also { iconCache.put(key, it) }
        }
    }

    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        fallback()
    }
}

// A handful of monitored apps at two or three sizes; small enough to keep for the process lifetime.
private val iconCache = LruCache<String, ImageBitmap>(16)

private fun loadIcon(pm: PackageManager, packageName: String, sizePx: Int): ImageBitmap? =
    try {
        // Adaptive icons draw through the device's own mask, so the shape matches the launcher.
        pm.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
