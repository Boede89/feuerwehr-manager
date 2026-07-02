package de.feuerwehr.einsatzapp

import java.util.concurrent.atomic.AtomicBoolean

object AppForegroundTracker {
    private val inForeground = AtomicBoolean(false)

    fun setInForeground(value: Boolean) {
        inForeground.set(value)
    }

    fun isInForeground(): Boolean = inForeground.get()
}
