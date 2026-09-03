package io.legado.app.help.exoplayer

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import com.google.gson.reflect.TypeToken
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import okhttp3.CacheControl
import splitties.init.appCtx
import java.io.File
import java.util.concurrent.TimeUnit


@Suppress("unused")
@SuppressLint("UnsafeOptInUsageError")
object ExoPlayerHelper {

    private const val SPLIT_TAG = "\uD83D\uDEA7"

    private val mapType by lazy {
        object : TypeToken<Map<String, String>>() {}.type
    }

    fun createMediaItem(
        url: String,
        headers: Map<String, String>,
        customCacheKey: String? = null,
    ): MediaItem {
        val formatUrl = url + SPLIT_TAG + GSON.toJson(headers, mapType)
        return MediaItem.Builder()
            .setUri(formatUrl)
            .setCustomCacheKey(customCacheKey)
            .build()
    }

    fun createHttpExoPlayer(context: Context): ExoPlayer {
        return ExoPlayer.Builder(context).setLoadControl(
            DefaultLoadControl.Builder().setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS / 10,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 10
            ).build()
        ).setMediaSourceFactory(
            DefaultMediaSourceFactory(
                context,
                DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
            ).setDataSourceFactory(resolvingDataSource)
                .setLiveTargetOffsetMs(5000)
        ).build()
    }


    private val resolvingDataSource: ResolvingDataSource.Factory by lazy {
        ResolvingDataSource.Factory(audioReadThroughDataSourceFactory, ::resolveDataSpec)
    }


    /**
     * 支持缓存的DataSource.Factory
     */
    val cacheDataSourceFactory by lazy {
        //使用自定义的CacheDataSource以支持设置UA
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(okhttpDataFactory)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(cache)
                    .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE)
            )
    }

    /**
     * 手动下载的有声书缓存必须长期保留，不能进入播放器 100M LRU 临时缓存。
     */
    private val audioDownloadCache: Cache by lazy {
        SimpleCache(
            File(appCtx.externalFiles, "audio_cache"),
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(appCtx),
        )
    }

    private val audioDownloadDataSourceFactory by lazy {
        CacheDataSource.Factory()
            .setCache(audioDownloadCache)
            .setUpstreamDataSourceFactory(okhttpDataFactory)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(audioDownloadCache)
                    .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE)
            )
    }

    /**
     * 播放优先读取完整的手动下载缓存，缺口继续走原 100M 临时缓存与网络。
     * 播放过程不会反向写入手动下载缓存。
     */
    private val audioReadThroughDataSourceFactory by lazy {
        CacheDataSource.Factory()
            .setCache(audioDownloadCache)
            .setUpstreamDataSourceFactory(cacheDataSourceFactory)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setCacheWriteDataSinkFactory(null)
    }

    /**
     * Okhttp DataSource.Factory
     */
    private val okhttpDataFactory by lazy {
        val client = okHttpClient.newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
        OkHttpDataSource.Factory(client)
            .setCacheControl(CacheControl.Builder().maxAge(1, TimeUnit.DAYS).build())
    }

    /**
     * Exoplayer 内置的缓存
     */
    private val cache: Cache by lazy {
        val databaseProvider = StandaloneDatabaseProvider(appCtx)
        return@lazy SimpleCache(
            //Exoplayer的缓存路径
            File(appCtx.externalCache, "exoplayer"),
            //100M的缓存
            LeastRecentlyUsedCacheEvictor((100 * 1024 * 1024).toLong()),
            //记录缓存的数据库
            databaseProvider
        )
    }

    /**
     * 通过kotlin扩展函数+反射实现CacheDataSource.Factory设置默认请求头
     * 需要添加混淆规则 -keepclassmembers class com.google.android.exoplayer2.upstream.cache.CacheDataSource$Factory{upstreamDataSourceFactory;}
     * @param headers
     * @return
     */
//    private fun CacheDataSource.Factory.setDefaultRequestProperties(headers: Map<String, String> = mapOf()): CacheDataSource.Factory {
//        val declaredField = this.javaClass.getDeclaredField("upstreamDataSourceFactory")
//        declaredField.isAccessible = true
//        val df = declaredField[this] as DataSource.Factory
//        if (df is OkHttpDataSource.Factory) {
//            df.setDefaultRequestProperties(headers)
//        }
//        return this
//    }


    fun getMediaSource(mediaItems: List<MediaItem>): MediaSource? {
        if (mediaItems.isEmpty()) return null
        val dataSourceFactory: DataSource.Factory = resolvingDataSource
        val mediaSourceBuilder = ConcatenatingMediaSource2.Builder()
        for (mediaItem in mediaItems) {
            mediaSourceBuilder.add(
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem), 3000
            )
        }
        return mediaSourceBuilder.build()
    }

    fun createAudioCacheWriter(
        mediaItem: MediaItem,
        progressListener: CacheWriter.ProgressListener? = null,
    ): CacheWriter {
        val localConfiguration = requireNotNull(mediaItem.localConfiguration)
        val cacheKey = requireNotNull(localConfiguration.customCacheKey)
        val dataSpec = resolveDataSpec(
            DataSpec.Builder()
                .setUri(localConfiguration.uri)
                .setKey(cacheKey)
                .build()
        )
        return CacheWriter(
            audioDownloadDataSourceFactory.createDataSourceForDownloading(),
            dataSpec,
            null,
            progressListener,
        )
    }

    fun audioCacheContentLength(cacheKey: String): Long =
        ContentMetadata.getContentLength(audioDownloadCache.getContentMetadata(cacheKey))

    fun audioCacheContiguousLength(cacheKey: String): Long {
        var end = 0L
        for (span in audioDownloadCache.getCachedSpans(cacheKey)) {
            if (span.position > end) break
            end = maxOf(end, span.position + span.length)
        }
        return end
    }

    fun isAudioCacheComplete(cacheKey: String, length: Long): Boolean =
        length > 0L && audioDownloadCache.isCached(cacheKey, 0L, length)

    fun removeAudioCache(cacheKeys: Collection<String>) {
        cacheKeys.forEach(audioDownloadCache::removeResource)
    }

    fun removeAudioCacheByPrefix(prefix: String) {
        audioDownloadCache.keys
            .filter { it.startsWith(prefix) }
            .forEach(audioDownloadCache::removeResource)
    }

    fun clearAudioCache() {
        audioDownloadCache.keys.toList().forEach(audioDownloadCache::removeResource)
    }

    private fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val encodedUrl = dataSpec.uri.toString()
        val splitIndex = encodedUrl.indexOf(SPLIT_TAG)
        if (splitIndex < 0) return dataSpec
        val url = encodedUrl.substring(0, splitIndex)
        val headers = runCatching {
            GSON.fromJson<Map<String, String>>(
                encodedUrl.substring(splitIndex + SPLIT_TAG.length),
                mapType,
            )
        }.getOrNull().orEmpty()
        return dataSpec
            .withUri(Uri.parse(url))
            .withRequestHeaders(headers)
    }
}
