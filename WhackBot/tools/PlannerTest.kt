import com.liam.whackbot.core.*

fun ev(id: Int, st: State, l: Label? = Label.KNIGHT, pos: Float = 0.95f) =
    HoleEval(DEFAULT_HOLES[id], st, l, pos, 0.1f, 100, 100, 250, 250)
fun frame(vararg e: Pair<Int, HoleEval>): List<HoleEval> =
    DEFAULT_HOLES.map { h -> e.firstOrNull { it.first == h.id }?.second ?: HoleEval(h, State.EMPTY) }

var fails = 0
fun check(name: String, cond: Boolean) { println((if (cond) "PASS " else "FAIL ") + name); if (!cond) fails++ }

fun main() {
    // 1. needs 2 consecutive TAP frames
    var p = TapPlanner()
    check("no tap on first frame", p.plan(frame(0 to ev(0, State.TAP)), 0) == null)
    check("tap on second frame", p.plan(frame(0 to ev(0, State.TAP)), 33)?.holeId == 0)
    // 2. no repeat tap on same hole while it stays up
    check("no repeat within lock", (34..600 step 33).all { p.plan(frame(0 to ev(0, State.TAP)), it.toLong()) == null })
    // 3. re-arms after hole empties
    p.plan(frame(), 700); p.plan(frame(), 733)
    p.plan(frame(0 to ev(0, State.TAP)), 766)
    check("re-arm after empty", p.plan(frame(0 to ev(0, State.TAP)), 800)?.holeId == 0)
    // 4. AVOID / UNKNOWN / RISING / ARTIFACT never produce a tap
    p = TapPlanner()
    val never = listOf(State.AVOID, State.UNKNOWN, State.RISING, State.ARTIFACT)
    var tapped = false
    for (t in 0 until 50) for (s in never) if (p.plan(frame(*(0..8).map { it to ev(it, s, Label.BOMB) }.toTypedArray()), t * 33L) != null) tapped = true
    check("never taps AVOID/UNKNOWN/RISING/ARTIFACT", !tapped)
    // 5. flicker TAP/UNKNOWN never reaches confirm
    p = TapPlanner(); tapped = false
    for (t in 0 until 40) if (p.plan(frame(0 to ev(0, if (t % 2 == 0) State.TAP else State.UNKNOWN)), t * 33L) != null) tapped = true
    check("flickering verdict is not tapped", !tapped)
    // 6. min gap between taps + picks highest confidence first
    p = TapPlanner()
    val two = frame(1 to ev(1, State.TAP, pos = 0.85f), 2 to ev(2, State.TAP, pos = 0.99f))
    p.plan(two, 0)
    check("highest confidence first", p.plan(two, 33)?.holeId == 2)
    check("min gap respected", p.plan(two, 50) == null)
    check("second target after gap", p.plan(two, 120)?.holeId == 1)
    // 7. coordinate scaling: a 1440-wide frame gives same verdicts as 1080 (nearest-neighbour upscale)
    println(if (fails == 0) "ALL PLANNER TESTS PASSED" else "$fails FAILED")
}
