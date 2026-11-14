package com.hyper.island.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/** Raiz da janela da ilha: detecta toques fora dela para recolher o painel. */
@SuppressLint("ViewConstructor")
class TouchRootLayout(
    context: Context,
    private val onOutsideTouch: () -> Unit,
) : FrameLayout(context) {

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }
}
