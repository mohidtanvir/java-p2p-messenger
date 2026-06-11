package model;


public class FileRecord {

    private int    id;
    private String senderMac;
    private String receiverMac;
    private String filename;
    private long   filesize;     // bytes
    private long   timestamp;
    private String status;       // "sent" | "received"

    // ── constructors
    public FileRecord() {}

    public FileRecord(String senderMac, String receiverMac,
                      String filename, long filesize, String status) {
        this.senderMac   = senderMac;
        this.receiverMac = receiverMac;
        this.filename    = filename;
        this.filesize    = filesize;
        this.status      = status;
        this.timestamp   = System.currentTimeMillis();
    }

    // ── getters ──────────────────────────────────────────────────────
    public int    getId()          { return id;          }
    public String getSenderMac()   { return senderMac;   }
    public String getReceiverMac() { return receiverMac; }
    public String getFilename()    { return filename;    }
    public long   getFilesize()    { return filesize;    }
    public long   getTimestamp()   { return timestamp;   }
    public String getStatus()      { return status;      }

    // ── setters ──────────────────────────────────────────────────────
    public void setId         (int    v) { id          = v; }
    public void setSenderMac  (String v) { senderMac   = v; }
    public void setReceiverMac(String v) { receiverMac = v; }
    public void setFilename   (String v) { filename    = v; }
    public void setFilesize   (long   v) { filesize    = v; }
    public void setTimestamp  (long   v) { timestamp   = v; }
    public void setStatus     (String v) { status      = v; }
}
