package model;


public class Message {

    private int    id;
    private String senderMac;    // who sent it
    private String receiverMac;  // who receives it
    private String content;      // the text
    private long   timestamp;    // epoch millis
    private String status;       // "sent" | "pending"

    // ── constructors
    public Message() {}

    public Message(String senderMac, String receiverMac, String content, String status) {
        this.senderMac   = senderMac;
        this.receiverMac = receiverMac;
        this.content     = content;
        this.status      = status;
        this.timestamp   = System.currentTimeMillis();
    }

    // ── getters
    public int    getId()          { return id;          }
    public String getSenderMac()   { return senderMac;   }
    public String getReceiverMac() { return receiverMac; }
    public String getContent()     { return content;     }
    public long   getTimestamp()   { return timestamp;   }
    public String getStatus()      { return status;      }

    // ── setters ──────────────────────────────────────────────────────
    public void setId         (int    v) { id          = v; }
    public void setSenderMac  (String v) { senderMac   = v; }
    public void setReceiverMac(String v) { receiverMac = v; }
    public void setContent    (String v) { content     = v; }
    public void setTimestamp  (long   v) { timestamp   = v; }
    public void setStatus     (String v) { status      = v; }
}
