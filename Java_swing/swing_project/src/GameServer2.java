
// import java.io.*;
// import java.net.*;
// import java.util.*;
// import java.util.concurrent.*;

// public class GameServer2 {
//     private static final int PORT = 2003;
//     private static final int WIDTH = 1280;
//     private static final int HEIGHT = 720;
//     private static final int CHARACTER_WIDTH = 50;
//     private static final int CHARACTER_HEIGHT = 50;
//     private static final int ENEMY_SIZE = 100;
//     private static final int BULLET_SPEED = 10;
//     private static final int BULLET_LIFETIME = 2000;
//     private static final int PLAYER_MOVE_SPEED = 5;
//     private static final int ENEMY_SPEED = 2;
//     private static final int BULLET_SIZE = 140;

//     private static class Player {

//         int mp = 100;
//         int x = 100;
//         int y = 300;
//         int health = 100;
//         boolean facingRight = true;
//         Set<String> keys = ConcurrentHashMap.newKeySet();
//     }

//     private static class Enemy {

//         int x, y, frame = 0;
//         boolean moving = false;

//         Enemy(int spawnX, int spawnY) {
//             this.x = spawnX;
//             this.y = spawnY;
//         }
//     }

//     private static class Bullet {

//         int x, y, dx;
//         long createdTime;

//         Bullet(int x, int y, int dx) {
//             this.x = x;
//             this.y = y;
//             this.dx = dx;
//             this.createdTime = System.currentTimeMillis();
//         }

//         boolean isExpired() {
//             return System.currentTimeMillis() - createdTime > BULLET_LIFETIME;
//         }
//     }

//     private final Map<Socket, Player> players = new ConcurrentHashMap<>();
//     private final List<Enemy> enemies = new CopyOnWriteArrayList<>();
//     private final List<Bullet> bullets = new CopyOnWriteArrayList<>();
//     private final List<ObjectOutputStream> outputs = new CopyOnWriteArrayList<>();

//     public void start() throws IOException {
//         ServerSocket serverSocket = new ServerSocket(PORT);
//         System.out.println("서버 실행 중... 포트: " + PORT);

//         spawnEnemies(5);

//         Timer gameLoop = new Timer();
//         gameLoop.scheduleAtFixedRate(new TimerTask() {
//             public void run() {
//                 updateGameState();
//                 broadcastState();
//             }
//         }, 0, 16);

//         new Timer().scheduleAtFixedRate(new TimerTask() {
//             public void run() {
//                 for (Enemy e : enemies) {
//                     if (e.moving) {
//                         e.frame = (e.frame + 1) % 6;
//                     }
//                 }
//             }
//         }, 0, 200);

//         new Timer().scheduleAtFixedRate(new TimerTask() {
//             public void run() {
//                 checkDamage();
//             }
//         }, 0, 500);

//         while (true) {
//             Socket client = serverSocket.accept();
//             System.out.println("클라이언트 접속: " + client.getInetAddress());
//             Player player = new Player();
//             players.put(client, player);

//             ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
//             outputs.add(out);
//             ObjectInputStream in = new ObjectInputStream(client.getInputStream());

//             new Thread(() -> handleClient(client, in)).start();
//         }
//     }

//     private void spawnEnemies(int count) {
//         Random rand = new Random();
//         for (int i = 0; i < count; i++) {
//             int side = rand.nextInt(4);
//             int spawnX = 0, spawnY = 0;
//             switch (side) {
//                 case 0: // 왼쪽
//                     spawnX = -ENEMY_SIZE;
//                     spawnY = rand.nextInt(HEIGHT);
//                     break;
//                 case 1: // 오른쪽
//                     spawnX = WIDTH;
//                     spawnY = rand.nextInt(HEIGHT);
//                     break;
//                 case 2: // 위
//                     spawnX = rand.nextInt(WIDTH);
//                     spawnY = -ENEMY_SIZE;
//                     break;
//                 case 3: // 아래
//                     spawnX = rand.nextInt(WIDTH);
//                     spawnY = HEIGHT;
//                     break;
//             }
//             enemies.add(new Enemy(spawnX, spawnY));
//         }
//     }

//     private void handleClient(Socket socket, ObjectInputStream in) {
//         try {
//             while (true) {
//                 Object obj = in.readObject();
//                 if (obj instanceof Map) {
//                     @SuppressWarnings("unchecked")
//                     Map<String, Object> input = (Map<String, Object>) obj;
//                     handleInput(players.get(socket), input);
//                 }
//             }
//         } catch (Exception e) {
//             System.out.println("클라이언트 연결 종료: " + socket.getInetAddress());
//             players.remove(socket);
//         }
//     }

//     private void handleInput(Player player, Map<String, Object> input) {
//         Object keys = input.get("keys");
//         if (keys instanceof List<?>) {
//             player.keys = new HashSet<>((List<String>) keys);
//         }

//         Object action = input.get("action");
//         if ("skill".equals(action)) {
//             if (player.mp >= 10) {
//                 player.mp -= 10;
//             }
//         } else if ("attack".equals(action)) {
//             int bulletX = player.facingRight ? player.x + CHARACTER_WIDTH - BULLET_SIZE / 2
//                     : player.x - BULLET_SIZE / 2;
//             int bulletY = player.y + CHARACTER_HEIGHT / 2 - BULLET_SIZE / 2 - 13;
//             int dx = player.facingRight ? BULLET_SPEED : -BULLET_SPEED;
//             bullets.add(new Bullet(bulletX, bulletY, dx));
//         }
//     }

//     private void updateGameState() {
//         for (Player player : players.values()) {
//             updatePlayer(player);
//         }
//         updateEnemies();
//         updateBullets();
//     }

//     private void updatePlayer(Player player) {
//         int dx = 0, dy = 0;
//         for (String key : player.keys) {
//             switch (key) {
//                 case "LEFT":
//                     dx -= PLAYER_MOVE_SPEED;
//                     player.facingRight = false;
//                     break;
//                 case "RIGHT":
//                     dx += PLAYER_MOVE_SPEED;
//                     player.facingRight = true;
//                     break;
//                 case "UP":
//                     dy -= PLAYER_MOVE_SPEED;
//                     break;
//                 case "DOWN":
//                     dy += PLAYER_MOVE_SPEED;
//                     break;
//             }
//         }
//         if (dx != 0 && dy != 0) {
//             dx *= 0.7071;
//             dy *= 0.7071;
//         }
//         player.x += dx;
//         player.y += dy;
//     }

//     private void updateEnemies() {
//         for (Enemy e : enemies) {
//             Player nearest = getNearestPlayer(e);
//             if (nearest == null) {
//                 continue;
//             }
//             int targetX = nearest.x - CHARACTER_WIDTH / 2;
//             int targetY = nearest.y - CHARACTER_HEIGHT / 2 + 20;
//             int dx = targetX - e.x;
//             int dy = targetY - e.y;
//             double dist = Math.sqrt(dx * dx + dy * dy);
//             if (dist > 0) {
//                 e.x += (int) (ENEMY_SPEED * dx / dist);
//                 e.y += (int) (ENEMY_SPEED * dy / dist);
//                 e.moving = true;
//             } else {
//                 e.moving = false;
//             }
//         }
//     }

//     private Player getNearestPlayer(Enemy e) {
//         Player nearest = null;
//         double minDist = Double.MAX_VALUE;
//         for (Player p : players.values()) {
//             double dist = Math.hypot(p.x - e.x, p.y - e.y);
//             if (dist < minDist) {
//                 minDist = dist;
//                 nearest = p;
//             }
//         }
//         return nearest;
//     }

//     private void updateBullets() {
//         bullets.removeIf(Bullet::isExpired);
//         for (Bullet b : bullets) {
//             b.x += b.dx;
//         }
//     }

//     private void checkDamage() {
//         for (Player p : players.values()) {
//             int centerX = p.x + CHARACTER_WIDTH / 2;
//             int centerY = p.y + CHARACTER_HEIGHT / 2;
//             int hitLeft = centerX - 25;
//             int hitRight = centerX + 25;
//             int hitTop = centerY - 45;
//             int hitBottom = centerY + 20;
//             int count = 0;
//             for (Enemy e : enemies) {
//                 int ex = e.x + ENEMY_SIZE / 2;
//                 int ey = e.y + ENEMY_SIZE / 2;
//                 if (ex >= hitLeft && ex <= hitRight && ey >= hitTop && ey <= hitBottom) {
//                     count++;
//                 }
//             }
//             int effective = Math.min(count, 5);
//             int damage = effective * 10;
//             p.health = Math.max(0, p.health - damage);
//         }
//     }

//     private void broadcastState() {
//         Map<String, Object> state = createGameState();
//         for (ObjectOutputStream out : outputs) {
//             try {
//                 out.reset();
//                 out.writeObject(state);
//                 out.flush();
//             } catch (IOException e) {
//                 outputs.remove(out);
//             }
//         }
//     }

//     private Map<String, Object> createGameState() {
//         Map<String, Object> state = new HashMap<>();

//         List<Map<String, Object>> playerList = new ArrayList<>();
//         for (Player p : players.values()) {
//             Map<String, Object> pm = new HashMap<>();
//             pm.put("x", p.x);
//             pm.put("y", p.y);
//             pm.put("health", p.health);
//             pm.put("facingRight", p.facingRight);
//             pm.put("mp", p.mp);
//             playerList.add(pm);
//         }
//         state.put("players", playerList);

//         List<Map<String, Object>> enemyList = new ArrayList<>();
//         for (Enemy e : enemies) {
//             Map<String, Object> em = new HashMap<>();
//             em.put("x", e.x);
//             em.put("y", e.y);
//             em.put("frame", e.frame);
//             enemyList.add(em);
//         }
//         state.put("enemies", enemyList);

//         List<Map<String, Object>> bulletList = new ArrayList<>();
//         for (Bullet b : bullets) {
//             Map<String, Object> bm = new HashMap<>();
//             bm.put("x", b.x);
//             bm.put("y", b.y);
//             bulletList.add(bm);
//         }
//         state.put("bullets", bulletList);

//         return state;
//     }

//     public static void main(String[] args) throws IOException {
//         new GameServer2().start();
//     }
// }
