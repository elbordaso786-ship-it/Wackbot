package com.liam.whackbot

/** Shared switches. Auto mode is OFF every time the app starts. */
object Control {
    @Volatile var autoEnabled = false
    @Volatile var running = false
}
