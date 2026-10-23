package diplay.desktop

import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.net.*
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import javax.swing.border.EmptyBorder

class ReceiverPanel(private val tools:()->Unit={}) : JPanel(BorderLayout(0,20)), AutoCloseable {
    private val devices=JComboBox<WindowsBluetooth.Device>()
    private data class Address(val description:String,val ip:Inet4Address) {
        override fun toString()="${if(description.contains("Wi-Fi",true)||description.contains("Wireless",true)) "Wi-Fi" else description.take(18)} · ${ip.hostAddress}"
    }
    private val addresses=JComboBox<Address>()
    private val ssid=JTextField()
    private val password=JPasswordField()
    private val connect=DesktopTheme.button("Conectar CarPlay",UiIcon.Kind.PLAY,true)
    private val disconnect=DesktopTheme.button("Encerrar",UiIcon.Kind.STOP)
    private val refresh=DesktopTheme.button("Atualizar",UiIcon.Kind.REFRESH)
    private val state=DesktopTheme.label("Tudo pronto para começar",16,bold=true)
    private val subtitle=DesktopTheme.label("Conecte seu iPhone para abrir o CarPlay.",13,DesktopTheme.muted)
    private val badge=DesktopTheme.label("  Aguardando conexão  ",12,DesktopTheme.muted)
    private val duration=DesktopTheme.label("",13,DesktopTheme.muted)
    private val streamInfo=DesktopTheme.label("CarPlay sem fio",12,DesktopTheme.muted)
    private val console=JTextArea().apply { isEditable=false; font=Font(Font.MONOSPACED,Font.PLAIN,12); lineWrap=true; wrapStyleWord=true; margin=Insets(12,12,12,12) }
    private val journal=SessionLog()
    private var logSequence=-1L
    private val gate=AttemptGate()
    private var token=0L
    private val input=Executors.newSingleThreadExecutor { runnable->Thread(runnable,"carplay-ui-input").apply { isDaemon=true } }
    private val lifecycle=Executors.newSingleThreadExecutor { runnable->Thread(runnable,"carplay-ui-lifecycle").apply { isDaemon=true } }
    private val move=AtomicReference<()->Unit>()
    private val moveQueued=AtomicBoolean()
    private val repaintQueued=AtomicBoolean()
    @Volatile private var receiver:DesktopReceiver?=null
    @Volatile private var picture:BufferedImage?=null
    private var painted:BufferedImage?=null
    private var connectedAt=0L
    private var streaming=false
    private var stopping=false
    private var disposed=false
    private var refreshing=false
    private var settingsVisible=true
    private var saved=ConnectionPreferences.load()
    private val navigation=mutableListOf<JButton>()
    private val steps=mutableListOf<JLabel>()
    private val content=DesktopTheme.transparent(BorderLayout(20,0))
    private val player=Surface(BorderLayout(0,0),Color(0x141D28)).apply { border=EmptyBorder(1,1,1,1) }
    private val sidebar=Surface(BorderLayout(0,12))
    private val sideScroll=JScrollPane(sidebar).apply {
        border=EmptyBorder(0,0,0,0); isOpaque=false; viewport.isOpaque=false
        horizontalScrollBarPolicy=ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        preferredSize=Dimension(306,570)
    }
    private val logScroll=JScrollPane(console).apply { preferredSize=Dimension(0,155); border=EmptyBorder(0,0,0,0); isVisible=false }
    private val screen=object:JPanel() {
        init { background=Color(0x080E16); preferredSize=Dimension(900,510) }
        override fun paintComponent(graphics:Graphics) {
            super.paintComponent(graphics)
            val image=picture
            val g=graphics.create() as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
            if(image!=null) {
                val area=VideoGeometry.fit(width,height,image.width,image.height)
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.drawImage(image,area.x,area.y,area.width,area.height,null)
            } else {
                g.paint=GradientPaint(0f,0f,Color(0x101D2C),width.toFloat(),height.toFloat(),Color(0x080E16)); g.fillRect(0,0,width,height)
                val cx=width/2; val cy=height/2-35
                g.color=Color(0x152E38); g.fillRoundRect(cx-44,cy-44,88,88,28,28)
                UiIcon(UiIcon.Kind.BRAND,DesktopTheme.accent,48).paintIcon(this,g,cx-24,cy-24)
                fun centered(value:String,y:Int,size:Int,color:Color,bold:Boolean=false) {
                    g.font=Font("Segoe UI",if(bold) Font.BOLD else Font.PLAIN,size); g.color=color
                    g.drawString(value,cx-g.fontMetrics.stringWidth(value)/2,y)
                }
                centered(if(receiver==null) "Seu iPhone. Uma nova tela." else "Preparando sua conexão",cy+82,23,DesktopTheme.text,true)
                centered(if(receiver==null) "Mapas, músicas e seus apps, com espaço para tudo." else "Acompanhe as etapas da conexão acima.",cy+109,13,DesktopTheme.muted)
            }; g.dispose()
        }
    }
    private val uiTimer=Timer(200) { flushUi() }
    init {
        border=EmptyBorder(22,24,18,24); background=DesktopTheme.background
        val header=DesktopTheme.transparent(BorderLayout(16,0))
        val brand=DesktopTheme.transparent(FlowLayout(FlowLayout.LEFT,0,0))
        brand.add(JLabel(UiIcon(UiIcon.Kind.BRAND,DesktopTheme.accent,42)))
        brand.add(DesktopTheme.transparent(GridLayout(2,1,0,1)).apply {
            border=EmptyBorder(0,12,0,0); add(DesktopTheme.label("WinPlay",27,bold=true)); add(DesktopTheme.label("CarPlay para Windows",12,DesktopTheme.muted))
        }); header.add(brand,BorderLayout.WEST)
        val headerActions=DesktopTheme.transparent(FlowLayout(FlowLayout.RIGHT,8,4))
        disconnect.foreground=DesktopTheme.danger; disconnect.isVisible=false
        headerActions.add(disconnect)
        headerActions.add(DesktopTheme.button("Conexão",UiIcon.Kind.SETTINGS).apply { addActionListener { toggleSettings(!settingsVisible) } })
        headerActions.add(DesktopTheme.button("Ferramentas",UiIcon.Kind.LOGS).apply { addActionListener { tools() } })
        header.add(headerActions,BorderLayout.EAST); add(header,BorderLayout.NORTH)
        buildSidebar(); buildPlayer()
        content.add(sideScroll,BorderLayout.WEST); content.add(player,BorderLayout.CENTER); add(content,BorderLayout.CENTER)
        val footer=DesktopTheme.transparent(BorderLayout())
        footer.add(DesktopTheme.label("Bluetooth + Wi-Fi  •  Conexão local",12,DesktopTheme.muted),BorderLayout.WEST)
        footer.add(DesktopTheme.label("Windows  /  ${Desktop.VERSION}",12,DesktopTheme.muted),BorderLayout.EAST); add(footer,BorderLayout.SOUTH)
        ssid.text=saved.ssid
        devices.renderer=object:DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list:JList<*>?,value:Any?,index:Int,selected:Boolean,focus:Boolean):Component {
                super.getListCellRendererComponent(list,value,index,selected,focus)
                text=(value as? WindowsBluetooth.Device)?.name?:"Selecione seu iPhone"; return this
            }
        }
        devices.addActionListener { devices.toolTipText=(devices.selectedItem as? WindowsBluetooth.Device)?.toString() }
        addresses.addActionListener { addresses.toolTipText=(addresses.selectedItem as? Address)?.let { "${it.description} — ${it.ip.hostAddress}" } }
        connect.addActionListener { startConnection() }; disconnect.addActionListener { stopConnection() }; refresh.addActionListener { refresh() }
        installMouse(); setControls(false); uiTimer.start()
        registerKeyboardAction({ if(!stopping) { if(receiver==null) startConnection() else stopConnection() } },KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,InputEvent.CTRL_DOWN_MASK),WHEN_IN_FOCUSED_WINDOW)
        registerKeyboardAction({ fullscreen() },KeyStroke.getKeyStroke(KeyEvent.VK_F11,0),WHEN_IN_FOCUSED_WINDOW)
    }
    private fun buildSidebar() {
        val form=DesktopTheme.transparent(); form.layout=BoxLayout(form,BoxLayout.Y_AXIS)
        fun addRow(component:JComponent,gap:Int=12) { component.alignmentX=LEFT_ALIGNMENT; component.maximumSize=Dimension(Int.MAX_VALUE,component.preferredSize.height.coerceAtLeast(32)); form.add(component); form.add(Box.createVerticalStrut(gap)) }
        addRow(DesktopTheme.label("Vamos conectar",21,bold=true),3)
        addRow(DesktopTheme.label("Seu iPhone, na tela grande.",13,DesktopTheme.muted),16)
        fun field(title:String,component:JComponent) {
            addRow(DesktopTheme.label(title,12,DesktopTheme.muted,true),3)
            component.preferredSize=Dimension(255,42); addRow(component,10)
        }
        field("IPHONE",devices); field("REDE DO PC",addresses); field("NOME DO WI-FI",ssid); field("SENHA DO WI-FI",password)
        ssid.putClientProperty("JTextField.placeholderText","Nome exato da sua rede")
        password.putClientProperty("JTextField.placeholderText","Não será salva")
        addRow(JCheckBox("Mostrar senha").apply { isOpaque=false; font=font.deriveFont(12f); addActionListener { password.echoChar=if(isSelected) '\u0000' else '\u2022' } },12)
        connect.preferredSize=Dimension(255,46); addRow(connect,7)
        refresh.preferredSize=Dimension(255,39); addRow(refresh,12)
        addRow(JLabel("<html><div style='width:185px'>Use a mesma rede Wi-Fi no PC e no iPhone. Mantenha o Bluetooth pareado e aceite a conexão no telefone.</div></html>").apply { foreground=DesktopTheme.muted; font=font.deriveFont(12f) },0)
        sidebar.add(form,BorderLayout.NORTH)
    }
    private fun buildPlayer() {
        val top=DesktopTheme.transparent(BorderLayout(10,0)).apply { border=EmptyBorder(17,19,15,19) }
        val statusText=DesktopTheme.transparent(GridLayout(2,1,0,5)).apply { add(state); add(subtitle) }
        top.add(statusText,BorderLayout.CENTER); top.add(badge,BorderLayout.EAST)
        val stepRow=DesktopTheme.transparent(FlowLayout(FlowLayout.LEFT,18,0)).apply { border=EmptyBorder(0,2,0,0) }
        listOf("1  Bluetooth","2  Autenticação","3  CarPlay").forEach { name->DesktopTheme.label(name,12,DesktopTheme.muted).also { steps.add(it); stepRow.add(it) } }
        val north=DesktopTheme.transparent(BorderLayout(0,0)).apply { add(top,BorderLayout.CENTER); add(stepRow,BorderLayout.SOUTH); border=EmptyBorder(0,0,14,0) }
        player.add(north,BorderLayout.NORTH)
        val stage=DesktopTheme.transparent(BorderLayout()).apply { add(screen,BorderLayout.CENTER); add(logScroll,BorderLayout.SOUTH) }; player.add(stage,BorderLayout.CENTER)
        val toolbar=DesktopTheme.transparent(BorderLayout(8,0)).apply { border=EmptyBorder(12,14,12,14) }
        val buttons=DesktopTheme.transparent(FlowLayout(FlowLayout.LEFT,6,0))
        listOf(Triple("Início",UiIcon.Kind.HOME,{r:DesktopReceiver->r.home()}),Triple("Voltar",UiIcon.Kind.BACK,{r:DesktopReceiver->r.back()}),Triple("Siri",UiIcon.Kind.MIC,{r:DesktopReceiver->r.siri()})).forEach { (title,icon,action)->
            val button=DesktopTheme.button(title,icon).apply { addActionListener { dispatch(action) } }; navigation.add(button); buttons.add(button)
        }
        buttons.add(DesktopTheme.button("",UiIcon.Kind.LOGS).apply { toolTipText="Mostrar ou ocultar histórico"; accessibleContext.accessibleName="Mostrar histórico"; addActionListener { logScroll.isVisible=!logScroll.isVisible; stage.revalidate() } })
        toolbar.add(buttons,BorderLayout.WEST)
        val right=DesktopTheme.transparent(FlowLayout(FlowLayout.RIGHT,9,0)).apply {
            add(duration); add(DesktopTheme.button("",UiIcon.Kind.FULLSCREEN).apply { toolTipText="Tela cheia · F11"; accessibleContext.accessibleName="Tela cheia"; addActionListener { fullscreen() } })
        }; toolbar.add(right,BorderLayout.EAST)
        val footer=DesktopTheme.transparent(BorderLayout()).apply {
            add(toolbar,BorderLayout.CENTER)
            add(DesktopTheme.transparent(BorderLayout()).apply { border=EmptyBorder(0,20,12,20); add(streamInfo,BorderLayout.WEST)
                add(DesktopTheme.button("Salvar relatório",UiIcon.Kind.LOGS).apply { font=font.deriveFont(12f); addActionListener { exportReport() } },BorderLayout.EAST) },BorderLayout.SOUTH)
        }; player.add(footer,BorderLayout.SOUTH)
    }
    private fun message(value:String) { journal.append(value) }
    private fun flushUi() {
        if(logScroll.isVisible) {
            val (sequence,text)=journal.snapshot()
            if(sequence!=logSequence) { logSequence=sequence; console.text=text; console.caretPosition=console.document.length }
        }
        if(connectedAt!=0L) { val seconds=(System.nanoTime()-connectedAt)/1_000_000_000; duration.text="%02d:%02d".format(seconds/60,seconds%60) }
    }
    fun refresh() {
        if(refreshing||receiver!=null||stopping||disposed) return
        refreshing=true; refresh.isEnabled=false; connect.isEnabled=false
        val current=gate.begin(); token=current; subtitle.text="Buscando dispositivos e interfaces…"
        object:SwingWorker<Pair<List<WindowsBluetooth.Device>,List<Address>>,Unit>() {
            override fun doInBackground():Pair<List<WindowsBluetooth.Device>,List<Address>> {
                val phones=WindowsBluetooth.inventory().devices
                val networks=Collections.list(NetworkInterface.getNetworkInterfaces()).filter { it.isUp&&!it.isLoopback }.flatMap { iface->
                    Collections.list(iface.inetAddresses).filterIsInstance<Inet4Address>().map { Address(iface.displayName,it) }
                }.sortedBy { if(it.description.contains("Wi-Fi",true)||it.description.contains("Wireless",true)) 0 else 1 }
                return phones to networks
            }
            override fun done() {
                refreshing=false
                if(disposed||!gate.accepts(current)) return
                try {
                    val (phones,networks)=get(); devices.removeAllItems(); phones.forEach(devices::addItem)
                    val deviceIndex=phones.indexOfFirst { it.address==saved.device }.takeIf { it>=0 }?:phones.indexOfFirst { it.name.contains("iphone",true) }
                    if(deviceIndex>=0) devices.selectedIndex=deviceIndex
                    addresses.removeAllItems(); networks.forEach(addresses::addItem)
                    networks.indexOfFirst { it.ip.hostAddress==saved.address }.takeIf { it>=0 }?.let { addresses.selectedIndex=it }
                    subtitle.text=if(phones.isEmpty()) "Pareie o iPhone no Windows e clique em Atualizar." else "Selecione o iPhone e conecte para começar."
                    message("Consulta concluída: ${phones.size} dispositivos; ${networks.size} interfaces de rede.")
                } catch(error:Exception) { subtitle.text="Não foi possível consultar o Bluetooth."; message("Consulta: ${error.cause?.message?:error.message}") }
                setControls(false)
            }
        }.execute()
    }
    private fun setControls(active:Boolean) {
        val editable=!active&&!stopping
        listOf(devices,addresses,ssid,password).forEach { it.isEnabled=editable }
        connect.isEnabled=editable&&!refreshing; refresh.isEnabled=editable&&!refreshing
        disconnect.isEnabled=active&&!stopping; navigation.forEach { it.isEnabled=streaming&&!stopping }
        disconnect.isVisible=active||stopping
    }
    private fun startConnection() {
        if(stopping||disposed||refreshing) return
        val settings=try {
            val phone=devices.selectedItem as? WindowsBluetooth.Device?:error("Selecione seu iPhone. Se ele não aparecer, pareie-o no Bluetooth do Windows e atualize a lista.")
            val network=addresses.selectedItem as? Address?:error("Selecione a interface Wi-Fi que está na mesma rede do iPhone.")
            val chars=password.password
            try { ReceiverSettings(phone,network.ip,ssid.text.trim(),String(chars)) } finally { chars.fill('\u0000') }
        } catch(error:Exception) { JOptionPane.showMessageDialog(this,error.message,"Conectar ao iPhone",JOptionPane.INFORMATION_MESSAGE); return }
        if(receiver!=null) stopConnection(after={ launch(settings) }) else launch(settings)
    }
    private fun launch(settings:ReceiverSettings) {
        token=gate.begin(); val current=token
        streaming=false; connectedAt=0; picture=null; painted=null; journal.clear(); streamInfo.text="CarPlay sem fio"; duration.text=""
        saved=ConnectionPreferences(settings.device.address,settings.address.hostAddress,settings.ssid)
        lifecycle.execute { runCatching { saved.save() } }
        PlatformLog.listener={ text->if(gate.accepts(current)) message(text) }
        receiver=DesktopReceiver(settings,{ value->SwingUtilities.invokeLater { if(!disposed&&gate.accepts(current)) applyStatus(value) } },
            { value->if(gate.accepts(current)) message(value) },{ image->if(gate.accepts(current)) {
                picture=image
                if(repaintQueued.compareAndSet(false,true)) SwingUtilities.invokeLater {
                    repaintQueued.set(false)
                    if(gate.accepts(current)&&!disposed) { painted=picture; screen.repaint(); streamInfo.text="${image.width} × ${image.height}  •  CarPlay sem fio" }
                }
            } }).also { it.start() }
        connect.text="Conectar CarPlay"; applyStatus("Preparando autenticação…"); setControls(true); screen.repaint()
    }
    private fun applyStatus(value:String) {
        message(value)
        val failed=value.startsWith("Falha:")||value.startsWith("Sem sessão")||value.startsWith("Sessão encerrada")
        if(streaming&&!failed) return
        state.text=when {
            value.startsWith("CarPlay — recebendo")->"Você está conectado"
            value.startsWith("Preparando")->"Preparando sua conexão"
            value.startsWith("Conectando")->"Conectando ao iPhone"
            value.startsWith("Identificando")->"Autorizando a conexão"
            value.startsWith("Autenticado")->"iPhone autenticado"
            value.startsWith("Aguardando")||value.startsWith("Sessão de rede")->"Abrindo o CarPlay"
            failed->"Conexão interrompida"
            else->"Conectando"
        }
        subtitle.text=when {
            value.startsWith("CarPlay — recebendo")->"Seus apps estão na tela. Use o mouse para controlar."
            value.startsWith("Identificando")->"Aceite a solicitação de CarPlay no seu iPhone."
            failed->"Abra o histórico para ver a etapa atingida."
            else->value
        }
        val step=when { value.startsWith("CarPlay — recebendo")->3; value.startsWith("Autenticado")||value.startsWith("Aguardando")||value.startsWith("Sessão de rede")->2; value.startsWith("Identificando")->1; else->0 }
        steps.forEachIndexed { index,label->label.foreground=if(index<step) DesktopTheme.accent else DesktopTheme.muted }
        if(failed) {
            streaming=false; connectedAt=0; badge.text="  Conexão interrompida  "; badge.foreground=DesktopTheme.danger
            connect.text="Reconectar"; connect.isEnabled=true; navigation.forEach { it.isEnabled=false }
            if(value.startsWith("Falha:")) stopConnection(keepFailure=true)
        } else if(step==3) {
            streaming=true; connectedAt=System.nanoTime(); badge.text="  ●  Conectado  "; badge.foreground=DesktopTheme.accent
            setControls(true); toggleSettings(false)
        } else { badge.text="  Conectando…  "; badge.foreground=DesktopTheme.accent }
    }
    private fun stopConnection(after:(()->Unit)?=null,keepFailure:Boolean=false) {
        if(stopping||disposed) return
        val current=gate.begin(); token=current; val previous=receiver; receiver=null
        stopping=true; streaming=false; connectedAt=0; picture=null; move.set(null); screen.repaint(); setControls(false)
        if(!keepFailure) { state.text="Encerrando a conexão"; subtitle.text="Liberando a sessão para uma nova tentativa…"; badge.text="  Encerrando…  "; badge.foreground=DesktopTheme.muted }
        lifecycle.execute {
            runCatching { previous?.close() }.onFailure { message("Encerramento: ${it.message}") }
            SwingUtilities.invokeLater {
                if(disposed||!gate.accepts(current)) return@invokeLater
                stopping=false; PlatformLog.listener=null; setControls(false); toggleSettings(true)
                duration.text=""; streamInfo.text="CarPlay sem fio"
                if(!keepFailure) { state.text="Tudo pronto para começar"; subtitle.text="Seu iPhone e sua rede estão selecionados."; badge.text="  Aguardando conexão  "; badge.foreground=DesktopTheme.muted; steps.forEach { it.foreground=DesktopTheme.muted } }
                after?.invoke()
            }
        }
    }
    private fun dispatch(action:(DesktopReceiver)->Unit) {
        val target=receiver?:return; val current=token
        if(disposed) return
        input.execute { if(gate.accepts(current)&&receiver===target) runCatching { action(target) }.onFailure { message("Controle: ${it.message}") } }
    }
    private fun installMouse() {
        var down=false
        fun touch(event:MouseEvent,pressed:Boolean,drag:Boolean=false) {
            if(!streaming) return
            val image=picture?:return
            val area=VideoGeometry.fit(screen.width,screen.height,image.width,image.height)
            val point=VideoGeometry.point(area,event.x,event.y,release=!pressed)?:return
            val target=receiver?:return; val current=token
            val action={ if(gate.accepts(current)&&receiver===target) runCatching { target.touch(point.first,point.second,pressed) }.onFailure { message("Toque: ${it.message}") }; Unit }
            if(drag) {
                move.set(action)
                if(moveQueued.compareAndSet(false,true)) input.execute { val latest=move.getAndSet(null); moveQueued.set(false); latest?.invoke() }
            } else { if(!pressed) move.set(null); input.execute(action) }
        }
        screen.addMouseListener(object:MouseAdapter() {
            override fun mousePressed(event:MouseEvent) {
                val image=picture?:return
                if(streaming&&SwingUtilities.isLeftMouseButton(event)&&VideoGeometry.fit(screen.width,screen.height,image.width,image.height).contains(event.point)) { down=true; touch(event,true) }
            }
            override fun mouseReleased(event:MouseEvent) { if(down) { down=false; touch(event,false) } }
        })
        screen.addMouseMotionListener(object:MouseMotionAdapter() { override fun mouseDragged(event:MouseEvent) { if(down) touch(event,true,true) } })
    }
    private fun toggleSettings(visible:Boolean) { settingsVisible=visible; sideScroll.isVisible=visible; content.revalidate(); content.repaint() }
    private fun exportReport() {
        val chooser=JFileChooser().apply { selectedFile=java.io.File("winplay-conexao.txt") }
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION) return
        val target=chooser.selectedFile
        if(target.exists()&&JOptionPane.showConfirmDialog(this,"Substituir ${target.name}?","Salvar relatório",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return
        val report="WinPlay ${Desktop.VERSION}\n${state.text}\n${journal.snapshot().second}\n"
        lifecycle.execute { runCatching { target.writeText(report) }.onFailure { error->SwingUtilities.invokeLater { JOptionPane.showMessageDialog(this,"Não foi possível salvar: ${error.message}") } } }
    }
    private var fullscreenWindow:JFrame?=null
    private fun fullscreen() {
        fullscreenWindow?.let { restoreFullscreen(); return }
        val owner=SwingUtilities.getWindowAncestor(this)
        val window=JFrame("WinPlay · CarPlay").apply { isUndecorated=true; defaultCloseOperation=JFrame.DO_NOTHING_ON_CLOSE }
        content.remove(player); window.contentPane.add(player,BorderLayout.CENTER)
        window.rootPane.registerKeyboardAction({ restoreFullscreen() },KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),WHEN_IN_FOCUSED_WINDOW)
        window.rootPane.registerKeyboardAction({ restoreFullscreen() },KeyStroke.getKeyStroke(KeyEvent.VK_F11,0),WHEN_IN_FOCUSED_WINDOW)
        window.addWindowListener(object:WindowAdapter() { override fun windowClosing(event:WindowEvent) { restoreFullscreen() } })
        window.bounds=owner?.graphicsConfiguration?.bounds?:GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
        fullscreenWindow=window; window.isVisible=true; content.revalidate()
    }
    private fun restoreFullscreen() {
        val window=fullscreenWindow?:return; window.contentPane.remove(player); content.add(player,BorderLayout.CENTER)
        fullscreenWindow=null; window.dispose(); content.revalidate(); content.repaint()
    }
    fun hasSession()=receiver!=null||stopping
    fun shutdown(after:()->Unit) {
        if(disposed) return
        disposed=true; gate.cancel(); uiTimer.stop(); PlatformLog.listener=null; restoreFullscreen()
        val previous=receiver; receiver=null
        lifecycle.execute { runCatching { previous?.close() }; SwingUtilities.invokeLater(after) }; lifecycle.shutdown(); input.shutdownNow()
    }
    override fun close() { shutdown {} }
}
