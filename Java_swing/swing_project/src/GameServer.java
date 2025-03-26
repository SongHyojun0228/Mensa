
// // GameServer.java (슬라임 데미지 타이머 추가 - GameWindow와 HP 감소 속도 일치)
// import java.io.*;
// import java.net.*;
// import java.util.*;
// import java.util.concurrent.*;

// public class GameServer {
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
//         int mp = 100; // MP 추가
//         int x = 100;
//         int y = 300;
//         int health = 100;
//         boolean facingRight = true;
//         Set<String> keys = new HashSet<>();
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

//     private Player player = new Player();
//     private List<Enemy> enemies = new CopyOnWriteArrayList<>();
//     private List<Bullet> bullets = new CopyOnWriteArrayList<>();

//     private Timer enemyAnimationTimer; // 슬라임 애니메이션 속도 조절용
//     private Timer damageTimer; // 데미지 적용 타이머 추가

//     public void start() throws IOException {
//         ServerSocket serverSocket = new ServerSocket(PORT);
//         System.out.println("서버 실행, 클라이언트 대기 중...2003");

//         Socket client = serverSocket.accept();
//         System.out.println("클라이언트 연결완료 : " + client.getInetAddress());

//         ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
//         ObjectInputStream in = new ObjectInputStream(client.getInputStream());

//         spawnEnemies(5);

//         Timer loop = new Timer();
//         loop.scheduleAtFixedRate(new TimerTask() {
//             public void run() {
//                 updateGameState();
//                 try {
//                     out.reset();
//                     out.writeObject(createGameState());
//                     out.flush();
//                 } catch (IOException e) {
//                     System.err.println("클라이언트에 상태 전송 실패");
//                     cancel();
//                 }
//             }
//         }, 0, 16); // 60 FPS

//         enemyAnimationTimer = new Timer();
//         enemyAnimationTimer.scheduleAtFixedRate(new TimerTask() {
//             @Override
//             public void run() {
//                 for (Enemy e : enemies) {
//                     if (e.moving) {
//                         e.frame = (e.frame + 1) % 6;
//                     }
//                 }
//             }
//         }, 0, 200); // GameWindow와 동일하게 200ms 주기

//         damageTimer = new Timer();
//         damageTimer.scheduleAtFixedRate(new TimerTask() {
//             @Override
//             public void run() {
//                 checkDamage();
//             }
//         }, 0, 500); // GameWindow 기준 0.5초마다 데미지 적용

//         while (true) {
//             try {
//                 Object obj = in.readObject();
//                 if (obj instanceof Map) {
//                     @SuppressWarnings("unchecked")
//                     Map<String, Object> input = (Map<String, Object>) obj;
//                     handleInput(input);
//                 }
//             } catch (Exception e) {
//                 System.out.println("클라이언트 연결 종료");
//                 break;
//             }
//         }

//         loop.cancel();
//         enemyAnimationTimer.cancel();
//         damageTimer.cancel();
//         serverSocket.close();
//     }

//     private void handleInput(Map<String, Object> input) {
//         Object keys = input.get("keys");
//         if (keys instanceof List<?>) {
//             player.keys = new HashSet<>((List<String>) keys);
//         }

//         Object action = input.get("action");
//         if ("skill".equals(action)) {
//             if (player.mp >= 10)
//                 player.mp -= 10;
//         } else if ("attack".equals(action)) {
//             int bulletX = player.facingRight ? player.x + CHARACTER_WIDTH - BULLET_SIZE / 2
//                     : player.x - BULLET_SIZE / 2;
//             int bulletY = player.y + CHARACTER_HEIGHT / 2 - BULLET_SIZE / 2 - 13;
//             int dx = player.facingRight ? BULLET_SPEED : -BULLET_SPEED;
//             bullets.add(new Bullet(bulletX, bulletY, dx));
//         }
//     }

//     private void updateGameState() {
//         updatePlayer();
//         updateEnemies();
//         updateBullets();
//     }

//     private void updatePlayer() {
//         int dx = 0, dy = 0;
//         for (String key : player.keys) {
//             switch (key) {
//                 case "LEFT": {
//                     dx -= PLAYER_MOVE_SPEED;
//                     player.facingRight = false;
//                     break;
//                 }
//                 case "RIGHT": {
//                     dx += PLAYER_MOVE_SPEED;
//                     player.facingRight = true;
//                     break;
//                 }
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
//             int targetX = player.x - CHARACTER_WIDTH / 2;
//             int targetY = player.y - CHARACTER_HEIGHT / 2 + 20;
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

//     private void updateBullets() {
//         bullets.removeIf(Bullet::isExpired);
//         for (Bullet b : bullets) {
//             b.x += b.dx;
//         }
//     }

//     private void checkDamage() {
//         int centerX = player.x + CHARACTER_WIDTH / 2;
//         int centerY = player.y + CHARACTER_HEIGHT / 2;
//         int hitLeft = centerX - 25;
//         int hitRight = centerX + 25;
//         int hitTop = centerY - 45;
//         int hitBottom = centerY + 20;
//         int count = 0;
//         for (Enemy e : enemies) {
//             int ex = e.x + ENEMY_SIZE / 2;
//             int ey = e.y + ENEMY_SIZE / 2;
//             if (ex >= hitLeft && ex <= hitRight && ey >= hitTop && ey <= hitBottom) {
//                 count++;
//             }
//         }
//         int effective = Math.min(count, 5);
//         int damage = effective * 10;
//         player.health = Math.max(0, player.health - damage);
//     }

//     private void spawnEnemies(int count) {
//         Random rand = new Random();
//         for (int i = 0; i < count; i++) {
//             int side = rand.nextInt(4);
//             int spawnX = 0, spawnY = 0;
//             switch (side) {
//                 case 0: {
//                     spawnX = -ENEMY_SIZE;
//                     spawnY = rand.nextInt(HEIGHT);
//                     break;
//                 }
//                 case 1: {
//                     spawnX = WIDTH;
//                     spawnY = rand.nextInt(HEIGHT);
//                     break;
//                 }
//                 case 2: {
//                     spawnX = rand.nextInt(WIDTH);
//                     spawnY = -ENEMY_SIZE;
//                     break;
//                 }
//                 case 3: {
//                     spawnX = rand.nextInt(WIDTH);
//                     spawnY = HEIGHT;
//                     break;
//                 }
//             }
//             enemies.add(new Enemy(spawnX, spawnY));
//         }
//     }

//     private Map<String, Object> createGameState() {
//         Map<String, Object> state = new HashMap<>();

//         state.put("playerX", player.x);
//         state.put("playerY", player.y);
//         state.put("health", player.health);
//         state.put("facingRight", player.facingRight);

//         List<Map<String, Object>> enemyList = new ArrayList<>();
//         for (Enemy e : enemies) {
//             Map<String, Object> enemyMap = new HashMap<>();
//             enemyMap.put("x", e.x);
//             enemyMap.put("y", e.y);
//             enemyMap.put("frame", e.frame);
//             enemyList.add(enemyMap);
//         }
//         state.put("enemies", enemyList);

//         List<Map<String, Object>> bulletList = new ArrayList<>();
//         for (Bullet b : bullets) {
//             Map<String, Object> bulletMap = new HashMap<>();
//             bulletMap.put("x", b.x);
//             bulletMap.put("y", b.y);
//             bulletList.add(bulletMap);
//         }
//         state.put("bullets", bulletList);

//         return state;
//     }

//     public static void main(String[] args) throws IOException {
//         new GameServer().start();
//     }
// }
