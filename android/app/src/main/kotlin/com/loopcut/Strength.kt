/**
 * MIT License
 * LoopCut Mini - Negative Speech Detection App
 * Copyright (c) 2025
 */
package com.loopcut

enum class Strength(val waveform: LongArray) {
    WEAK(longArrayOf(0, 250, 750, 250, 750, 250)),
    MEDIUM(longArrayOf(0, 500, 500, 500, 500, 500)),
    STRONG(longArrayOf(0, 700, 300, 700, 300, 700))
}
