package view;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.InputStream;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;


public class WebServer {

    public  static final int HTTP_PORT = 8080;
    public  static final int WS_PORT   = 8081;

    // All connected WebSocket clients
    private final Set<WebSocketClient> clients =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private HttpServer httpServer;
    private ServerSocket wsServerSocket;

    // ── start
    public void start() throws Exception {
        startHttpServer();
        startWebSocketServer();
        System.out.println("[WebServer] HTTP  → http://localhost:" + HTTP_PORT);
        System.out.println("[WebServer] WS    → ws://localhost:"   + WS_PORT);
        System.out.println("[WebServer] Open your browser at: http://localhost:" + HTTP_PORT);
    }

    // ── HTTP server
    private void startHttpServer() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);

        // Serve index.html
        httpServer.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || "/index.html".equals(path)) {
                serveHtml(exchange);
            } else if (path.startsWith("/file/")) {
                serveFile(exchange, path.substring(6));
            } else {
                send404(exchange);
            }
        });

        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
    }

    private void serveHtml(HttpExchange ex) throws IOException {
        // Load from disk if exists, otherwise use embedded
        String html = loadIndexHtml();
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private void serveFile(HttpExchange ex, String filename) throws IOException {
        Path p = Paths.get("received_files", filename);
        if (!Files.exists(p)) { send404(ex); return; }
        byte[] bytes = Files.readAllBytes(p);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + filename + "\"");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private void send404(HttpExchange ex) throws IOException {
        byte[] b = "Not found".getBytes();
        ex.sendResponseHeaders(404, b.length);
        ex.getResponseBody().write(b);
        ex.getResponseBody().close();
    }

    private String loadIndexHtml() {
        String[] paths = {
                "src/view/index.html",
                "src\\view\\index.html",
                "../src/view/index.html",
                "view/index.html",
                "P2PWeb/src/view/index.html",
                "P2PWeb\\src\\view\\index.html",
};
        for (String p : paths) {
            try {
                Path path = Paths.get(p);
                if (Files.exists(path)) return Files.readString(path);
            } catch (Exception ignored) {}
        }
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("index.html");
            if (is != null) return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return "<h1>index.html not found. Working dir: " + System.getProperty("user.dir") + "</h1>";
    }

    //  WebSocket server
    private void startWebSocketServer() throws Exception {
        wsServerSocket = new ServerSocket(WS_PORT);
        Thread t = new Thread(() -> {
            while (!wsServerSocket.isClosed()) {
                try {
                    Socket sock = wsServerSocket.accept();
                    WebSocketClient client = new WebSocketClient(sock, this::removeClient);
                    clients.add(client);
                    client.start();
                } catch (Exception ignored) {}
            }
        }, "WS-Accept");
        t.setDaemon(true);
        t.start();
    }

    private void removeClient(WebSocketClient c) { clients.remove(c); }

    // ── broadcast a JSON event to all browser tabs
    public void broadcast(String json) {
        for (WebSocketClient c : clients) {
            try { c.send(json); } catch (Exception ignored) {}
        }
    }

    // ── stop
    public void stop() {
        try { if (httpServer      != null) httpServer.stop(0);       } catch (Exception ignored) {}
        try { if (wsServerSocket  != null) wsServerSocket.close();   } catch (Exception ignored) {}
    }

    private static class WebSocketClient {
        private final Socket           socket;
        private final Consumer<WebSocketClient> onClose;
        private       OutputStream     out;
        private       boolean          handshakeDone = false;

        WebSocketClient(Socket socket, Consumer<WebSocketClient> onClose) {
            this.socket  = socket;
            this.onClose = onClose;
        }

        void start() {
            Thread t = new Thread(() -> {
                try {
                    InputStream  in  = socket.getInputStream();
                    this.out         = socket.getOutputStream();
                    doHandshake(in, out);
                    handshakeDone = true;
                    // Keep connection alive — read frames (ignore content for now)
                    byte[] buf = new byte[1024];
                    while (!socket.isClosed()) {
                        int n = in.read(buf);
                        if (n < 0) break;
                    }
                } catch (Exception ignored) {
                } finally {
                    onClose.accept(this);
                    try { socket.close(); } catch (Exception ignored) {}
                }
            }, "WS-Client");
            t.setDaemon(true);
            t.start();
        }

        // WebSocket HTTP upgrade handshake
        private void doHandshake(InputStream in, OutputStream out) throws Exception {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String key = null;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Sec-WebSocket-Key:")) {
                    key = line.substring(18).trim();
                }
            }
            if (key == null) throw new IOException("No WS key");

            String accept = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1")
                            .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                    .getBytes(StandardCharsets.ISO_8859_1)));

            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            out.write(response.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
        }

        // Send a WebSocket text frame
        synchronized void send(String text) throws IOException {
            if (!handshakeDone || socket.isClosed()) return;
            byte[] payload = text.getBytes(StandardCharsets.UTF_8);
            int    len     = payload.length;
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x81); // FIN + text opcode
            if (len <= 125) {
                frame.write(len);
            } else if (len <= 65535) {
                frame.write(126);
                frame.write((len >> 8) & 0xFF);
                frame.write(len & 0xFF);
            } else {
                frame.write(127);
                for (int i = 7; i >= 0; i--) frame.write((int)((len >> (i * 8)) & 0xFF));
            }
            frame.write(payload);
            out.write(frame.toByteArray());
            out.flush();
        }
    }
}
