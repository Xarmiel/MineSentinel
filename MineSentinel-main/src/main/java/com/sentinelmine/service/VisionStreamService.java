package com.sentinelmine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class VisionStreamService {

    private static final Logger log = LoggerFactory.getLogger(VisionStreamService.class);

    private static final int MAX_FRAME = 8 * 1024 * 1024;

    private static final byte[] LIMITE = "--visionframe\r\n".getBytes();
    private static final byte[] CABECERA = "Content-Type: image/jpeg\r\nContent-Length: ".getBytes();
    private static final byte[] SALTO = "\r\n\r\n".getBytes();

    private final AtomicLong secuencia = new AtomicLong();
    private final Object monitor = new Object();

    private volatile byte[] ultimo;
    private volatile long recibidoEn;

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
                return;
            }
            visto = secuencia.get();

            try {
                salida.write(LIMITE);
                salida.write(CABECERA);
                salida.write(Integer.toString(frame.length).getBytes());
                salida.write(SALTO);
                salida.write(frame);
                salida.write("\r\n".getBytes());
                salida.flush();
            } catch (IOException e) {
                log.debug("Suscriptor MJPEG desconectado: {}", e.getMessage());
                return;
            }
        }
    }
}
