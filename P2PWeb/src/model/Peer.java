package model;


public class Peer {

    private String macAddress;   // unique ID  e.g. "AA:BB:CC:DD:EE:FF"
    private String ipAddress;    // LAN IP     e.g. "192.168.1.5"
    private String username;     // display name chosen at first launch
    private int    port;         // TCP port this peer listens on
    private long   lastSeen;     // System.currentTimeMillis() – updated on login

    // ── constructors
    public Peer() {}

    public Peer(String macAddress, String ipAddress, String username, int port) {
        this.macAddress = macAddress;
        this.ipAddress  = ipAddress;
        this.username   = username;
        this.port       = port;
        this.lastSeen   = System.currentTimeMillis();
    }

    // ── getters ──────────────────────────────────────────────────────
    public String getMacAddress() { return macAddress; }
    public String getIpAddress()  { return ipAddress;  }
    public String getUsername()   { return username;   }
    public int    getPort()       { return port;       }
    public long   getLastSeen()   { return lastSeen;   }

    // ── setters ──────────────────────────────────────────────────────
    public void setMacAddress(String v) { macAddress = v; }
    public void setIpAddress (String v) { ipAddress  = v; }
    public void setUsername  (String v) { username   = v; }
    public void setPort      (int    v) { port       = v; }
    public void setLastSeen  (long   v) { lastSeen   = v; }

    @Override
    public String toString() {
        return username + " [" + macAddress + "] @ " + ipAddress + ":" + port;
    }
}
