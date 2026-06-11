package service;

import database.DBHelper;
import model.FileRecord;
import model.Peer;
import network.Server;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.*;
import java.util.List;


public class FileService {

    private static final int    TIMEOUT_MS  = 10_000;         // 10 seconds
    private static final long   MAX_SIZE    = 10 * 1024 * 1024L;  // 10 MB
    private static final int    CHUNK       = 4096;


    public String sendFile(String senderMac, Peer receiver, File file) {

        if (!file.exists())
            return "ERROR: File not found: " + file.getAbsolutePath();

        if (!file.getName().toLowerCase().endsWith(".txt"))
            return "ERROR: Only .txt files can be sent.";

        if (file.length() == 0)
            return "ERROR: File is empty.";

        if (file.length() > MAX_SIZE)
            return "ERROR: File too large (max 10 MB).";

        // ── read file into memory
        byte[] data;
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return "ERROR: Cannot read file — " + e.getMessage();
        }

        // ── send over TCP
        String ip   = receiver.getIpAddress();
        int    port = receiver.getPort();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // Header
            out.writeUTF(Server.PROTO_FILE);
            out.writeUTF(senderMac);
            out.writeUTF(file.getName());
            out.writeLong(data.length);

            // Body — chunked so large files don't block
            for (int offset = 0; offset < data.length; offset += CHUNK) {
                int len = Math.min(CHUNK, data.length - offset);
                out.write(data, offset, len);
            }
            out.flush();

            // Log to DB
            FileRecord fr = new FileRecord(
                    senderMac, receiver.getMacAddress(),
                    file.getName(), data.length, "sent");
            DBHelper.insertFileRecord(fr);

            System.out.println("[FileService] File sent: " + file.getName()
                    + " to " + receiver.getUsername());
            return "OK";

        } catch (Exception e) {
            System.err.println("[FileService] Send failed: " + e.getMessage());
            return "ERROR: Could not reach " + receiver.getUsername()
                    + ". Are they online?\n(" + e.getMessage() + ")";
        }
    }

    // get file history

    public List<FileRecord> getFileHistory(String myMac) {
        return DBHelper.getFileHistory(myMac);
    }

    // ── read a received file from disk

    public String readReceivedFile(String filename) {
        Path path = Paths.get(Server.RECEIVED_DIR, filename);
        if (!Files.exists(path))
            return "[File not found on disk: " + path + "]";
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "[Cannot read file: " + e.getMessage() + "]";
        }
    }
}
