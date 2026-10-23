package diplay.desktop

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

object BrandAssets {
    fun image(size:Int=256):BufferedImage {
        val image=BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB)
        val g=image.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
        g.color=DesktopTheme.background; g.fillRoundRect(0,0,size,size,size/4,size/4)
        val inner=size*3/4; UiIcon(UiIcon.Kind.BRAND,DesktopTheme.accent,inner).paintIcon(null,g,(size-inner)/2,(size-inner)/2)
        g.dispose(); return image
    }
    fun writeIcon(file:File) {
        val png=ByteArrayOutputStream(); ImageIO.write(image(),"png",png)
        val bytes=png.toByteArray()
        val header=ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(1).put(0).put(0).put(0).put(0)
        header.putShort(1).putShort(32).putInt(bytes.size).putInt(22)
        file.parentFile?.mkdirs(); file.outputStream().use { it.write(header.array()); it.write(bytes) }
    }
}
