package io.github.wingedpanther.cleanmytv.home

import android.service.dreams.DreamService
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * The slideshow as the TV's screensaver, listed as "CleanMyTV Slideshow": the idle screen's photos with the
 * clock, over whatever app is open. The idle screen itself only shows while Home is in front. A DreamService
 * isn't an activity, so it gives Compose the lifecycle an activity would.
 */
class SlidesDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val life = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = life
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        saved.performRestore(null)
        life.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ConfigStore.load(this)
        isInteractive = false
        isFullscreen = true
        life.currentState = Lifecycle.State.STARTED
        setContentView(ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SlidesDream)
            setViewTreeSavedStateRegistryOwner(this@SlidesDream)
            setContent {
                val cfg by ConfigStore.config.collectAsState()
                HomeFont(cfg.font) { SlideshowSaver() }
            }
        })
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        NowPlaying.start(this)
        life.currentState = Lifecycle.State.RESUMED
    }

    override fun onDreamingStopped() {
        life.currentState = Lifecycle.State.STARTED
        NowPlaying.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        life.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }
}
