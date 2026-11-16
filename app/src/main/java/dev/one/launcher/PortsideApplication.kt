package dev.one.launcher

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Application class (v1.8.7): owns the Coil ImageLoader so cover art
 * is cached properly. The default ImageLoader uses a small memory
 * cache and no named disk cache, and AsyncImage loads raw URL strings
 * — the poster and the detail header load the same cover URL at
 * different sizes, so Coil missed the memory cache when opening a
 * game page and showed a blank grey box for a frame. This loader
 * gives the memory cache a quarter of the app's memory, a 150 MB
 * disk cache for cover art, RGB_565 (covers don't need alpha), and a
 * short crossfade. The detail headers reuse the poster's memory
 * cache key as a placeholder (see OneApp), so the art appears
 * instantaneously at 0 ms while the higher-res crop settles.
 */
class PortsideApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("cover_art_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .allowRgb565(true)
            .crossfade(150)
            .build()
    }
}
