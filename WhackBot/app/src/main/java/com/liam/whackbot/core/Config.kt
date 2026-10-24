package com.liam.whackbot.core

/** All coordinates are in "reference space": the 1080x2316 layout of the sample screenshots. */
object Ref { const val W = 1080; const val H = 2316 }

interface PixelSource {
    val width: Int
    val height: Int
    /** 0xRRGGBB */
    fun rgb(x: Int, y: Int): Int
}

class IntPixels(override val width: Int, override val height: Int, private val px: IntArray) : PixelSource {
    override fun rgb(x: Int, y: Int) = px[y * width + x] and 0xFFFFFF
}

enum class Label { JASMINE, KNIGHT, MOUSE, SKULL, BOMB }

data class Hole(val id: Int, val cx: Int, val cy: Int) {
    val x0 get() = cx - 135
    val x1 get() = cx + 135
    val y0 get() = maxOf(cy - 230, 388)
    val y1 get() = cy + 35
}

val DEFAULT_HOLES = listOf(
    Hole(0, 190, 610), Hole(1, 880, 605),
    Hole(2, 535, 830),
    Hole(3, 190, 1055), Hole(4, 875, 1055),
    Hole(5, 535, 1280),
    Hole(6, 190, 1505), Hole(7, 875, 1505),
    Hole(8, 530, 1730),
)

data class Config(
    val holes: List<Hole> = DEFAULT_HOLES,
    /** What to tap. Anything not listed here is never tapped. */
    val tapLabels: Set<Label> = setOf(Label.JASMINE, Label.KNIGHT, Label.MOUSE),
    /** Hard veto list. Used for the margin rule. */
    val avoidLabels: Set<Label> = setOf(Label.SKULL, Label.BOMB),
    val diffThreshold: Int = 40,      // per-channel difference vs. empty-board reference
    val step: Int = 2,                // sampling stride in reference px
    val minCells: Int = 600,          // smallest blob (in sampled cells) considered an object
    val minW: Int = 100, val maxW: Int = 230,
    val minH: Int = 100, val maxH: Int = 260,
    val minAspect: Double = 0.85,     // h/w below this = still rising, never tapped
    val tapScore: Float = 0.80f,      // min correlation with a tap template
    val margin: Float = 0.15f,        // tap score must beat best avoid-score by this much
    val confirmFrames: Int = 2,       // consecutive frames with same verdict before tapping
    val lockMs: Long = 1200,          // no second tap on the same hole within this time
    val rearmEmptyFrames: Int = 2,    // hole must look empty this many frames to re-arm early
    val minGapMs: Long = 60,          // minimum time between two taps
    val guardMaxMismatch: Float = 0.25f, // scene guard: fraction of background samples allowed to differ
)
