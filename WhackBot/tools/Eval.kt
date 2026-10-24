import com.liam.whackbot.core.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun load(path: String): PixelSource {
    val img = ImageIO.read(File(path))
    val w = img.width; val h = img.height
    val px = IntArray(w * h)
    img.getRGB(0, 0, w, h, px, 0, w)
    return IntPixels(w, h, px)
}

val UP: String = System.getenv("SAMPLES") ?: "tools/samples/"
val TPL: String = "app/src/main/assets/templates/"

fun loadTemplates(): TemplateSet {
    val items = ArrayList<Pair<Label, FloatArray>>()
    for (f in File(TPL).listFiles()!!.sortedBy { it.name }) {
        val label = Label.valueOf(f.name.substringBefore("_").uppercase())
        val img = ImageIO.read(f)
        val arr = FloatArray(D * D * 3)
        for (y in 0 until D) for (x in 0 until D) {
            val p = img.getRGB(x, y)
            val i = (y * D + x) * 3
            arr[i] = ((p shr 16) and 255).toFloat(); arr[i + 1] = ((p shr 8) and 255).toFloat(); arr[i + 2] = (p and 255).toFloat()
        }
        items.add(label to arr)
    }
    return TemplateSet(items)
}

// (file, hole) the templates are built from -> excluded from the "held-out" accuracy numbers
val SOURCES = listOf(
    Triple("38210", 0, Label.JASMINE),
    Triple("38212", 8, Label.KNIGHT),
    Triple("38224", 0, Label.SKULL),
    Triple("38234", 3, Label.BOMB),
    Triple("38232", 2, Label.MOUSE),
)

// Ground truth, labelled by eye. "RISING"/"COINS" = something visible that must NOT be tapped.
val TRUTH: Map<String, Map<Int, String>> = mapOf(
    "38208" to mapOf(), "38216" to mapOf(),
    "38210" to mapOf(0 to "JASMINE"),
    "38212" to mapOf(8 to "KNIGHT"),
    "38214" to mapOf(6 to "KNIGHT"),
    "38218" to mapOf(5 to "JASMINE"),
    "38220" to mapOf(0 to "RISING", 8 to "RISING"),
    "38222" to mapOf(2 to "JASMINE", 5 to "JASMINE"),
    "38224" to mapOf(0 to "SKULL"),
    "38226" to mapOf(0 to "KNIGHT", 4 to "COINS", 7 to "KNIGHT"),
    "38228" to mapOf(0 to "SKULL", 6 to "KNIGHT"),
    "38230" to mapOf(0 to "JASMINE", 1 to "JASMINE", 4 to "KNIGHT", 5 to "JASMINE", 7 to "KNIGHT"),
    "38232" to mapOf(2 to "MOUSE", 4 to "JASMINE", 7 to "KNIGHT"),
    "38234" to mapOf(0 to "KNIGHT", 1 to "JASMINE", 3 to "BOMB", 5 to "KNIGHT", 6 to "KNIGHT"),
)

fun main(args: Array<String>) {
    val ref = load(UP + "38208.jpg")
    if (args.getOrNull(0) == "build") {
        File(TPL).mkdirs(); File(TPL).listFiles()!!.forEach { it.delete() }
        val det = Detector(ref, TemplateSet(emptyList()))
        for ((i, s) in SOURCES.withIndex()) {
            val f = load(UP + s.first + ".jpg")
            val hole = det.cfg.holes[s.second]
            val b = det.segment(f, hole)!!
            val d = det.descriptor(f, hole, b)
            val img = BufferedImage(D, D, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until D) for (x in 0 until D) {
                val k = (y * D + x) * 3
                img.setRGB(x, y, (d[k].toInt().coerceIn(0, 255) shl 16) or (d[k + 1].toInt().coerceIn(0, 255) shl 8) or d[k + 2].toInt().coerceIn(0, 255))
            }
            ImageIO.write(img, "png", File(TPL + s.third.name.lowercase() + "_0.png"))
            println("template ${s.third} <- ${s.first} hole ${s.second}")
        }
        return
    }
    val det = Detector(ref, loadTemplates())
    var okN = 0; var badN = 0; var heldOk = 0; var heldBad = 0
    var wrongTap = 0; var avoidTapped = 0
    for ((file, truth) in TRUTH.toSortedMap()) {
        val fr = load(UP + "$file.jpg")
        val ev = det.evaluate(fr)
        val mm = det.sceneMismatch(fr)
        println("== $file  sceneMismatch=${"%.2f".format(mm)}")
        for (e in ev) {
            val t = truth[e.hole.id]
            val wantTap = t in setOf("JASMINE", "KNIGHT", "MOUSE")
            val gotTap = e.state == State.TAP
            val labelOk = !wantTap || e.best?.name == t
            val ok = (wantTap == gotTap) && (!gotTap || labelOk)
            val held = SOURCES.none { it.first == file && it.second == e.hole.id }
            if (ok) { okN++; if (held) heldOk++ } else { badN++; if (held) heldBad++ }
            if (gotTap && !wantTap) wrongTap++
            if (gotTap && (t == "SKULL" || t == "BOMB")) avoidTapped++
            if (t != null || e.state != State.EMPTY) {
                val sc = e.scores.entries.sortedByDescending { it.value }.joinToString(" ") { "${it.key.name.take(3)}=${"%.2f".format(it.value)}" }
                println("  hole ${e.hole.id} truth=${t ?: "-"} -> ${e.state} ${e.best ?: ""} pos=${"%.2f".format(e.pos)} neg=${"%.2f".format(e.neg)} [$sc] box=${e.bx1 - e.bx0}x${e.by1 - e.by0} ${if (ok) "OK" else "<<< MISMATCH"}${if (held) "" else " (template source)"}")
            }
        }
    }
    println("\nholes correct: $okN, wrong: $badN | held-out (excluding template sources): $heldOk ok, $heldBad wrong")
    println("taps on non-targets: $wrongTap (of which skull/bomb: $avoidTapped)")
    // fail-safe: black frame and shifted-layout frame must not look like the game
    val black = IntPixels(1080, 2316, IntArray(1080 * 2316))
    println("scene guard on black frame: ok=${det.sceneOk(black)}  (expected false)")
}
