package service;

import database.DBHelper;
import model.Message;
import model.Peer;
import network.Server;

import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;


public class MessageService {

    private static final int TIMEOUT_MS = 5_000;   // 5 second connect + read timeout


    public String sendMessage(String senderMac, Peer receiver, String content) {
        if (content == null || content.isBlank()) {
            return "ERROR: Message is empty.";
        }

        String ip   = receiver.getIpAddress();
        int    port = receiver.getPort();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(Server.PROTO_MSG);      // "MSG"
            out.writeUTF(senderMac);
            out.writeUTF(content);
            out.flush();

            // Save to local DB
            Message msg = new Message(senderMac, receiver.getMacAddress(), content, "sent");
            DBHelper.insertMessage(msg);

            System.out.println("[MessageService] Sent to " + receiver.getUsername());
            return "OK";

        } catch (Exception e) {
            System.err.println("[MessageService] Send failed: " + e.getMessage());
            return "ERROR: Could not reach " + receiver.getUsername() + ". Are they online?";
        }
    }

    // ── get chat history

    public List<Message> getChatHistory(String myMac, String otherMac) {
        return DBHelper.getChatHistory(myMac, otherMac);
    }
}
