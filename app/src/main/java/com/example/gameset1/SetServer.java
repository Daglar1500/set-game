import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class SetServer {
    private static final int PORT = 12345;
    private static final int TICK_MS = 500;

    // Global State
    private static final List<Card> board = Collections.synchronizedList(new ArrayList<>());
    private static final List<Card> deck = new ArrayList<>();
    private static final List<ClientHandler> players = new CopyOnWriteArrayList<>();
    private static final List<MoveRequest> moveBuffer = Collections.synchronizedList(new ArrayList<>());
    
    // Scores and Names
    private static final Map<Integer, Integer> playerScores = new ConcurrentHashMap<>();
    private static final Map<Integer, String> playerNames = new ConcurrentHashMap<>();

    private static int playerCount = 0;
    private static long tickCount = 0;

    public static void main(String[] args) throws IOException {
        System.out.println(">>> SET GAME SERVER STARTED ON PORT " + PORT);
        initializeDeck();
        deal(12);

        new Thread(SetServer::tickLoop).start();

        ServerSocket serverSocket = new ServerSocket(PORT);
        while (true) {
            Socket socket = serverSocket.accept();
            playerCount++;
            System.out.println("Player connected: ID " + playerCount);
            
            // Initialize
            playerScores.put(playerCount, 0);
            playerNames.put(playerCount, "Player " + playerCount);
            
            ClientHandler player = new ClientHandler(socket, playerCount);
            players.add(player);
            player.start();
        }
    }

    private static void tickLoop() {
        while (true) {
            try {
                Thread.sleep(TICK_MS);
                synchronized (board) {}
                processMoveBuffer();
                tickCount++;
                broadcast("TICK:" + tickCount);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void processMoveBuffer() {
        synchronized (moveBuffer) {
            if (moveBuffer.isEmpty()) return;

            moveBuffer.sort(Comparator.comparingInt(m -> m.playerId));
            List<Card> cardsToRemove = new ArrayList<>();

            for (MoveRequest req : moveBuffer) {
                boolean conflict = false;
                for (int id : req.cardIds) {
                    boolean found = false;
                    for (Card c : board) {
                        if (c.id == id && !cardsToRemove.contains(c)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        conflict = true;
                        break;
                    }
                }

                if (!conflict) {
                    Card c1 = findCard(req.cardIds[0]);
                    Card c2 = findCard(req.cardIds[1]);
                    Card c3 = findCard(req.cardIds[2]);

                    if (isSet(c1, c2, c3)) {
                        System.out.println("Set found by " + req.playerId);
                        cardsToRemove.add(c1);
                        cardsToRemove.add(c2);
                        cardsToRemove.add(c3);
                        
                        int newScore = playerScores.merge(req.playerId, 1, Integer::sum);
                        broadcast("SCORE:" + req.playerId + ":1");
                        
                        if (newScore >= 3) {
                            broadcast("WIN:" + req.playerId);
                            for (Integer key : playerScores.keySet()) {
                                playerScores.put(key, 0);
                            }
                            broadcast("RESET");
                        }

                    }
                } else {
                    sendToPlayer(req.playerId, "MSG:Too Slow!");
                }
            }

            if (!cardsToRemove.isEmpty()) {
                board.removeAll(cardsToRemove);
                deal(3);
                broadcastBoard();
            }
            moveBuffer.clear();
        }
    }

    private static void broadcast(String msg) {
        for (ClientHandler p : players) p.send(msg);
    }

    private static void sendToPlayer(int id, String msg) {
        for (ClientHandler p : players) {
            if (p.pid == id) p.send(msg);
        }
    }

    private static void broadcastNames() {
        StringBuilder sb = new StringBuilder("NAMES:");
        for (Map.Entry<Integer, String> entry : playerNames.entrySet()) {
            sb.append(entry.getKey()).append("-").append(entry.getValue()).append(",");
        }
        broadcast(sb.toString());
    }

    private static void broadcastBoard() {
        StringBuilder sb = new StringBuilder("BOARD:");
        synchronized (board) {
            for (Card c : board) {
                sb.append(c.id).append("-").append(c.number).append("-")
                  .append(c.shape).append("-").append(c.shading).append("-")
                  .append(c.color).append(",");
            }
        }
        broadcast(sb.toString());
    }

    private static void deal(int count) {
        for (int i = 0; i < count; i++) {
            if (!deck.isEmpty()) board.add(deck.remove(0));
        }
    }

    private static void initializeDeck() {
        deck.clear();
        for(int n=0; n<3; n++)
            for(int s=0; s<3; s++)
                for(int sh=0; sh<3; sh++)
                    for(int c=0; c<3; c++)
                        deck.add(new Card(n, s, sh, c));
        Collections.shuffle(deck);
    }

    private static Card findCard(int id) {
        synchronized (board) {
            for(Card c : board) if(c.id == id) return c;
        }
        return null;
    }

    private static boolean isSet(Card a, Card b, Card c) {
        if(a==null || b==null || c==null) return false;
        return ((a.number + b.number + c.number) % 3 == 0) &&
                ((a.shape + b.shape + c.shape) % 3 == 0) &&
                ((a.shading + b.shading + c.shading) % 3 == 0) &&
                ((a.color + b.color + c.color) % 3 == 0);
    }

    static class Card {
        int id, number, shape, shading, color;
        Card(int n, int s, int sh, int c) {
            this.number = n; this.shape = s; this.shading = sh; this.color = c;
            this.id = n*27 + s*9 + sh*3 + c;
        }
    }

    static class MoveRequest {
        int playerId;
        int[] cardIds;
        MoveRequest(int pid, int[] ids) { this.playerId = pid; this.cardIds = ids; }
    }

    static class ClientHandler extends Thread {
        Socket socket;
        int pid;
        PrintWriter out;

        ClientHandler(Socket s, int id) { this.socket = s; this.pid = id; }

        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                send("MSG:Welcome Player " + pid);
                
                for (Map.Entry<Integer, Integer> entry : playerScores.entrySet()) {
                     if (entry.getValue() > 0) send("SCORE:" + entry.getKey() + ":" + entry.getValue());
                }
                
                broadcastNames(); 
                broadcastBoard();

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("MOVE:")) {
                        String[] parts = line.split(":")[1].split(",");
                        int[] ids = new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
                        synchronized (moveBuffer) {
                            moveBuffer.add(new MoveRequest(pid, ids));
                        }
                    }
                    else if (line.startsWith("NAME:")) {
                        String name = line.split(":")[1];
                        playerNames.put(pid, name);
                        broadcastNames(); 
                    }
                }
            } catch (Exception e) {
                // Log exception if needed
                System.out.println("Error for Player " + pid + ": " + e.getMessage());
            } finally {
                // --- FIXED: ALWAYS RUN CLEANUP ---
                System.out.println("Player " + pid + " disconnected.");
                players.remove(this);
                
                // Remove from maps
                playerNames.remove(pid);
                playerScores.remove(pid);
                
                // Broadcast update to all remaining clients
                broadcastNames();
                
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        void send(String msg) {
            if (out != null) out.println(msg);
        }
    }
}