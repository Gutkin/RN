package com.gutkin.frame.video

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class PreviewView(val reactContext: ThemedReactContext) : PlayerView(reactContext), LifecycleEventListener {
    private var engine: ExoPlayer? = null
    init {
        useController = true
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        setShowBuffering(SHOW_BUFFERING_WHEN_PLAYING)
        reactContext.addLifecycleEventListener(this)
    }
    fun source(value: ReadableMap?) {
        engine?.release(); engine = null; player = null
        if (value == null) return
        val uri = Uri.parse(value.getString("uri"))
        if (uri.scheme != "file" || !java.io.File(uri.path ?: "").canonicalPath.startsWith(reactContext.filesDir.canonicalPath + "/media/")) return
        val item = MediaItem.Builder().setUri(uri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(value.getDouble("startMs").toLong()).setEndPositionMs(value.getDouble("endMs").toLong()).build()
        ).build()
        engine = ExoPlayer.Builder(reactContext).build().also {
            player = it
            it.setVideoEffects(VideoStyle.effects(value.getString("style") ?: "journal", value.getString("title") ?: ""))
            it.setMediaItem(item); it.repeatMode = Player.REPEAT_MODE_ONE
            it.volume = if (value.getBoolean("muted")) 0f else 1f
            it.prepare(); it.playWhenReady = false
        }
    }
    override fun onHostPause() { engine?.pause() }
    override fun onHostResume() {}
    override fun onHostDestroy() { dispose() }
    fun dispose() { engine?.release(); engine = null; player = null; reactContext.removeLifecycleEventListener(this) }
}

class VideoPreviewManager : SimpleViewManager<PreviewView>() {
    override fun getName() = "FrameVideoPreview"
    override fun createViewInstance(context: ThemedReactContext) = PreviewView(context)
    @ReactProp(name = "source") fun setSource(view: PreviewView, value: ReadableMap?) = view.source(value)
    override fun onDropViewInstance(view: PreviewView) { view.dispose(); super.onDropViewInstance(view) }
}
