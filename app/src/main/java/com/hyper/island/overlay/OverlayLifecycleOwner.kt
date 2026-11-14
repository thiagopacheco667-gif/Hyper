package com.hyper.island.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * O Compose exige um LifecycleOwner na árvore de views. Fora de uma Activity
 * (aqui, dentro de um serviço) nós mesmos fornecemos um.
 */
class OverlayLifecycleOwner : SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onResume() = registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

    fun onDestroy() {
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    fun attachTo(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }
}
