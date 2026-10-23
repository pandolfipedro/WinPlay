package diplay.desktop;

import org.bytedeco.javacpp.*;
import org.bytedeco.ffmpeg.avcodec.*;
import org.bytedeco.ffmpeg.avutil.*;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.*;
import java.util.function.Consumer;

/** Small Windows FFmpeg boundary. No Android dependencies or external codec installation. */
public final class NativeCodecs {
    private static void check(int result, String operation) throws IOException {
        if (result < 0) throw new IOException(operation + " (FFmpeg " + result + ")");
    }
    private static void extra(AVCodecContext context, byte[] bytes) {
        if (bytes.length == 0) return;
        BytePointer pointer = new BytePointer(av_mallocz(bytes.length + AV_INPUT_BUFFER_PADDING_SIZE));
        pointer.put(bytes); context.extradata(pointer).extradata_size(bytes.length);
    }
    public static final class Video implements AutoCloseable {
        private AVCodecContext context;
        private AVPacket packet;
        private AVFrame frame, rgb;
        private SwsContext scaler;
        private int width, height;
        public Video(byte[] annexBConfiguration) throws IOException {
            av_log_set_level(AV_LOG_ERROR);
            AVCodec codec = avcodec_find_decoder(AV_CODEC_ID_H264);
            if (codec == null || codec.isNull()) throw new IOException("Decodificador H.264 indisponível");
            context = avcodec_alloc_context3(codec);
            if (context == null || context.isNull()) throw new IOException("Falha alocando H.264");
            context.thread_count(1).flags(context.flags() | AV_CODEC_FLAG_LOW_DELAY);
            extra(context, annexBConfiguration);
            try { check(avcodec_open2(context, codec, (AVDictionary)null), "Abrir H.264"); }
            catch (IOException failure) { avcodec_free_context(context); context = null; throw failure; }
            packet = av_packet_alloc(); frame = av_frame_alloc(); rgb = av_frame_alloc();
        }
        public synchronized void decode(byte[] bytes, Consumer<BufferedImage> output) throws IOException {
            if (context == null) return;
            av_packet_unref(packet); check(av_new_packet(packet, bytes.length), "Pacote H.264");
            packet.data().put(bytes);
            try {
                check(avcodec_send_packet(context, packet), "Enviar H.264");
                while (true) {
                    int result = avcodec_receive_frame(context, frame);
                    if (result == AVERROR_EAGAIN() || result == AVERROR_EOF) break;
                    check(result, "Receber H.264");
                    if (frame.width() <= 0 || frame.height() <= 0 || frame.width() > 4096 || frame.height() > 4096)
                        throw new IOException("Dimensões de vídeo inválidas");
                    if (width != frame.width() || height != frame.height()) {
                        if (width != 0) av_free(rgb.data(0));
                        width = frame.width(); height = frame.height();
                        check(av_image_alloc(rgb.data(), rgb.linesize(), width, height, AV_PIX_FMT_BGR24, 1), "Buffer BGR");
                    }
                    scaler = sws_getCachedContext(scaler, width, height, frame.format(), width, height, AV_PIX_FMT_BGR24, SWS_FAST_BILINEAR, null, null, (DoublePointer)null);
                    if (scaler == null || scaler.isNull()) throw new IOException("Conversão BGR indisponível");
                    sws_scale(scaler, frame.data(), frame.linesize(), 0, height, rgb.data(), rgb.linesize());
                    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
                    byte[] target = ((DataBufferByte)image.getRaster().getDataBuffer()).getData();
                    int stride = rgb.linesize(0);
                    for (int row = 0; row < height; row++) rgb.data(0).position((long)row * stride).get(target, row * width * 3, width * 3);
                    rgb.data(0).position(0); output.accept(image); av_frame_unref(frame);
                }
            } finally { av_packet_unref(packet); }
        }
        @Override public synchronized void close() {
            if (context == null) return;
            if (width != 0) { rgb.data(0).position(0); av_free(rgb.data(0)); }
            if (scaler != null) sws_freeContext(scaler);
            av_frame_free(rgb); av_frame_free(frame); av_packet_free(packet); avcodec_free_context(context);
            context = null;
        }
    }

    public static final class OpusEncoder implements AutoCloseable {
        private AVCodecContext context;
        private AVFrame frame;
        private AVPacket packet;
        private long pts;
        private final int channels;
        public OpusEncoder(int channels) throws IOException {
            this.channels = channels;
            AVCodec codec = avcodec_find_encoder_by_name("libopus");
            if (codec == null || codec.isNull()) throw new IOException("Encoder libopus indisponível");
            context = avcodec_alloc_context3(codec);
            context.sample_rate(48000).sample_fmt(AV_SAMPLE_FMT_S16).bit_rate(64000);
            context.time_base().num(1).den(48000);
            av_channel_layout_default(context.ch_layout(), channels);
            try { check(avcodec_open2(context, codec, (AVDictionary)null), "Abrir microfone Opus"); }
            catch (IOException failure) { avcodec_free_context(context); context=null; throw failure; }
            frame = av_frame_alloc(); frame.format(AV_SAMPLE_FMT_S16).sample_rate(48000).nb_samples(960);
            av_channel_layout_copy(frame.ch_layout(), context.ch_layout());
            check(av_frame_get_buffer(frame, 0), "Buffer de microfone"); packet = av_packet_alloc();
        }
        public synchronized void encode(byte[] pcm, Consumer<byte[]> output) throws IOException {
            if (context == null) return;
            if (pcm.length != 960 * channels * 2) throw new IOException("Tamanho de quadro Opus incorreto");
            check(av_frame_make_writable(frame), "Quadro microfone"); frame.data(0).position(0).put(pcm);
            frame.pts(pts); pts += 960; check(avcodec_send_frame(context, frame), "Codificar microfone");
            while (true) {
                int result = avcodec_receive_packet(context, packet);
                if (result == AVERROR_EAGAIN() || result == AVERROR_EOF) break;
                check(result, "Pacote microfone"); byte[] bytes = new byte[packet.size()]; packet.data().get(bytes);
                output.accept(bytes); av_packet_unref(packet);
            }
        }
        @Override public synchronized void close() {
            if (context != null) {
                avcodec_send_frame(context, (AVFrame)null);
                while (avcodec_receive_packet(context, packet) == 0) av_packet_unref(packet);
                av_frame_free(frame); av_packet_free(packet); avcodec_free_context(context); context=null;
            }
        }
    }

    public static final class Audio implements AutoCloseable {
        private AVCodecContext context;
        private AVPacket packet;
        private AVFrame frame;
        private final int channels;
        public Audio(boolean opus, int sampleRate, int channels) throws IOException {
            this.channels = channels;
            AVCodec codec = avcodec_find_decoder(opus ? AV_CODEC_ID_OPUS : AV_CODEC_ID_AAC);
            context = avcodec_alloc_context3(codec);
            context.sample_rate(opus ? 48000 : sampleRate);
            av_channel_layout_default(context.ch_layout(), channels);
            if (opus) {
                ByteBuffer header = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN);
                header.put("OpusHead".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                header.put((byte)1).put((byte)channels).putShort((short)0).putInt(48000).putShort((short)0).put((byte)0);
                extra(context, header.array());
            } else {
                int[] rates = {96000,88200,64000,48000,44100,32000,24000,22050,16000,12000,11025,8000,7350};
                int index = -1; for (int i=0;i<rates.length;i++) if (rates[i] == sampleRate) index=i;
                if (index < 0) { avcodec_free_context(context); context = null; throw new IOException("Taxa AAC não suportada"); }
                int config = (2 << 11) | (index << 7) | (channels << 3);
                extra(context, new byte[]{(byte)(config >>> 8), (byte)config});
            }
            try { check(avcodec_open2(context, codec, (AVDictionary)null), "Abrir áudio"); }
            catch (IOException failure) { avcodec_free_context(context); context=null; throw failure; }
            packet=av_packet_alloc(); frame=av_frame_alloc();
        }
        public synchronized void decode(byte[] bytes, Consumer<byte[]> output) throws IOException {
            if (context == null) return;
            av_packet_unref(packet); check(av_new_packet(packet, bytes.length), "Pacote áudio"); packet.data().put(bytes);
            try {
                check(avcodec_send_packet(context, packet), "Enviar áudio");
                while (true) {
                    int result=avcodec_receive_frame(context, frame);
                    if (result == AVERROR_EAGAIN() || result == AVERROR_EOF) break;
                    check(result, "Receber áudio");
                    int count=frame.nb_samples(), format=frame.format();
                    int actual=frame.ch_layout().nb_channels();
                    if (actual != channels || count < 0 || count > 16384) throw new IOException("Layout de áudio inesperado");
                    boolean planar=av_sample_fmt_is_planar(format) != 0;
                    int packed=av_get_packed_sample_fmt(format), size=av_get_bytes_per_sample(format);
                    ByteBuffer[] planes=new ByteBuffer[planar ? channels : 1];
                    for (int p=0;p<planes.length;p++) planes[p]=frame.data(p).capacity((long)count*size*(planar?1:channels)).asByteBuffer().order(ByteOrder.nativeOrder());
                    byte[] pcm=new byte[count*channels*2];
                    for (int s=0;s<count;s++) for (int c=0;c<channels;c++) {
                        ByteBuffer data=planes[planar?c:0]; int offset=(planar?s:s*channels+c)*size; double value;
                        if (packed == AV_SAMPLE_FMT_FLT) value=data.getFloat(offset)*32768.0;
                        else if (packed == AV_SAMPLE_FMT_DBL) value=data.getDouble(offset)*32768.0;
                        else if (packed == AV_SAMPLE_FMT_S16) value=data.getShort(offset);
                        else if (packed == AV_SAMPLE_FMT_S32) value=data.getInt(offset)/65536.0;
                        else throw new IOException("Formato de amostra não suportado: "+format);
                        short sample=(short)Math.max(-32768,Math.min(32767,Math.round(value)));
                        int at=(s*channels+c)*2; pcm[at]=(byte)sample; pcm[at+1]=(byte)(sample>>>8);
                    }
                    output.accept(pcm); av_frame_unref(frame);
                }
            } finally { av_packet_unref(packet); }
        }
        @Override public synchronized void close() {
            if (context != null) { av_frame_free(frame); av_packet_free(packet); avcodec_free_context(context); context=null; }
        }
    }
}
