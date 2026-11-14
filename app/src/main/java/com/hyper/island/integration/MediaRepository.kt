package com.hyper.island.integration

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.roundToInt

data class MediaInfo(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    /** Cor de destaque tirada da capa (barras e brilho do painel, como no HyperOS). */
    val accentColor: Int?,
    val isPlaying: Boolean,
    val durationMs: Long,
    /** Posição informada pelo player no instante [positionUpdatedAt] (elapsedRealtime). */
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val speed: Float,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val canSeek: Boolean,
) {
    /** Posição atual estimada sem consultar o player (sem polling). */
    fun currentPosition(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        if (!isPlaying || positionUpdatedAt <= 0) return positionMs.coerceAtLeast(0)
        val p = positionMs + ((nowElapsed - positionUpdatedAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else max(0, p)
    }
}

/**
 * Lê o player ativo pelas MediaSessions do sistema. Só funciona com o acesso a
 * notificações concedido (o Android exige o componente do listener).
 * Tudo é por eventos: nenhuma consulta periódica.
 */
object MediaRepository {
    private const val ART_PX = 256

    private val handler = Handler(Looper.getMainLooper())
    private val _media = MutableStateFlow<MediaInfo?>(null)
    val media: StateFlow<MediaInfo?> = _media.asStateFlow()

    private val _anyPlaying = MutableStateFlow(false)

    /** Algum player (música ou vídeo) está tocando agora, em qualquer app. */
    val anyPlaying: StateFlow<Boolean> = _anyPlaying.asStateFlow()

    private var appContext: Context? = null
    private var manager: MediaSessionManager? = null
    private var listenerComponent: ComponentName? = null
    private val callbacks = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var active: MediaController? = null

    // Cache da capa reduzida: evita redimensionar a cada mudança de estado.
    private var artSource: Bitmap? = null
    private var artScaled: Bitmap? = null
    private var artAccent: Int? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateControllers(controllers.orEmpty())
    }

    fun start(context: Context, component: ComponentName) {
        if (manager != null) return
        appContext = context.applicationContext
        listenerComponent = component
        val msm = context.getSystemService(MediaSessionManager::class.java) ?: return
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            manager = msm
            updateControllers(msm.getActiveSessions(component))
        } catch (e: SecurityException) {
            // Acesso a notificações revogado.
            manager = null
        }
    }

    fun stop() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        manager = null
        updateControllers(emptyList())
    }

    fun playPause() {
        val c = active ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = active?.transportControls?.skipToNext()

    fun previous() = active?.transportControls?.skipToPrevious()

    fun seekTo(positionMs: Long) = active?.transportControls?.seekTo(positionMs)

    private fun updateControllers(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        // Remove sessões que sumiram.
        callbacks.keys.filter { it !in tokens }.forEach { token ->
            callbacks.remove(token)?.let { (controller, cb) -> controller.unregisterCallback(cb) }
        }
        // Registra as novas.
        controllers.forEach { controller ->
            val token = controller.sessionToken
            if (token !in callbacks) {
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = pickActive()
                    override fun onMetadataChanged(metadata: MediaMetadata?) = pickActive()
                    override fun onSessionDestroyed() {
                        callbacks.remove(token)?.let { (c, me) -> c.unregisterCallback(me) }
                        pickActive()
                    }
                }
                controller.registerCallback(cb, handler)
                callbacks[token] = controller to cb
            }
        }
        pickActive()
    }

    private fun pickActive() {
        val controllers = callbacks.values.map { it.first }
        active = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING && it.metadata != null }
            ?: active?.takeIf { a -> controllers.any { it.sessionToken == a.sessionToken } && a.metadata != null }
            ?: controllers.firstOrNull { it.metadata != null }
        _media.value = active?.let(::buildInfo)
        _anyPlaying.value = controllers.any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
    }

    private fun buildInfo(controller: MediaController): MediaInfo? {
        val metadata = controller.metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return null
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val state = controller.playbackState
        val actions = state?.actions ?: 0L
        val context = appContext
        return MediaInfo(
            packageName = controller.packageName,
            appLabel = context?.let { AppInfoCache.label(it, controller.packageName) } ?: controller.packageName,
            title = title,
            artist = artist,
            art = scaledArt(metadata),
            accentColor = artAccent,
            isPlaying = state?.state == PlaybackState.STATE_PLAYING,
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            positionMs = state?.position ?: 0L,
            positionUpdatedAt = state?.lastPositionUpdateTime ?: 0L,
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
        )
    }

    private fun scaledArt(metadata: MediaMetadata): Bitmap? {
        val source = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: run {
                artSource = null
                artScaled = null
                artAccent = null
                return null
            }
        if (source === artSource) return artScaled
        val scale = ART_PX.toFloat() / max(source.width, source.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).roundToInt().coerceAtLeast(1),
                (source.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            source
        }
        artSource = source
        artScaled = scaled
        artAccent = runCatching { accentOf(scaled) }.getOrNull()
        return scaled
    }

    /** Cor mais viva da capa, clareada para aparecer sobre o preto. */
    private fun accentOf(bitmap: Bitmap): Int? {
        val software = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val small = Bitmap.createScaledBitmap(software, 12, 12, true)
        val hsv = FloatArray(3)
        var best: FloatArray? = null
        var bestScore = 0f
        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                Color.colorToHSV(small.getPixel(x, y), hsv)
                val score = hsv[1] * hsv[2]
                if (score > bestScore) {
                    bestScore = score
                    best = hsv.copyOf()
                }
            }
        }
        val chosen = best?.takeIf { bestScore > 0.12f } ?: return null
        chosen[1] = chosen[1].coerceIn(0.35f, 0.85f)
        chosen[2] = chosen[2].coerceAtLeast(0.75f)
        return Color.HSVToColor(chosen)
    }
}
