package network;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.*;
import java.util.function.Consumer;

import database.DBHelper;
import model.FileRecord;
import model.Message;


public class Server implements Runnable {

    public  static final int    DEFAULT_PORT     = 9000;
    public  static final String PROTO_MSG        = "MSG";
    public  static final String PROTO_FILE       = "FILE";
    public  static final String RECEIVED_DIR     = "received_files";

    private final int             port;
    private final String          myMac;
    private final Consumer<String> onMessageReceived;   // GUI callback
    private final Consumer<String> onFileReceived;      // GUI callback

    private ServerSocket    serverSocket;
    private ExecutorService pool;
    private volatile boolean running = false;

    // ── constructor
    public Server(int port, String myMac,
                  Consumer<String> onMessageReceived,
                  Consumer<String> onFileReceived) {
        this.port              = port;
        this.myMac             = myMac;
        this.onMessageReceived = onMessageReceived;
        this.onFileReceived    = onFileReceived;
    }

    // ── start
    @Override
    public void run() {
        pool = Executors.newCachedThreadPool();
        try {
            serverSocket = new ServerSocket(port, 50);
            running = true;
            System.out.println("[Server] Listening on port " + port);

            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    pool.submit(() -> handle(client));
                } catch (Exception e) {
                    if (running) System.err.println("[Server] Accept error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[Server] Cannot start on port " + port + ": " + e.getMessage());
        }
    }

    // ── handle one incoming connection
    private void handle(Socket client) {
        try (Socket s = client) {
            s.setSoTimeout(10_000);
            DataInputStream in = new DataInputStream(s.getInputStream());

            String proto = in.readUTF();

            if (PROTO_MSG.equals(proto)) {
                handleMessage(in);
            } else if (PROTO_FILE.equals(proto)) {
                handleFile(in);
            }
        } catch (Exception e) {
            System.err.println("[Server] Handle error: " + e.getMessage());
        }
    }

    // ── receive a text message
    private void handleMessage(DataInputStream in) throws Exception {
        String senderMac = in.readUTF();
        String content   = in.readUTF();

        // Save to DB
        Message msg = new Message(senderMac, myMac, content, "sent");
        DBHelper.insertMessage(msg);

        // Get sender username for display
        var sender = DBHelper.getPeerByMac(senderMac);
        String name = (sender != null) ? sender.getUsername() : senderMac;

        // Notify GUI
        onMessageReceived.accept(senderMac + "|" + name + "|" + content);
        System.out.println("[Server] Message from " + name);
    }

    // ── receive a .txt file
    private void handleFile(DataInputStream in) throws Exception {
        String senderMac = in.readUTF();
        String filename  = in.readUTF();
        long   filesize  = in.readLong();

        // Safety check: max 10 MB
        if (filesize > 10 * 1024 * 1024) {
            System.err.println("[Server] Rejected oversized file from " + senderMac);
            return;
        }

        // Read all bytes
        byte[] data   = new byte[(int) filesize];
        int    offset = 0;
        while (offset < data.length) {
            int read = in.read(data, offset, data.length - offset);
            if (read < 0) break;
            offset += read;
        }

        // Save to received_files/ with timestamp prefix
        Files.createDirectories(Paths.get(RECEIVED_DIR));
        String stamp   = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String saveName = stamp + "_" + filename;
        Path   savePath = Paths.get(RECEIVED_DIR, saveName);
        Files.write(savePath, data);

        // Log to DB
        FileRecord fr = new FileRecord(senderMac, myMac, saveName, filesize, "received");
        DBHelper.insertFileRecord(fr);

        // Get sender name
        var sender = DBHelper.getPeerByMac(senderMac);
        String name = (sender != null) ? sender.getUsername() : senderMac;

        // Notify GUI
        onFileReceived.accept(senderMac + "|" + name + "|" + saveName + "|" + savePath.toAbsolutePath());
        System.out.println("[Server] File received from " + name + ": " + saveName);
    }

    // ── stop
    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        if (pool != null) pool.shutdownNow();
        System.out.println("[Server] Stopped.");
    }

    public boolean isRunning() { return running; }
}
