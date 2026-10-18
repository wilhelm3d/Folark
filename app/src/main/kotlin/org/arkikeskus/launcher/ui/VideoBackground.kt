package org.arkikeskus.launcher.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.io.File

/** Home background files that are videos (picked in Settings ▸ Home backdrop ▸ Video background). */
internal fun isVideoBackground(path: String): Boolean =
    path.substringAfterLast('.', "").lowercase() in VIDEO_TYPES

/**
 * A looping, silent video behind this display's home screen (each display can have its own, or
 * none). It plays only while [playing] (home in front and not covered, battery saver off) and is
 * cropped to fill the screen like a picture background.
 */
@Composable
internal fun VideoBackground(path: String, playing: Boolean, modifier: Modifier = Modifier) {
    val holder = remember(path) { VideoHolder(File(path)) }
    DisposableEffect(holder) { onDispose { holder.release() } }
    // Home in front: another app over it pauses the video.
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(holder) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    LaunchedEffect(holder, playing && resumed) { holder.setPlaying(playing && resumed) }
    AndroidView(
        factory = { context -> TextureView(context).also { holder.attach(it) } },
        modifier = modifier,
    )
}

private class VideoHolder(private val file: File) : TextureView.SurfaceTextureListener {
    private var view: TextureView? = null
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var wantPlaying = false
    private var prepared = false

    fun attach(textureView: TextureView) {
        view = textureView
        textureView.surfaceTextureListener = this
        textureView.surfaceTexture?.let { start(it) }
    }

    fun setPlaying(playing: Boolean) {
        wantPlaying = playing
        val p = player ?: return
        if (!prepared) return
        runCatching { if (playing && !p.isPlaying) p.start() else if (!playing && p.isPlaying) p.pause() }
    }

    private fun start(texture: SurfaceTexture) {
        if (player != null || !file.exists()) return
        val s = Surface(texture)
        surface = s
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setSurface(s)
                isLooping = true
                setVolume(0f, 0f)
                setOnVideoSizeChangedListener { _, w, h -> crop(w, h) }
                setOnPreparedListener {
                    prepared = true
                    crop(it.videoWidth, it.videoHeight)
                    if (wantPlaying) it.start() else it.seekTo(0)
                }
                prepareAsync()
            }
        }.getOrNull()
    }

    /** Center-crop: the video fills the view, cut at the sides or top and bottom. */
    private fun crop(videoW: Int, videoH: Int) {
        val v = view ?: return
        val viewW = v.width.toFloat()
        val viewH = v.height.toFloat()
        if (videoW <= 0 || videoH <= 0 || viewW <= 0f || viewH <= 0f) return
        val scale = maxOf(viewW / videoW, viewH / videoH)
        val sx = videoW * scale / viewW
        val sy = videoH * scale / viewH
        v.setTransform(Matrix().apply { setScale(sx, sy, viewW / 2f, viewH / 2f) })
    }

    fun release() {
        runCatching { player?.release() }
        player = null
        prepared = false
        surface?.release()
        surface = null
        view?.surfaceTextureListener = null
        view = null
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = start(texture)

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        player?.takeIf { prepared }?.let { crop(it.videoWidth, it.videoHeight) }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        runCatching { player?.release() }
        player = null
        prepared = false
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
}

private val VIDEO_TYPES = setOf("mp4", "webm", "mkv", "3gp", "mov")
