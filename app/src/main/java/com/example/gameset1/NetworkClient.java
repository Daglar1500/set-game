package com.example.gameset1;

import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkClient extends Thread {

    private final String serverIp;
    private final int serverPort;
    private final String playerName;
    private final NetworkListener listener;
    private PrintWriter out;
    private boolean isRunning = true;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    public interface NetworkListener {
        void onBoardReceived(List<Card> cards);
        void onTick(String tickMsg);
        void onMessage(String msg);
        void onScore(int playerId, int points);
        void onPlayerListUpdate(Map<Integer, String> names);
        // NEW: Game Over and Reset callbacks
        void onGameOver(int winnerId);
        void onReset();
    }

    public NetworkClient(String ip, int port, String name, NetworkListener listener) {
        this.serverIp = ip;
        this.serverPort = port;
        this.playerName = name;
        this.listener = listener;
    }

    @Override
    public void run() {
        try {
            Socket socket = new Socket(serverIp, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("NAME:" + playerName);

            uiHandler.post(() -> listener.onMessage("Connected!"));

            String line;
            while (isRunning && (line = in.readLine()) != null) {
                processMessage(line);
            }

        } catch (IOException e) {
            uiHandler.post(() -> listener.onMessage("Connection Failed: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    public void sendMove(int id1, int id2, int id3) {
        new Thread(() -> {
            if (out != null) out.println("MOVE:" + id1 + "," + id2 + "," + id3);
        }).start();
    }

    private void processMessage(String msg) {
        if (msg.startsWith("TICK:")) {
            uiHandler.post(() -> listener.onTick(msg));
        }
        else if (msg.startsWith("MSG:")) {
            String text = msg.split(":")[1];
            uiHandler.post(() -> listener.onMessage(text));
        }
        else if (msg.startsWith("SCORE:")) {
            String[] parts = msg.split(":");
            int pid = Integer.parseInt(parts[1]);
            int score = Integer.parseInt(parts[2]);
            uiHandler.post(() -> listener.onScore(pid, score));
        }
        else if (msg.startsWith("NAMES:")) {
            String data = msg.substring(6);
            Map<Integer, String> map = new HashMap<>();
            String[] entries = data.split(",");
            for (String entry : entries) {
                if (entry.contains("-")) {
                    String[] pair = entry.split("-");
                    try {
                        map.put(Integer.parseInt(pair[0]), pair[1]);
                    } catch (Exception ignored) {}
                }
            }
            uiHandler.post(() -> listener.onPlayerListUpdate(map));
        }
        // NEW: Handle Win and Reset
        else if (msg.startsWith("WIN:")) {
            int pid = Integer.parseInt(msg.split(":")[1]);
            uiHandler.post(() -> listener.onGameOver(pid));
        }
        else if (msg.startsWith("RESET")) {
            uiHandler.post(() -> listener.onReset());
        }
        else if (msg.startsWith("BOARD:")) {
            String data = msg.substring(6);
            if(data.isEmpty()) return;

            List<Card> cards = new ArrayList<>();
            String[] rawCards = data.split(",");
            Object dummyLock = new Object();

            for (String rc : rawCards) {
                if (rc.isEmpty()) continue;
                String[] feats = rc.split("-");
                cards.add(new Card(
                        Integer.parseInt(feats[1]),
                        Integer.parseInt(feats[2]),
                        Integer.parseInt(feats[3]),
                        Integer.parseInt(feats[4]),
                        dummyLock
                ));
            }
            uiHandler.post(() -> listener.onBoardReceived(cards));
        }
    }
}