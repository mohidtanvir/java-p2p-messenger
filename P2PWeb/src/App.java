import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import controller.AppController;
import database.DBHelper;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;


public class App {

    private static AppController ctrl;

    public static void main(String[] args) throws Exception {

        // 1. Username from command line args or default
        String username = (args.length > 0) ? args[0] : System.getProperty("user.name", "User");

        // 2. Init MySQL
        try {
            DBHelper.init();
        } catch (Exception e) {
            System.err.println("\n[ERROR] Cannot connect to MySQL!\n" + e.getMessage());
            System.err.println("\nMake sure MySQL is running and DBHelper.java credentials are correct.\n");
            System.exit(1);
        }


        ctrl = new AppController();
        ctrl.init(username);


        registerApiRoutes();

        // 5. Open browser automatically
        openBrowser("http://localhost:8080");

        System.out.println("\n[App] P2P LAN Chat running!");
        System.out.println("[App] Logged in as: " + ctrl.getLocalPeer().getUsername());
        System.out.println("[App] Open http://localhost:8080 in your browser\n");

        // Keep alive
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            System.out.println("[App] Shutting down.")));
        Thread.currentThread().join();
    }

    // ── HTTP API routes ─
    private static void registerApiRoutes() throws Exception {
        // Create a separate HttpServer on port 8080 for API only
        // Actually we use WebServer's existing server — we re-use port 8080
        HttpServer api = HttpServer.create(new InetSocketAddress(8082), 0);
        api.setExecutor(Executors.newCachedThreadPool());

        // GET /api/me  → local peer info
        api.createContext("/api/me", exchange -> {
            sendJson(exchange, ctrl.getLocalPeerJson());
        });

        // GET /api/peers  → online peers list
        api.createContext("/api/peers", exchange -> {
            sendJson(exchange, ctrl.getPeersJson());
        });

        // GET /api/history?mac=XX:XX:XX  → chat history
        api.createContext("/api/history", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String mac   = parseParam(query, "mac");
            sendJson(exchange, ctrl.getHistoryJson(mac));
        });

        // POST /api/send  → send message  body: mac=XX&content=hello
        api.createContext("/api/send", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { send405(exchange); return; }
            String body    = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String mac     = parseParam(body, "mac");
            String content = parseParam(body, "content");
            String result  = ctrl.sendMessage(mac, content);
            sendJson(exchange, "{\"result\":\"" + result + "\"}");
        });

        // POST /api/sendfile  → multipart file upload
        api.createContext("/api/sendfile", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) { send405(exchange); return; }
            try {
                String ct     = exchange.getRequestHeaders().getFirst("Content-Type");
                String mac    = parseParam(exchange.getRequestURI().getQuery(), "mac");
                byte[] bytes  = exchange.getRequestBody().readAllBytes();

                // Extract filename from Content-Disposition in multipart
                String filename = "upload_" + System.currentTimeMillis() + ".txt";
                if (ct != null && ct.contains("boundary=")) {
                    String boundary = "--" + ct.split("boundary=")[1].trim();
                    String bodyStr  = new String(bytes, StandardCharsets.UTF_8);
                    int fnIdx = bodyStr.indexOf("filename=\"");
                    if (fnIdx >= 0) {
                        filename = bodyStr.substring(fnIdx + 10, bodyStr.indexOf("\"", fnIdx + 10));
                    }
                    // Extract file bytes after double newline
                    byte[] sep = "\r\n\r\n".getBytes();
                    int start = indexOf(bytes, sep) + 4;
                    int end   = bytes.length - boundary.getBytes().length - 8;
                    if (start > 4 && end > start) {
                        bytes = Arrays.copyOfRange(bytes, start, end);
                    }
                }

                // Save temp file and send
                Path tmp = Files.createTempFile("p2p_", "_" + filename);
                Files.write(tmp, bytes);
                String result = ctrl.sendFile(mac, tmp.toFile());
                Files.deleteIfExists(tmp);
                sendJson(exchange, "{\"result\":\"" + result + "\"}");
            } catch (Exception e) {
                sendJson(exchange, "{\"result\":\"ERROR: " + e.getMessage() + "\"}");
            }
        });

        // GET /api/files  → file history
        api.createContext("/api/files", exchange -> {
            sendJson(exchange, ctrl.getFileHistoryJson());
        });

        // GET /api/readfile?name=xxx  → read received file content
        api.createContext("/api/readfile", exchange -> {
            String query    = exchange.getRequestURI().getQuery();
            String filename = parseParam(query, "name");
            String content  = ctrl.readFile(filename);
            sendJson(exchange, "{\"content\":" + jsonString(content) + "}");
        });

        api.start();
        System.out.println("[App] API server → http://localhost:8082/api/");
    }


    private static void sendJson(HttpExchange ex, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, b.length);
        ex.getResponseBody().write(b);
        ex.getResponseBody().close();
    }

    private static void send405(HttpExchange ex) throws IOException {
        ex.sendResponseHeaders(405, 0);
        ex.getResponseBody().close();
    }

    private static String parseParam(String query, String key) {
        if (query == null) return "";
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                try { return java.net.URLDecoder.decode(kv[1], "UTF-8"); }
                catch (Exception e) { return kv[1]; }
            }
        }
        return "";
    }

    private static String jsonString(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r") + "\"";
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win"))  Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            else if (os.contains("mac")) Runtime.getRuntime().exec("open " + url);
            else Runtime.getRuntime().exec("xdg-open " + url);
        } catch (Exception ignored) {}
    }
}
