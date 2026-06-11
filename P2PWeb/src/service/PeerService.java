package service;

import database.DBHelper;
import model.Peer;

import java.util.List;

public class PeerService {

    // How old a last_seen timestamp can be before we consider a peer offline
    private static final long ONLINE_THRESHOLD_MS = 3 * 60 * 1000L;  // 3 minutes

    // ── register or update this peer on launch

    public Peer loginOrRegister(String mac, String ip, int port, String usernameIfNew) {
        Peer existing = DBHelper.getPeerByMac(mac);

        if (existing == null) {
            // First time — needs a username
            String name = (usernameIfNew != null && !usernameIfNew.isBlank())
                    ? usernameIfNew.trim()
                    : "User_" + mac.substring(mac.length() - 5).replace(":", "");

            Peer p = new Peer(mac, ip, name, port);
            DBHelper.insertPeer(p);
            System.out.println("[PeerService] New peer registered: " + name);
        } else {
            DBHelper.updatePeer(mac, ip, port);
            System.out.println("[PeerService] Peer updated: " + existing.getUsername());
        }

        return DBHelper.getPeerByMac(mac);
    }

    // ── get online peers ─────────────────────────────────────────────
    /**
     * Returns peers whose last_seen is within the last 3 minutes.
     * Excludes the local peer (myMac).
     */
    public List<Peer> getOnlinePeers(String myMac) {
        List<Peer> peers = DBHelper.getOnlinePeers();
        peers.removeIf(p -> p.getMacAddress().equalsIgnoreCase(myMac));
        return peers;
    }

    // ── get all known peers (for contacts)
    public List<Peer> getAllPeers(String myMac) {
        List<Peer> peers = DBHelper.getAllPeers();
        peers.removeIf(p -> p.getMacAddress().equalsIgnoreCase(myMac));
        return peers;
    }

    // ── check single peer online status
    public boolean isOnline(String mac) {
        Peer p = DBHelper.getPeerByMac(mac);
        if (p == null) return false;
        return (System.currentTimeMillis() - p.getLastSeen()) < ONLINE_THRESHOLD_MS;
    }

    // ── get peer by MAC
    public Peer getPeer(String mac) {
        return DBHelper.getPeerByMac(mac);
    }

    // ── display name helper

    public String getDisplayName(String mac) {
        Peer p = DBHelper.getPeerByMac(mac);
        if (p == null) return mac;
        String status = isOnline(mac) ? "online" : "offline";
        return p.getUsername() + " (" + status + ")";
    }
}
