import com.liam.whackbot.core.*
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun main() {
    val ref = load(UP + "38208.jpg")
    val det = Detector(ref, loadTemplates())
    for (file in listOf("38230", "38234", "38222")) {
        val src = ImageIO.read(File(UP + "$file.jpg"))
        val big = BufferedImage(1440, 3088, BufferedImage.TYPE_INT_RGB)
        val g = big.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, 1440, 3088, null); g.dispose()
        val px = IntArray(1440 * 3088); big.getRGB(0, 0, 1440, 3088, px, 0, 1440)
        val a = det.evaluate(IntPixels(1440, 3088, px)).map { it.state }
        val b = det.evaluate(load(UP + "$file.jpg")).map { it.state }
        println("$file 1440x3088 vs 1080x2316 verdicts identical: ${a == b}  mismatch=${"%.2f".format(det.sceneMismatch(IntPixels(1440, 3088, px)))}")
    }
}
