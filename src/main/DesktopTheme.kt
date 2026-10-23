package diplay.desktop

import com.formdev.flatlaf.FlatDarkLaf
import java.awt.*
import java.awt.geom.Path2D
import javax.swing.*
import javax.swing.border.EmptyBorder

object DesktopTheme {
    val background=Color(0x10151D)
    val panel=Color(0x19212D)
    val field=Color(0x222D3B)
    val text=Color(0xEDF3F9)
    val muted=Color(0x95A5B9)
    val accent=Color(0x4ADBC4)
    val danger=Color(0xFF8994)
    fun install(largeFont:Boolean=false) {
        FlatDarkLaf.setup()
        UIManager.put("defaultFont",Font("Segoe UI",Font.PLAIN,if(largeFont) 17 else 14))
        mapOf("Panel.background" to background,"Label.foreground" to text,"Button.background" to field,
            "Button.disabledBackground" to Color(0x1B2531),"Button.disabledText" to Color(0x66798F),
            "Button.foreground" to text,"TextField.background" to field,"PasswordField.background" to field,
            "ComboBox.background" to field,"ComboBox.buttonBackground" to field,"TextArea.background" to panel,"TextArea.foreground" to muted,
            "Component.focusColor" to accent,"Component.borderColor" to Color(0x344253),
            "Component.accentColor" to accent,"ScrollPane.background" to background,
            "TabbedPane.background" to background,"TabbedPane.underlineColor" to accent).forEach { (key,value)->UIManager.put(key,value) }
        UIManager.put("Button.arc",14); UIManager.put("Component.arc",12); UIManager.put("TextComponent.arc",12)
        UIManager.put("Component.focusWidth",1); UIManager.put("Button.margin",Insets(9,14,9,14))
        UIManager.put("TextField.margin",Insets(9,10,9,10)); UIManager.put("PasswordField.margin",Insets(9,10,9,10))
        UIManager.put("ComboBox.padding",Insets(8,10,8,10)); UIManager.put("ScrollBar.width",10)
    }
    fun label(value:String,size:Int=14,color:Color=text,bold:Boolean=false)=JLabel(value).apply {
        foreground=color; font=font.deriveFont(if(bold) Font.BOLD else Font.PLAIN,size.toFloat())
    }
    fun button(value:String,icon:UiIcon.Kind?=null,primary:Boolean=false)=JButton(value).apply {
        if(icon!=null) this.icon=UiIcon(icon,if(primary) background else muted)
        isFocusPainted=true; cursor=Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        if(primary) { this.background=accent; foreground=DesktopTheme.background; font=font.deriveFont(Font.BOLD) }
    }
    fun transparent(layout:LayoutManager=BorderLayout())=JPanel(layout).apply { isOpaque=false }
}

class Surface(layout:LayoutManager=BorderLayout(),private val fill:Color=DesktopTheme.panel):JPanel(layout) {
    init { isOpaque=false; border=EmptyBorder(18,18,18,18) }
    override fun paintComponent(graphics:Graphics) {
        val g=graphics.create() as Graphics2D; g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
        g.color=fill; g.fillRoundRect(0,0,width,height,24,24); g.dispose(); super.paintComponent(graphics)
    }
}

class UiIcon(private val kind:Kind,private val color:Color=DesktopTheme.muted,private val size:Int=18):Icon {
    enum class Kind { PHONE,WIFI,PLAY,STOP,HOME,BACK,MIC,FULLSCREEN,SETTINGS,LOGS,REFRESH,BRAND }
    override fun getIconWidth()=size
    override fun getIconHeight()=size
    override fun paintIcon(component:Component?,graphics:Graphics,x:Int,y:Int) {
        val g=graphics.create() as Graphics2D; g.translate(x,y); g.scale(size/24.0,size/24.0)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
        g.color=color; g.stroke=BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)
        fun line(x1:Int,y1:Int,x2:Int,y2:Int)=g.drawLine(x1,y1,x2,y2)
        fun path(vararg points:Double) { val p=Path2D.Double(); p.moveTo(points[0],points[1]); for(i in 2 until points.size step 2) p.lineTo(points[i],points[i+1]); g.draw(p) }
        when(kind) {
            Kind.PHONE->{ g.drawRoundRect(6,2,12,20,4,4); line(10,5,14,5); line(11,19,13,19) }
            Kind.WIFI->{ g.drawArc(2,4,20,18,30,120); g.drawArc(6,9,12,12,30,120); g.fillOval(10,18,4,4) }
            Kind.PLAY->{ val p=Path2D.Double(); p.moveTo(8.0,4.0); p.lineTo(20.0,12.0); p.lineTo(8.0,20.0); p.closePath(); g.fill(p) }
            Kind.STOP->g.fillRoundRect(5,5,14,14,3,3)
            Kind.HOME->{ path(3.0,11.0,12.0,3.0,21.0,11.0); path(6.0,9.0,6.0,21.0,18.0,21.0,18.0,9.0); g.drawRect(10,15,4,6) }
            Kind.BACK->{ path(11.0,5.0,4.0,12.0,11.0,19.0); line(4,12,21,12) }
            Kind.MIC->{ g.drawRoundRect(9,2,6,13,6,6); g.drawArc(5,7,14,13,180,180); line(12,20,12,23) }
            Kind.FULLSCREEN->{ path(9.0,3.0,3.0,3.0,3.0,9.0); path(15.0,3.0,21.0,3.0,21.0,9.0); path(3.0,15.0,3.0,21.0,9.0,21.0); path(21.0,15.0,21.0,21.0,15.0,21.0) }
            Kind.SETTINGS->{ line(3,6,21,6); line(3,12,21,12); line(3,18,21,18); g.color=DesktopTheme.panel; g.fillOval(6,3,6,6); g.fillOval(14,9,6,6); g.fillOval(8,15,6,6); g.color=color; g.drawOval(6,3,6,6); g.drawOval(14,9,6,6); g.drawOval(8,15,6,6) }
            Kind.LOGS->{ g.drawRoundRect(4,3,16,18,3,3); line(8,8,16,8); line(8,12,16,12); line(8,16,13,16) }
            Kind.REFRESH->{ g.drawArc(4,4,16,16,25,295); path(20.0,3.0,20.0,9.0,14.0,9.0) }
            Kind.BRAND->{ g.drawRoundRect(2,4,20,15,5,5); path(10.0,8.0,16.0,12.0,10.0,16.0); line(8,22,16,22) }
        }; g.dispose()
    }
}
