P2P LAN CHAT - Web Edition
===========================
Discord-style dark theme browser UI.
No Swing, no JavaFX — just open your browser!


HOW IT WORKS
=============
Your Java backend starts 3 servers:
  - TCP  port 9000  → peer-to-peer messaging (unchanged)
  - HTTP port 8080  → serves the browser UI (index.html)
  - HTTP port 8082  → REST API (send message, get peers, etc.)
  - WS   port 8081  → WebSocket (real-time push to browser)

You open http://localhost:8080 in Chrome/Firefox — that's your chat UI.
The browser talks to the Java backend via the API.
Real-time messages appear instantly via WebSocket.


SETUP
======

Step 1 — MySQL (same as before, run once):
  CREATE DATABASE p2p_chat;
  CREATE USER 'p2puser'@'localhost' IDENTIFIED BY 'p2ppass';
  GRANT ALL PRIVILEGES ON p2p_chat.* TO 'p2puser'@'localhost';
  FLUSH PRIVILEGES;

Step 2 — Put mysql-connector-j-9.7.0.jar in the lib/ folder.

Step 3 — Compile (Command Prompt):
  dir /s /b src\*.java > sources.txt
  javac -cp "lib\mysql-connector-j-9.7.0.jar" -d out @sources.txt

Step 4 — Run:
  java -cp "out;lib\mysql-connector-j-9.7.0.jar" App YourName

Step 5 — Browser opens automatically at http://localhost:8080
  If it doesn't open, go to http://localhost:8080 manually.


FOLDER STRUCTURE
=================
  src/
    App.java                  main() — starts everything + API routes
    model/                    Peer, Message, FileRecord (unchanged)
    database/DBHelper.java    MySQL queries (unchanged)
    service/                  All services (unchanged)
    network/Server.java       TCP server (unchanged)
    controller/
      AppController.java      updated — broadcasts via WebSocket instead of Swing
    view/
      WebServer.java          NEW — HTTP + WebSocket server
      index.html              NEW — Discord-style browser UI
  lib/
    mysql-connector-j-9.7.0.jar
  received_files/


WHAT CHANGED VS THE JAVA SWING VERSION
========================================
  REMOVED:  view/MainWindow.java  (Swing GUI — deleted)
  CHANGED:  controller/AppController.java (broadcasts JSON instead of Swing calls)
  CHANGED:  src/App.java (no Swing, opens browser instead)
  ADDED:    view/WebServer.java (HTTP + WebSocket server)
  ADDED:    view/index.html (the actual UI)

  Everything else (model, database, service, network) = 100% unchanged.


COMMON ERRORS
==============
"Cannot connect to MySQL"     → Start MySQL first
"Port 8080 already in use"    → Change HTTP_PORT in WebServer.java
"No peers showing"            → Both on same WiFi. Click Refresh button.
"WebSocket not connecting"    → Check port 8081 is not blocked by firewall
