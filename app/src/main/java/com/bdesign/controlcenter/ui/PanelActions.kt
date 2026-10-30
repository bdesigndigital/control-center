package com.bdesign.controlcenter.ui

import android.content.Intent

/** Ce que le panneau peut demander au service (qui possède la fenêtre). */
interface PanelActions {
    /** Ferme le panneau. */
    fun close()

    /** Lance un écran système ou une app, puis ferme le panneau. */
    fun launch(intent: Intent)

    /** Ferme le panneau puis exécute une action globale d'accessibilité. */
    fun global(action: Int)
}
