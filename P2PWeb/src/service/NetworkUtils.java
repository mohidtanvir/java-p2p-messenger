package service;

import java.net.*;
import java.util.*;

public class NetworkUtils {

    private NetworkUtils() {}

    // ── MAC address

    public static String getLocalMAC() {
        try {
            InetAddress host = InetAddress.getLocalHost();
            NetworkInterface primary = NetworkInterface.getByInetAddress(host);
            if (primary != null && !primary.isLoopback() && primary.isUp()) {
                String mac = formatMAC(primary.getHardwareAddress());
                if (mac != null) return mac;
            }

            // Fall back: scan all interfaces
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces != null) {
                for (NetworkInterface iface : Collections.list(ifaces)) {
                    if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                    String name = iface.getName().toLowerCase();
                    if (name.startsWith("docker") || name.startsWith("vmnet")
                            || name.startsWith("vbox")   || name.startsWith("tun")
                            || name.startsWith("tap"))   continue;
                    String mac = formatMAC(iface.getHardwareAddress());
                    if (mac != null) return mac;
                }
            }
        } catch (Exception e) {
            System.err.println("[NetworkUtils] MAC detection error: " + e.getMessage());
        }
        // Fallback — generate a stable fake MAC from hostname hash
        try {
            String host = InetAddress.getLocalHost().getHostName();
            int h = host.hashCode();
            return String.format("FA:KE:%02X:%02X:%02X:%02X",
                    (h >> 24) & 0xFF, (h >> 16) & 0xFF, (h >> 8) & 0xFF, h & 0xFF);
        } catch (Exception ex) {
            return "00:00:00:00:00:01";
        }
    }

    // ── LAN IP address ───────────────────────────────────────────────
    /**
     * Returns LAN IP like "192.168.1.42".
     * Never returns 127.0.0.1 (loopback).
     */
    public static String getLocalIP() {
        try {
            InetAddress host = InetAddress.getLocalHost();
            if (!host.isLoopbackAddress()) return host.getHostAddress();

            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces != null) {
                for (NetworkInterface iface : Collections.list(ifaces)) {
                    if (iface.isLoopback() || !iface.isUp()) continue;
                    for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                        // Only IPv4
                        if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[NetworkUtils] IP detection error: " + e.getMessage());
        }
        return "127.0.0.1";
    }

    // ── format raw bytes → "AA:BB:CC:DD:EE:FF"
    private static String formatMAC(byte[] raw) {
        if (raw == null || raw.length == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", raw[i]));
        }
        return sb.toString();
    }
}
