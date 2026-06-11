package controller;

import database.DBHelper;
import model.FileRecord;
import model.Message;
import model.Peer;
import network.Server;
import service.FileService;
import service.MessageService;
import service.NetworkUtils;
import service.PeerService;
import view.WebServer;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


public class AppController {

    // ── dependencies
    private final PeerService    peerService    = new PeerService();
    private final MessageService messageService = new MessageService();
    private final FileService    fileService    = new FileService();
    private final WebServer      webServer      = new WebServer();

    // ── state
    private Peer   localPeer;
    private Peer   selectedPeer;
    private Server server;

    // ── init
    public void init(String username) throws Exception {
        String mac = NetworkUtils.getLocalMAC();
        String ip  = NetworkUtils.getLocalIP();

        localPeer = peerService.loginOrRegister(mac, ip, Server.DEFAULT_PORT, username);
        System.out.println("[Controller] Logged in as: " + localPeer);

        // Start TCP server (listens for peer messages)
        server = new Server(
                Server.DEFAULT_PORT,
                localPeer.getMacAddress(),
                this::onMessageReceived,
                this::onFileReceived
        );
        Thread serverThread = new Thread(server, "P2P-Server");
        serverThread.setDaemon(true);
        serverThread.start();

        // Start Web server (serves browser UI)
        webServer.start();
    }

    public String getPeersJson() {
        List<Peer> peers = peerService.getOnlinePeers(localPeer.getMacAddress());
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < peers.size(); i++) {
            Peer p = peers.get(i);
            if (i > 0) sb.append(",");
            sb.append(peerToJson(p));
        }
        sb.append("]");
        return sb.toString();
    }

    /** Returns JSON of chat history with a peer */
    public String getHistoryJson(String peerMac) {
        List<Message> msgs = messageService.getChatHistory(
                localPeer.getMacAddress(), peerMac);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < msgs.size(); i++) {
            Message m = msgs.get(i);
            if (i > 0) sb.append(",");
            sb.append(messageToJson(m));
        }
        sb.append("]");
        return sb.toString();
    }

    /** Sends a text message. Returns "OK" or error */
    public String sendMessage(String peerMac, String content) {
        Peer peer = peerService.getPeer(peerMac);
        if (peer == null) return "ERROR: Peer not found";
        selectedPeer = peer;
        return messageService.sendMessage(localPeer.getMacAddress(), peer, content);
    }

    /** Sends a file. Returns "OK" or error */
    public String sendFile(String peerMac, File file) {
        Peer peer = peerService.getPeer(peerMac);
        if (peer == null) return "ERROR: Peer not found";
        selectedPeer = peer;
        return fileService.sendFile(localPeer.getMacAddress(), peer, file);
    }

    public String getFileHistoryJson() {
        List<FileRecord> records = fileService.getFileHistory(localPeer.getMacAddress());
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < records.size(); i++) {
            FileRecord fr = records.get(i);
            if (i > 0) sb.append(",");
            sb.append(fileRecordToJson(fr));
        }
        sb.append("]");
        return sb.toString();
    }

    /** Returns local peer info as JSON */
    public String getLocalPeerJson() {
        return peerToJson(localPeer);
    }

    /** Read text content of a received file */
    public String readFile(String filename) {
        return fileService.readReceivedFile(filename);
    }


    private void onMessageReceived(String data) {
        String[] parts = data.split("\\|", 3);
        if (parts.length < 3) return;
        String senderMac  = parts[0];
        String senderName = parts[1];
        String content    = parts[2];

        String time = new SimpleDateFormat("HH:mm").format(new Date());
        String json = "{\"type\":\"message\","
                + "\"senderMac\":" + q(senderMac) + ","
                + "\"senderName\":" + q(senderName) + ","
                + "\"content\":" + q(content) + ","
                + "\"time\":" + q(time) + "}";
        webServer.broadcast(json);
    }

    private void onFileReceived(String data) {
        String[] parts = data.split("\\|", 4);
        if (parts.length < 4) return;
        String senderMac  = parts[0];
        String senderName = parts[1];
        String filename   = parts[2];

        String json = "{\"type\":\"file\","
                + "\"senderMac\":"  + q(senderMac)  + ","
                + "\"senderName\":" + q(senderName) + ","
                + "\"filename\":"   + q(filename)   + "}";
        webServer.broadcast(json);
    }


    private String peerToJson(Peer p) {
        return "{\"mac\":"      + q(p.getMacAddress()) + ","
             + "\"username\":"  + q(p.getUsername())   + ","
             + "\"ip\":"        + q(p.getIpAddress())  + ","
             + "\"port\":"      + p.getPort()           + ","
             + "\"lastSeen\":"  + p.getLastSeen()       + "}";
    }

    private String messageToJson(Message m) {
        boolean mine = m.getSenderMac()
                        .equalsIgnoreCase(localPeer.getMacAddress());
        String time  = new SimpleDateFormat("HH:mm")
                        .format(new Date(m.getTimestamp()));
        return "{\"id\":"       + m.getId()            + ","
             + "\"sender\":"    + q(mine ? "You" : getSenderName(m.getSenderMac())) + ","
             + "\"content\":"   + q(m.getContent())    + ","
             + "\"time\":"      + q(time)              + ","
             + "\"mine\":"      + mine                 + "}";
    }

    private String fileRecordToJson(FileRecord fr) {
        boolean mine = fr.getSenderMac()
                         .equalsIgnoreCase(localPeer.getMacAddress());
        String time  = new SimpleDateFormat("MM/dd HH:mm")
                         .format(new Date(fr.getTimestamp()));
        return "{\"filename\":" + q(fr.getFilename())  + ","
             + "\"filesize\":"  + fr.getFilesize()     + ","
             + "\"time\":"      + q(time)              + ","
             + "\"sent\":"      + mine                 + "}";
    }

    private String getSenderName(String mac) {
        Peer p = peerService.getPeer(mac);
        return p != null ? p.getUsername() : mac;
    }

    // escape string for JSON
    private static String q(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r") + "\"";
    }

    // ── getters
    public Peer      getLocalPeer()  { return localPeer; }
    public WebServer getWebServer()  { return webServer; }

    public static String formatSize(long bytes) {
        if (bytes >= 1_048_576L) return String.format("%.2f MB", bytes / 1_048_576.0);
        if (bytes >= 1_024L)     return String.format("%.1f KB", bytes / 1_024.0);
        return bytes + " B";
    }
}
