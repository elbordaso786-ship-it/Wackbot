package com.liam.whackbot.core

data class TapCommand(val holeId: Int, val label: Label, val score: Float, val refX: Int, val refY: Int)

/** Debounce / lock logic. Only ever returns taps for holes whose verdict is State.TAP. */
class TapPlanner(private val cfg: Config = Config()) {
    private val n = cfg.holes.size
    private val confirm = IntArray(n)
    private val emptyStreak = IntArray(n)
    private val lockedUntil = LongArray(n)
    private var lastTapMs = Long.MIN_VALUE / 2

    fun plan(evals: List<HoleEval>, nowMs: Long): TapCommand? {
        var best: HoleEval? = null
        for ((i, e) in evals.withIndex()) {
            if (e.state == State.EMPTY) {
                emptyStreak[i]++
                if (emptyStreak[i] >= cfg.rearmEmptyFrames) lockedUntil[i] = 0
            } else emptyStreak[i] = 0
            if (e.state == State.TAP && nowMs >= lockedUntil[i]) confirm[i]++ else confirm[i] = 0
            if (confirm[i] >= cfg.confirmFrames && (best == null || e.pos > best.pos)) best = e
        }
        val b = best ?: return null
        if (nowMs - lastTapMs < cfg.minGapMs) return null
        val i = cfg.holes.indexOfFirst { it.id == b.hole.id }
        lockedUntil[i] = nowMs + cfg.lockMs
        confirm[i] = 0
        lastTapMs = nowMs
        return TapCommand(b.hole.id, b.best!!, b.pos, b.cx, b.cy)
    }

    fun reset() { confirm.fill(0); emptyStreak.fill(0); lockedUntil.fill(0) }
}
