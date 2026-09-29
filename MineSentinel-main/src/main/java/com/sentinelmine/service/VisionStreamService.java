package com.sentinelmine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Difusión de video MJPEG entre el motor de visión y los navegadores.
 *
 * Existe por una razón concreta: una webcam sólo puede estar en un proceso a la
 * vez, y la IA consume CPU. Si el motor dedicara el hilo principal a detectar,
 * el video quedaría a 3-4 fps aunque la cámara pueda entregar 30. La solución
 * es que el motor separe ambos trabajos y publique aquí el resultado: el video
 * corre fluido y los recuadros de EPP se refrescan cada pocas décimas de
 * segundo, que es como funcionan los sistemas de videovigilancia con analítica.
 *
 * Se guarda un solo frame, no un búfer: a un suscriptor le interesa el presente.
 * Cada suscriptor corre en su propio hilo (Spring ejecuta cada
 * StreamingResponseBody en el pool asíncrono) y espera a que llegue el
 * siguiente, así que un cliente lento no frena al motor.
 */
@Service
public class VisionStreamService {

    private static final Logger log = LoggerFactory.getLogger(VisionStreamService.class);

    /** Tamaño máximo admitido por frame. 8 MB cubre de sobra un JPEG 1080p. */
    private static final int MAX_FRAME = 8 * 1024 * 1024;

    private static final byte[] LIMITE = "--visionframe\r\n".getBytes();
    private static final byte[] CABECERA = "Content-Type: image/jpeg\r\nContent-Length: ".getBytes();
    private static final byte[] SALTO = "\r\n\r\n".getBytes();

    private final AtomicLong secuencia = new AtomicLong();
    private final Object monitor = new Object();

    private volatile byte[] ultimo;
    private volatile long recibidoEn;

    /**
     * Publica un frame para todos los suscriptores activos.
     */
    public void publicar(byte[] jpeg) {
        if (jpeg == null || jpeg.length == 0) {
            return;
        }
        this.ultimo = jpeg;
        this.recibidoEn = System.currentTimeMillis();
        this.secuencia.incrementAndGet();
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    /**
     * Espera a que llegue un frame posterior al visto y lo devuelve.
     *
     * @param vistoSecuencia secuencia del último frame ya consumido
     * @param timeoutMs      cuánto esperar antes de ceder el turno
     * @return el frame nuevo, o null si venció el tiempo de espera
     */
    public byte[] siguiente(long vistoSecuencia, long timeoutMs) throws InterruptedException {
        long limite = System.currentTimeMillis() + timeoutMs;
        synchronized (monitor) {
            while (secuencia.get() <= vistoSecuencia) {
                long restante = limite - System.currentTimeMillis();
                if (restante <= 0) {
                    return null;
                }
                monitor.wait(restante);
            }
        }
        return ultimo;
    }

    public long secuenciaActual() {
        return secuencia.get();
    }

    public long framesRecibidos() {
        return secuencia.get();
    }

    public long msDesdeUltimoFrame() {
        return this.recibidoEn == 0 ? -1 : System.currentTimeMillis() - this.recibidoEn;
    }

    // -------------------------------------------------------------------------
    // Ingesta: el motor abre UNA conexión y va escribiendo frames seguidos
    // -------------------------------------------------------------------------

    /**
     * Lee un flujo de frames JPEG con prefijo de longitud y los va republicando.
     *
     * El formato es deliberadamente binario y simple: 4 bytes de longitud en
     * big-endian seguidos de los bytes del JPEG. Se descartó multipart a
     * propósito, porque multipart sólo se parsea bien con buffering completo y
     * aquí el flujo no termina nunca; un prefijo de longitud se puede leer
     * incrementalmente sin cargar nada en memoria más allá del frame en curso.
     *
     * El método ocupa el hilo de la petición mientras el motor publique y sale
     * solo cuando el motor cierra la conexión, que es lo propio de un flujo
     * largo y bloqueante.
     */
    public void recibir(InputStream entrada) throws IOException {
        long recibidos = 0;
        long ultimoLog = System.currentTimeMillis();

        try {
            while (true) {
                byte[] cabecera = entrada.readNBytes(4);
                if (cabecera.length < 4) {
                    break;
                }
                int longitud = ByteBuffer.wrap(cabecera).getInt();
                if (longitud <= 0 || longitud > MAX_FRAME) {
                    log.warn("Longitud de frame inválida ({}); cortando el flujo", longitud);
                    break;
                }
                byte[] jpeg = entrada.readNBytes(longitud);
                if (jpeg.length < longitud) {
                    break;
                }
                publicar(jpeg);
                recibidos++;

                long ahora = System.currentTimeMillis();
                if (ahora - ultimoLog >= 10_000) {
                    log.info("MJPEG: {} frames en los últimos 10s ({} en total)",
                            recibidos, framesRecibidos());
                    recibidos = 0;
                    ultimoLog = ahora;
                }
            }
        } catch (IOException e) {
            log.info("Flujo de video cerrado tras {} frames: {}", recibidos, e.getMessage());
        }
    }

    /**
     * Escribe el stream multipart que un {@code <img>} del navegador consume
     * directamente como video continuo.
     */
    public void escribirMultipart(OutputStream salida, long timeoutMs) throws IOException {
        long visto = -1;

        while (true) {
            byte[] frame;
            try {
                frame = siguiente(visto, timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (frame == null) {
                // El motor no publica. Se corta la conexión para que el
                // navegador la reintente: mantenerla abierta mostraría la
                // última imagen congelada indefinidamente, que en un panel de
                // seguridad es peor que un hueco visible.
                return;
            }
            visto = secuencia.get();

            salida.write(LIMITE);
            salida.write(CABECERA);
            salida.write(Integer.toString(frame.length).getBytes());
            salida.write(SALTO);
            salida.write(frame);
            salida.write("\r\n".getBytes());
            salida.flush();
        }
    }
}
