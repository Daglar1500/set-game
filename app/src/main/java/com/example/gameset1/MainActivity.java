package com.example.gameset1;

import android.os.Bundle;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements NetworkClient.NetworkListener {

    private GridLayout gridLayout;
    private TextView scoreText, tickText;
    private NetworkClient networkClient;

    private final List<Card> selectedCards = new ArrayList<>();
    private final List<SetCardView> selectedViews = new ArrayList<>();

    private final Map<Integer, Integer> allScores = new HashMap<>();
    private final Map<Integer, String> playerNames = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gridLayout = findViewById(R.id.cardGrid);
        scoreText = findViewById(R.id.scoreP1);
        tickText = findViewById(R.id.tickIndicator);

        showLoginDialog();
    }

    private void showLoginDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Join Game");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Your Name");
        layout.addView(nameInput);

        final EditText ipInput = new EditText(this);
        ipInput.setHint("Server IP (e.g. 0.tcp.ngrok.io:12345)");
        ipInput.setText("10.0.2.2");
        layout.addView(ipInput);

        builder.setView(layout);

        builder.setPositiveButton("Connect", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) name = "Player";

            String address = ipInput.getText().toString().trim();
            startNetwork(address, name);
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void startNetwork(String address, String playerName) {
        String ip;
        int port = 12345;

        if (address.contains(":")) {
            String[] parts = address.split(":");
            ip = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid Port", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            ip = address;
        }

        networkClient = new NetworkClient(ip, port, playerName, this);
        networkClient.start();
    }

    private void updateScoreDisplay() {
        StringBuilder sb = new StringBuilder();
        List<Integer> sortedIds = new ArrayList<>(playerNames.keySet());
        Collections.sort(sortedIds);

        for (int pid : allScores.keySet()) {
            if (!playerNames.containsKey(pid)) {
                sortedIds.add(pid);
            }
        }

        int count = 0;
        for (int pid : sortedIds) {
            String name = playerNames.getOrDefault(pid, "P" + pid);
            int score = allScores.getOrDefault(pid, 0);

            String entry = String.format("%s: %d", name, score);
            sb.append(entry);

            sb.append("\n");

        }

        scoreText.setText(sb.toString());
    }

    // --- Network Callbacks ---

    @Override
    public void onTick(String tickMsg) {
        tickText.setText(tickMsg);
    }

    @Override
    public void onMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onScore(int playerId, int points) {
        int current = allScores.getOrDefault(playerId, 0);
        allScores.put(playerId, current + points);
        updateScoreDisplay();
    }

    @Override
    public void onPlayerListUpdate(Map<Integer, String> names) {
        playerNames.clear();
        playerNames.putAll(names);
        // Important: Remove scores for players who are no longer in the game
        allScores.keySet().retainAll(playerNames.keySet());
        updateScoreDisplay();
    }

    // NEW: Handle Game Over
    @Override
    public void onGameOver(int winnerId) {
        String name = playerNames.getOrDefault(winnerId, "Player " + winnerId);
        new AlertDialog.Builder(this)
                .setTitle("🏆 GAME OVER 🏆")
                .setMessage(name + " Wins the Game!")
                .setPositiveButton("OK", null)
                .show();
    }

    // NEW: Handle Reset
    @Override
    public void onReset() {
        allScores.clear();
        updateScoreDisplay();
        Toast.makeText(this, "New Game Started!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBoardReceived(List<Card> cards) {
        gridLayout.removeAllViews();
        selectedCards.clear();
        selectedViews.clear();

        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        int cardWidth = (screenWidth / 3) - 40;
        int cardHeight = (int)(cardWidth * 0.65);

        for (Card c : cards) {
            SetCardView view = new SetCardView(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cardWidth;
            params.height = cardHeight;
            params.setMargins(6, 6, 6, 6);
            view.setLayoutParams(params);

            view.setCard(c);
            view.setOnClickListener(v -> handleCardClick(view, c));

            gridLayout.addView(view);
        }
    }

    private void handleCardClick(SetCardView view, Card card) {
        if (selectedCards.contains(card)) {
            selectedCards.remove(card);
            selectedViews.remove(view);
            view.setSelected(false);
        } else {
            if (selectedCards.size() < 3) {
                selectedCards.add(card);
                selectedViews.add(view);
                view.setSelected(true);
            }
        }

        if (selectedCards.size() == 3) {
            networkClient.sendMove(
                    selectedCards.get(0).id,
                    selectedCards.get(1).id,
                    selectedCards.get(2).id
            );

            for(SetCardView v : selectedViews) v.setSelected(false);
            selectedCards.clear();
            selectedViews.clear();
        }
    }
}