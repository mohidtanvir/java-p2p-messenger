package database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import model.Peer;
import model.Message;
import model.FileRecord;


public class DBHelper {

    // ── MySQL connection settings — change these to match your setup ──
    private static final String DB_HOST = "localhost";
    private static final String DB_PORT = "3306";
    private static final String DB_NAME = "p2p_chat";
    private static final String DB_USER = "p2puser";
    private static final String DB_PASS = "p2ppass";

    private static final String DB_URL =
            "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // ── get a connection ─────────────────────────────────────────────
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    // ── create tables on first run ───────────────────────────────────
    public static void init() {
        String[] tables = {

            // peers table
            "CREATE TABLE IF NOT EXISTS peers (" +
            "  mac_address VARCHAR(17)  PRIMARY KEY," +
            "  ip_address  VARCHAR(45)  NOT NULL," +
            "  username    VARCHAR(100)," +
            "  port        INT," +
            "  last_seen   BIGINT" +        // epoch millis
            ")",

            // messages table
            "CREATE TABLE IF NOT EXISTS messages (" +
            "  id           INT          PRIMARY KEY AUTO_INCREMENT," +
            "  sender_mac   VARCHAR(17)," +
            "  receiver_mac VARCHAR(17)," +
            "  content      TEXT," +
            "  timestamp    BIGINT," +
            "  status       VARCHAR(20)" + // 'sent' or 'pending'
            ")",

            // file_records table
            "CREATE TABLE IF NOT EXISTS file_records (" +
            "  id           INT          PRIMARY KEY AUTO_INCREMENT," +
            "  sender_mac   VARCHAR(17)," +
            "  receiver_mac VARCHAR(17)," +
            "  filename     VARCHAR(255)," +
            "  filesize     BIGINT," +
            "  timestamp    BIGINT," +
            "  status       VARCHAR(20)" + // 'sent' or 'received'
            ")"
        };

        try (Connection c = getConnection();
             Statement  s = c.createStatement()) {
            for (String sql : tables) {
                s.execute(sql);
            }
            System.out.println("[DB] MySQL connected. Database: " + DB_NAME);
        } catch (SQLException e) {
            throw new RuntimeException(
                "[DB] Cannot connect to MySQL!\n" +
                "  Host : " + DB_HOST + ":" + DB_PORT + "\n" +
                "  DB   : " + DB_NAME + "\n" +
                "  User : " + DB_USER + "\n" +
                "  Error: " + e.getMessage() + "\n\n" +
                "  Make sure MySQL is running and the credentials in DBHelper.java are correct.", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PEER operations
    // ═══════════════════════════════════════════════════════════════

    /** Insert a brand-new peer (first launch). */
    public static void insertPeer(Peer p) {
        String sql = "INSERT INTO peers (mac_address, ip_address, username, port, last_seen)" +
                     " VALUES (?, ?, ?, ?, ?)";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.getMacAddress());
            ps.setString(2, p.getIpAddress());
            ps.setString(3, p.getUsername());
            ps.setInt   (4, p.getPort());
            ps.setLong  (5, p.getLastSeen());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("insertPeer failed: " + e.getMessage(), e);
        }
    }

    /** Update IP, port, and last_seen every time the app starts. */
    public static void updatePeer(String mac, String ip, int port) {
        String sql = "UPDATE peers SET ip_address = ?, port = ?, last_seen = ? WHERE mac_address = ?";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.setInt   (2, port);
            ps.setLong  (3, System.currentTimeMillis());
            ps.setString(4, mac);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updatePeer failed: " + e.getMessage(), e);
        }
    }

    /** Find one peer by MAC address. Returns null if not found. */
    public static Peer getPeerByMac(String mac) {
        String sql = "SELECT * FROM peers WHERE mac_address = ?";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, mac);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapPeer(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("getPeerByMac failed: " + e.getMessage(), e);
        }
        return null;
    }

    /** All peers whose last_seen is within the last 3 minutes (online). */
    public static List<Peer> getOnlinePeers() {
        long threshold = System.currentTimeMillis() - (3 * 60 * 1000L);
        String sql = "SELECT * FROM peers WHERE last_seen >= ?";
        List<Peer> list = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, threshold);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapPeer(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("getOnlinePeers failed: " + e.getMessage(), e);
        }
        return list;
    }

    /** All registered peers ordered by username. */
    public static List<Peer> getAllPeers() {
        String sql = "SELECT * FROM peers ORDER BY username";
        List<Peer> list = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapPeer(rs));
        } catch (SQLException e) {
            throw new RuntimeException("getAllPeers failed: " + e.getMessage(), e);
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════════════
    //  MESSAGE operations
    // ═══════════════════════════════════════════════════════════════

    /** Save a new message. */
    public static void insertMessage(Message m) {
        String sql = "INSERT INTO messages (sender_mac, receiver_mac, content, timestamp, status)" +
                     " VALUES (?, ?, ?, ?, ?)";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, m.getSenderMac());
            ps.setString(2, m.getReceiverMac());
            ps.setString(3, m.getContent());
            ps.setLong  (4, m.getTimestamp());
            ps.setString(5, m.getStatus());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("insertMessage failed: " + e.getMessage(), e);
        }
    }

    /** All messages between two peers (both directions), oldest first. */
    public static List<Message> getChatHistory(String mac1, String mac2) {
        String sql = "SELECT * FROM messages " +
                     "WHERE (sender_mac = ? AND receiver_mac = ?) " +
                     "   OR (sender_mac = ? AND receiver_mac = ?) " +
                     "ORDER BY timestamp ASC";
        List<Message> list = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, mac1); ps.setString(2, mac2);
            ps.setString(3, mac2); ps.setString(4, mac1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapMessage(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("getChatHistory failed: " + e.getMessage(), e);
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════════════
    //  FILE RECORD operations
    // ═══════════════════════════════════════════════════════════════

    /** Log a completed file transfer. */
    public static void insertFileRecord(FileRecord fr) {
        String sql = "INSERT INTO file_records (sender_mac, receiver_mac, filename, filesize, timestamp, status)" +
                     " VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, fr.getSenderMac());
            ps.setString(2, fr.getReceiverMac());
            ps.setString(3, fr.getFilename());
            ps.setLong  (4, fr.getFilesize());
            ps.setLong  (5, fr.getTimestamp());
            ps.setString(6, fr.getStatus());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("insertFileRecord failed: " + e.getMessage(), e);
        }
    }

    /** All file transfers involving a peer (sent or received). */
    public static List<FileRecord> getFileHistory(String myMac) {
        String sql = "SELECT * FROM file_records " +
                     "WHERE sender_mac = ? OR receiver_mac = ? " +
                     "ORDER BY timestamp DESC";
        List<FileRecord> list = new ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, myMac);
            ps.setString(2, myMac);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapFileRecord(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("getFileHistory failed: " + e.getMessage(), e);
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Row mappers  (ResultSet → model object)
    // ═══════════════════════════════════════════════════════════════

    private static Peer mapPeer(ResultSet rs) throws SQLException {
        Peer p = new Peer();
        p.setMacAddress(rs.getString("mac_address"));
        p.setIpAddress (rs.getString("ip_address"));
        p.setUsername  (rs.getString("username"));
        p.setPort      (rs.getInt   ("port"));
        p.setLastSeen  (rs.getLong  ("last_seen"));
        return p;
    }

    private static Message mapMessage(ResultSet rs) throws SQLException {
        Message m = new Message();
        m.setId         (rs.getInt   ("id"));
        m.setSenderMac  (rs.getString("sender_mac"));
        m.setReceiverMac(rs.getString("receiver_mac"));
        m.setContent    (rs.getString("content"));
        m.setTimestamp  (rs.getLong  ("timestamp"));
        m.setStatus     (rs.getString("status"));
        return m;
    }

    private static FileRecord mapFileRecord(ResultSet rs) throws SQLException {
        FileRecord fr = new FileRecord();
        fr.setId         (rs.getInt   ("id"));
        fr.setSenderMac  (rs.getString("sender_mac"));
        fr.setReceiverMac(rs.getString("receiver_mac"));
        fr.setFilename   (rs.getString("filename"));
        fr.setFilesize   (rs.getLong  ("filesize"));
        fr.setTimestamp  (rs.getLong  ("timestamp"));
        fr.setStatus     (rs.getString("status"));
        return fr;
    }
}
