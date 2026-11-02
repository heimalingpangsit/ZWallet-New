package com.zwallet.app.ui

import android.content.Context
import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hasil pemuatan aset: bitmap null berarti file tidak ada di assets. */
class LoadResult(val bitmap: ImageBitmap?)

/** Memuat gambar dari folder assets (png/webp/jpg) dengan downsample dan cache memori. */
object AssetImages {
    private val cache = HashMap<String, ImageBitmap>()
    private val missing = HashSet<String>()
    private val exts = listOf("png", "webp", "jpg", "jpeg")

    private fun key(dir: String, name: String, maxWidth: Int) = "$dir/$name@$maxWidth"

    fun peek(dir: String, name: String, maxWidth: Int): LoadResult? {
        val k = key(dir, name, maxWidth)
        synchronized(this) {
            val hit = cache[k]
            if (hit != null) return LoadResult(hit)
            if (k in missing) return LoadResult(null)
        }
        return null
    }

    fun load(context: Context, dir: String, name: String, maxWidth: Int): ImageBitmap? {
        val k = key(dir, name, maxWidth)
        synchronized(this) {
            cache[k]?.let { return it }
            if (k in missing) return null
        }
        val am = context.applicationContext.assets
        for (ext in exts) {
            val bmp = try { decode(am, "$dir/$name.$ext", maxWidth) } catch (e: Exception) { null }
            if (bmp != null) {
                synchronized(this) { cache[k] = bmp }
                return bmp
            }
        }
        synchronized(this) { missing.add(k) }
        return null
    }

    private fun decode(am: AssetManager, path: String, maxWidth: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        am.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return am.open(path).use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
    }
}

@Composable
fun rememberAssetImage(dir: String, name: String, maxWidth: Int): State<LoadResult?> {
    val context = LocalContext.current.applicationContext
    return produceState<LoadResult?>(AssetImages.peek(dir, name, maxWidth), dir, name, maxWidth) {
        if (value == null) {
            value = LoadResult(withContext(Dispatchers.IO) { AssetImages.load(context, dir, name, maxWidth) })
        }
    }
}

/**
 * Gambar kartu asli dari assets/cards/<assetId>, rasio 1.586:1, ContentScale.Fit.
 * assetId null atau file tidak ada -> placeholder hitam-emas.
 */
@Composable
fun CardImage(assetId: String?, name: String, modifier: Modifier = Modifier, maxWidth: Int = 1000) {
    val result by rememberAssetImage("cards", assetId ?: "_none", maxWidth)
    Box(modifier.aspectRatio(1.586f), contentAlignment = Alignment.Center) {
        val loaded = result
        val bmp = loaded?.bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else if (loaded != null) {
            CardPlaceholder(name)
        }
    }
}

@Composable
private fun CardPlaceholder(name: String) {
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(ZCard2)
            .border(1.dp, ZGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.CreditCard, null, tint = ZGold, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(8.dp))
        Text(name, color = ZText, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Gambar kartu belum tersedia", color = ZMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}
