package com.example.watchtest.uii;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** A minimal HTTP/1.1 server for tests: one request per connection, scripted answers. JDK sockets only. */
final class TinyHttpServer implements Closeable {
    final AtomicReference<String> path = new AtomicReference<>();
    final AtomicReference<String> authorization = new AtomicReference<>();
    final AtomicReference<String> contentType = new AtomicReference<>();
    final AtomicReference<String> body = new AtomicReference<>();
    volatile int status = 200;
    volatile String response = "{}";
    volatile long delayMs = 0;
    volatile String redirectTo = null;

    private final ServerSocket socket;
    private final Thread thread;

    TinyHttpServer() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        thread = new Thread(this::loop, "tiny-http");
        thread.setDaemon(true);
        thread.start();
    }

    String baseUrl() { return "http://127.0.0.1:" + socket.getLocalPort(); }

    private void loop() {
        while (!socket.isClosed()) {
            try (Socket s = socket.accept()) {
                handle(s);
            } catch (IOException ignored) {
                // closed or the client gave up (timeout test)
            }
        }
    }

    private void handle(Socket s) throws IOException {
        InputStream in = s.getInputStream();
        String requestLine = line(in);
        if (requestLine == null) return;
        String[] parts = requestLine.split(" ");
        path.set(parts.length > 1 ? parts[1] : "");
        Map<String, String> headers = new HashMap<>();
        String h;
        while ((h = line(in)) != null && !h.isEmpty()) {
            int i = h.indexOf(':');
            if (i > 0) headers.put(h.substring(0, i).trim().toLowerCase(Locale.ROOT), h.substring(i + 1).trim());
        }
        authorization.set(headers.get("authorization"));
        contentType.set(headers.get("content-type"));
        int len = headers.containsKey("content-length") ? Integer.parseInt(headers.get("content-length")) : 0;
        byte[] b = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(b, off, len - off);
            if (n < 0) break;
            off += n;
        }
        body.set(new String(b, StandardCharsets.UTF_8));

        if (delayMs > 0) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { }
        }
        OutputStream out = s.getOutputStream();
        if (redirectTo != null) {
            out.write(("HTTP/1.1 302 Found\r\nLocation: " + redirectTo + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        } else {
            byte[] r = response.getBytes(StandardCharsets.UTF_8);
            out.write(("HTTP/1.1 " + status + " X\r\nContent-Type: application/json\r\nContent-Length: " + r.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(r);
        }
        out.flush();
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') buf.write(c);
        }
        return c < 0 && buf.size() == 0 ? null : buf.toString(StandardCharsets.UTF_8);
    }

    @Override public void close() {
        try { socket.close(); } catch (IOException ignored) { }
    }
}
