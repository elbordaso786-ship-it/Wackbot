package com.liam.whackbot.core

import kotlin.math.abs
import kotlin.math.sqrt

enum class State { EMPTY, ARTIFACT, RISING, UNKNOWN, AVOID, TAP }

class Blob(
    val gx0: Int, val gy0: Int, val gx1: Int, val gy1: Int,
    val cells: Int, val comp: BooleanArray, val gw: Int, val gh: Int,
)

data class HoleEval(
    val hole: Hole,
    val state: State,
    val best: Label? = null,
    val pos: Float = 0f,
    val neg: Float = 0f,
    val bx0: Int = 0, val by0: Int = 0, val bx1: Int = 0, val by1: Int = 0,
    val scores: Map<Label, Float> = emptyMap(),
) {
    val cx get() = (bx0 + bx1) / 2
    val cy get() = (by0 + by1) / 2
}

class TemplateSet(val items: List<Pair<Label, FloatArray>>)

const val D = 32

fun ncc(a: FloatArray, b: FloatArray): Float {
    var ma = 0.0; var mb = 0.0
    for (i in a.indices) { ma += a[i]; mb += b[i] }
    ma /= a.size; mb /= a.size
    var dot = 0.0; var na = 0.0; var nb = 0.0
    for (i in a.indices) {
        val x = a[i] - ma; val y = b[i] - mb
        dot += x * y; na += x * x; nb += y * y
    }
    val den = sqrt(na * nb)
    return if (den < 1e-6) 0f else (dot / den).toFloat()
}

class Detector(
    private val ref: PixelSource,
    private val templates: TemplateSet,
    val cfg: Config = Config(),
) {
    private fun fpx(frame: PixelSource, rx: Int, ry: Int): Int {
        val x = (rx.toLong() * frame.width / Ref.W).toInt().coerceIn(0, frame.width - 1)
        val y = (ry.toLong() * frame.height / Ref.H).toInt().coerceIn(0, frame.height - 1)
        return frame.rgb(x, y)
    }

    private fun maxDiff(a: Int, b: Int): Int = maxOf(
        abs(((a shr 16) and 255) - ((b shr 16) and 255)),
        abs(((a shr 8) and 255) - ((b shr 8) and 255)),
        abs((a and 255) - (b and 255)),
    )

    /** Largest 8-connected region of pixels that differ from the empty-board reference. */
    fun segment(frame: PixelSource, hole: Hole): Blob? {
        val s = cfg.step
        val gw = (hole.x1 - hole.x0) / s
        val gh = (hole.y1 - hole.y0) / s
        val n = gw * gh
        val m = BooleanArray(n)
        for (gy in 0 until gh) for (gx in 0 until gw) {
            val rx = hole.x0 + gx * s; val ry = hole.y0 + gy * s
            m[gy * gw + gx] = maxDiff(fpx(frame, rx, ry), ref.rgb(rx, ry)) > cfg.diffThreshold
        }
        val visited = BooleanArray(n)
        val stack = IntArray(n)
        var bestStart = -1; var bestCount = 0
        for (st in 0 until n) {
            if (!m[st] || visited[st]) continue
            var sp = 0; stack[sp++] = st; visited[st] = true; var cnt = 0
            while (sp > 0) {
                val c = stack[--sp]; cnt++
                val cx = c % gw; val cy = c / gw
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = cx + dx; val ny = cy + dy
                    if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) continue
                    val ni = ny * gw + nx
                    if (m[ni] && !visited[ni]) { visited[ni] = true; stack[sp++] = ni }
                }
            }
            if (cnt > bestCount) { bestCount = cnt; bestStart = st }
        }
        if (bestCount < cfg.minCells) return null
        val comp = BooleanArray(n)
        var sp = 0; stack[sp++] = bestStart; comp[bestStart] = true
        var x0 = gw; var y0 = gh; var x1 = 0; var y1 = 0
        while (sp > 0) {
            val c = stack[--sp]
            val cx = c % gw; val cy = c / gw
            if (cx < x0) x0 = cx; if (cx > x1) x1 = cx
            if (cy < y0) y0 = cy; if (cy > y1) y1 = cy
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = cx + dx; val ny = cy + dy
                if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) continue
                val ni = ny * gw + nx
                if (m[ni] && !comp[ni]) { comp[ni] = true; stack[sp++] = ni }
            }
        }
        return Blob(x0, y0, x1, y1, bestCount, comp, gw, gh)
    }

    /** Masked 32x32 colour descriptor of the blob (background pixels are black). */
    fun descriptor(frame: PixelSource, hole: Hole, b: Blob): FloatArray {
        val sum = FloatArray(D * D * 3); val cnt = IntArray(D * D)
        val bw = b.gx1 - b.gx0 + 1; val bh = b.gy1 - b.gy0 + 1
        for (gy in b.gy0..b.gy1) for (gx in b.gx0..b.gx1) {
            val bin = ((gy - b.gy0) * D / bh) * D + ((gx - b.gx0) * D / bw)
            cnt[bin]++
            if (b.comp[gy * b.gw + gx]) {
                val p = fpx(frame, hole.x0 + gx * cfg.step, hole.y0 + gy * cfg.step)
                sum[bin * 3] += ((p shr 16) and 255).toFloat()
                sum[bin * 3 + 1] += ((p shr 8) and 255).toFloat()
                sum[bin * 3 + 2] += (p and 255).toFloat()
            }
        }
        for (i in 0 until D * D) if (cnt[i] > 0) for (c in 0..2) sum[i * 3 + c] /= cnt[i]
        return sum
    }

    fun evalHole(frame: PixelSource, hole: Hole): HoleEval {
        val b = segment(frame, hole) ?: return HoleEval(hole, State.EMPTY)
        val s = cfg.step
        val bx0 = hole.x0 + b.gx0 * s; val bx1 = hole.x0 + (b.gx1 + 1) * s
        val by0 = hole.y0 + b.gy0 * s; val by1 = hole.y0 + (b.gy1 + 1) * s
        val w = bx1 - bx0; val h = by1 - by0
        fun mk(st: State, best: Label? = null, pos: Float = 0f, neg: Float = 0f, sc: Map<Label, Float> = emptyMap()) =
            HoleEval(hole, st, best, pos, neg, bx0, by0, bx1, by1, sc)
        // Objects never touch the left/right/top edge of the hole's search box; overlays (volume slider etc.) do.
        if (b.gx0 <= 1 || b.gx1 >= b.gw - 2 || b.gy0 <= 0) return mk(State.ARTIFACT)
        if (w < cfg.minW || w > cfg.maxW) return mk(State.UNKNOWN)
        if (h < cfg.minAspect * w) return mk(State.RISING)
        if (h < cfg.minH || h > cfg.maxH) return mk(State.UNKNOWN)
        val d = descriptor(frame, hole, b)
        val scores = HashMap<Label, Float>()
        for ((l, t) in templates.items) {
            val sc = ncc(d, t)
            if (sc > (scores[l] ?: -1f)) scores[l] = sc
        }
        val pos = cfg.tapLabels.maxOfOrNull { scores[it] ?: -1f } ?: -1f
        val neg = cfg.avoidLabels.maxOfOrNull { scores[it] ?: -1f } ?: -1f
        val posL = cfg.tapLabels.maxByOrNull { scores[it] ?: -1f }
        val negL = cfg.avoidLabels.maxByOrNull { scores[it] ?: -1f }
        return when {
            neg >= cfg.tapScore && neg >= pos -> mk(State.AVOID, negL, pos, neg, scores)
            pos >= cfg.tapScore && pos - neg >= cfg.margin -> mk(State.TAP, posL, pos, neg, scores)
            else -> mk(State.UNKNOWN, if (pos >= neg) posL else negL, pos, neg, scores)
        }
    }

    fun evaluate(frame: PixelSource): List<HoleEval> = cfg.holes.map { evalHole(frame, it) }

    /** Fraction of background sample points (grass between the holes) that differ from the reference. */
    fun sceneMismatch(frame: PixelSource): Float {
        var bad = 0; var tot = 0
        for (y in GUARD_Y) for (x in GUARD_X) {
            tot++
            if (maxDiff(fpx(frame, x, y), ref.rgb(x, y)) > cfg.diffThreshold) bad++
        }
        return bad.toFloat() / tot
    }

    fun sceneOk(frame: PixelSource) = sceneMismatch(frame) <= cfg.guardMaxMismatch

    companion object {
        val GUARD_X = intArrayOf(100, 250, 400, 540, 680, 820, 950)
        val GUARD_Y = intArrayOf(720, 770, 1150, 1210, 1600, 1680, 1780, 1860)
    }
}
