package com.hyper.island.island

import com.hyper.island.overlay.IslandGeometry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IslandMode {
    /** Cápsula preta ao redor da câmera. */
    COMPACT,

    /** Cápsula alargada por alguns segundos para um aviso (ex.: carregador conectado). */
    PEEK,

    /** Painel aberto ao tocar. */
    EXPANDED,
}

/** Estado observável da ilha, compartilhado entre o controlador da janela e a UI. */
class IslandStateHolder(initialGeometry: IslandGeometry) {
    private val _mode = MutableStateFlow(IslandMode.COMPACT)
    val mode: StateFlow<IslandMode> = _mode.asStateFlow()

    private val _geometry = MutableStateFlow(initialGeometry)
    val geometry: StateFlow<IslandGeometry> = _geometry.asStateFlow()

    fun setMode(mode: IslandMode) {
        _mode.value = mode
    }

    fun setGeometry(geometry: IslandGeometry) {
        _geometry.value = geometry
    }
}

interface IslandActions {
    fun onTap()
    fun onLongPress()

    /** Chamado quando a animação de tamanho termina; a janela é ajustada ao tamanho final. */
    fun onAnimationSettled()
}
