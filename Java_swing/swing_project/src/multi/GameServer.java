// GameServer.java
package multi;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {

    // 서버소켓이 연결한 포트번호
    // 클라이언트 소켓은 이 포트번호로 요청 해야 함
    // socket = new Socket("127.0.0.1", 7777);
    private static final int PORT = 7777;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    // 마지막 슬라임 생성 시간
    private long lastSpawnTime = System.currentTimeMillis();

    // 플레이어 : 각 클라이언트가 제어하는 캐릭터
    public static class Player implements Serializable {

        public String id; // 플레이어 이름
        public int x = 100, y = 300; // 초기 좌표
        public int health = 100; // 체력
        public int mp = 100; // 마나
        public boolean facingRight = true; // 오른쪽 방향 바라보는지 여부
        public long lastHitTime = 0; // 마지막 피격 시간
        public Set<String> keys = new HashSet<>(); // 누르고 있는 키 목록
        public boolean isDead = false; // 사망 여부 추가**
        public boolean isHit = false; // 추가**
    }

    // 슬라임(Enemy) 몬스터 객체
    public static class Enemy implements Serializable {

        public int x, y; // 위치 좌표
        public int frame = 0; // 애니메이션 프레임 인덱스
        public boolean moving = false;
        public int health = 30; // 슬라임 체력 추가
        public int attackPower = 10; // 공격력
        public String targetId = null; // 추적할 플레이어 ID
        public long lastTargetChangeTime = 0; // 마지막 타겟 변경 시간
        private long lastFrameUpdate = 0; // 마지막 프레임 갱신 시간

        public Enemy(int x, int y) {
            this.x = x;
            this.y = y;
        }

        // 타겟 플레이어를 따라 이동하고 프레임 갱신
        public void update(Player target) {
            int targetX = target.x - 25;
            int targetY = target.y - 5;
            int dx = targetX - x;
            int dy = targetY - y;
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance > 0) {
                x += (int) (2 * dx / distance);
                y += (int) (2 * dy / distance);
                moving = true;
            } else {
                moving = false;
            }
            long now = System.currentTimeMillis();
            if (now - lastFrameUpdate >= 200) {
                frame = (frame + 1) % 6;
                lastFrameUpdate = now;
            }
        }
    }

    // 총알(Bullet)
    public static class Bullet implements Serializable {

        public int x, y, dx; // 위치, 이동 방향
        public long createdTime; // 생성 시간

        public Bullet(int x, int y, int dx) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.createdTime = System.currentTimeMillis();
        }

        public void update() {
            x += dx;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdTime > 2000;
        }
    }

    // 스킬 사용 이벤트를 전파하기 위한 클래스
    public static class SkillEvent implements Serializable {

        public String playerId;
        public int x, y;
        public boolean facingRight;

        public SkillEvent(String playerId, int x, int y, boolean facingRight) {
            this.playerId = playerId;
            this.x = x;
            this.y = y;
            this.facingRight = facingRight;
        }
    }

    private ConcurrentHashMap<String, Player> players = new ConcurrentHashMap<>();
    private List<Enemy> enemies = new CopyOnWriteArrayList<>();
    private List<Bullet> bullets = new CopyOnWriteArrayList<>();
    // 스킬 이벤트 목록 (브로드캐스트 후 초기화)
    private List<SkillEvent> skillEvents = new CopyOnWriteArrayList<>();
    private ConcurrentHashMap<String, ObjectOutputStream> clientOutputs = new ConcurrentHashMap<>();

    // 게임을 초기 상태로 리셋하는 메서드 추가**
    private void resetGame() {
        players.clear();
        enemies.clear();
        bullets.clear();
        skillEvents.clear();
        System.out.println("게임이 초기화되었습니다.");
    }

    // 서버 시작
    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("게임 서버 시작 (포트 " + PORT + ")");
        spawnEnemies(10);
        new Thread(() -> gameLoop()).start();
        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("클라이언트 접속: " + socket.getInetAddress());
            new ClientHandler(socket).start();
        }
    }

    // 게임 루프 : 상태 업데이트 후 모든 클라이언트에 브로드캐스트 (30FPS)
    private void gameLoop() {
        while (true) {
            if (players.isEmpty()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }

            long startTime = System.currentTimeMillis();
            updatePlayers();
            updateEnemies();
            updateBullets();
            checkDamage();
            checkBulletEnemyCollision();
            long now = System.currentTimeMillis();
            if (now - lastSpawnTime >= 10000) {
                spawnEnemies(10);
                lastSpawnTime = now;
            }
            broadcastGameState();
            long elapsed = System.currentTimeMillis() - startTime;
            try {
                Thread.sleep(Math.max(0, 33 - elapsed));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // 키 입력에 따라 플레이어 위치 이동
    // 각 플레이어의 입력에 따라 위치 업데이트
    private void updatePlayers() {
        for (Player p : players.values()) {
            int dx = 0, dy = 0;
            for (String key : p.keys) {
                switch (key) {
                    case "LEFT":
                        dx -= 5;
                        p.facingRight = false;
                        break;
                    case "RIGHT":
                        dx += 5;
                        p.facingRight = true;
                        break;
                    case "UP":
                        dy -= 5;
                        break;
                    case "DOWN":
                        dy += 5;
                        break;
                }
            }
            if (dx != 0 && dy != 0) {
                dx = (int) (dx * 0.7071);
                dy = (int) (dy * 0.7071);
            }
            p.x += dx;
            p.y += dy;
            p.x = Math.max(0, Math.min(p.x, WIDTH - 50)); // 50은 캐릭터 가로 크기
            p.y = Math.max(130, Math.min(p.y, HEIGHT - 50)); // 50은 캐릭터 세로 크기
        }
    }

    // 슬라임 이동 처리
    private void updateEnemies() {
        if (players.isEmpty())
            return;
        List<Player> playerList = new ArrayList<>(players.values());
        long now = System.currentTimeMillis();
        Random rand = new Random();
        for (Enemy e : enemies) {
            if (e.targetId == null || now - e.lastTargetChangeTime >= 10000) {
                Player newTarget = playerList.get(rand.nextInt(playerList.size()));
                e.targetId = newTarget.id;
                e.lastTargetChangeTime = now;
            }
            Player target = playerList.stream().filter(p -> p.id.equals(e.targetId)).findFirst()
                    .orElse(playerList.get(0));
            e.update(target);
        }
    }

    // 총알 이동 및 삭제
    private void updateBullets() {
        for (Bullet b : bullets) {
            b.update();
        }
        bullets.removeIf(Bullet::isExpired);
    }

    // 슬라임의 근접 공격에 의한 체력 감소 처리 (각 플레이어별)
    private void checkDamage() {
        long currentTime = System.currentTimeMillis();
        for (Player p : players.values()) {
            if (p.isDead)
                continue; // 이미 죽은 경우 무시**
            int centerX = p.x + 25;
            int centerY = p.y + 25;
            int hitLeft = centerX - 25;
            int hitRight = centerX + 25;
            int hitTop = centerY - 45;
            int hitBottom = centerY + 20;
            int count = 0;
            for (Enemy e : enemies) {
                int ex = e.x + 50;
                int ey = e.y + 50;
                if (ex >= hitLeft && ex <= hitRight && ey >= hitTop && ey <= hitBottom) {
                    count++;
                }
            }
            int effective = Math.min(count, 5);
            int damage = effective * (effective > 0 ? 10 : 0);
            if (damage > 0 && currentTime - p.lastHitTime >= 500) {
                p.health = Math.max(0, p.health - damage);
                p.lastHitTime = currentTime;
                p.isHit = true; // 피격 효과 활성화**
                if (p.health == 0) {
                    p.isDead = true; // 체력이 0이면 사망 처리**
                }
            }

            // 일정 시간동안은 isHit을 유지하도록 변경
            if (currentTime - p.lastHitTime >= 600) {
                p.isHit = false;
            }
        }
    }

    // 모든 클라이언트들에 게임 상태 브로드캐스트
    private void broadcastGameState() {
        Map<String, Object> state = new HashMap<>();
        state.put("players", new ArrayList<>(players.values()));
        state.put("enemies", enemies);
        state.put("bullets", bullets);
        state.put("skillEvents", new ArrayList<>(skillEvents));
        skillEvents.clear();
        for (ObjectOutputStream out : clientOutputs.values()) {
            try {
                out.reset();
                out.writeObject(state);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 지정한 개수의 슬라임 생성 (맵 외곽에서 등장)
    private void spawnEnemies(int count) {
        Random rand = new Random();
        for (int i = 0; i < count; i++) {
            int side = rand.nextInt(4);
            int spawnX = 0, spawnY = 0;
            switch (side) {
                case 0:
                    spawnX = -100;
                    spawnY = 130 + rand.nextInt(HEIGHT - 130);
                    break;
                case 1:
                    spawnX = WIDTH;
                    spawnY = 130 + rand.nextInt(HEIGHT - 130);
                    break;
                case 2:
                    spawnX = rand.nextInt(WIDTH);
                    spawnY = HEIGHT;
                    break;
                case 3:
                    spawnX = rand.nextInt(WIDTH);
                    spawnY = HEIGHT;
                    break;
            }
            enemies.add(new Enemy(spawnX, spawnY));
        }
    }

    // 클라이언트 연결 요청 처리 스레드
    class ClientHandler extends Thread {
        Socket socket; // 연결됐을 때 서버 측에서 생성할 소켓
        ObjectInputStream in;
        ObjectOutputStream out;
        String playerId;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                playerId = (String) in.readObject();
                System.out.println("플레이어 접속: " + playerId);
                Player p = new Player();
                p.id = playerId;
                players.put(playerId, p);
                clientOutputs.put(playerId, out);
                while (true) {
                    Object obj = in.readObject();
                    if (obj instanceof Map) {
                        handleInput(p, (Map<String, Object>) obj);
                    }
                }
            } catch (Exception e) {
                System.out.println("플레이어 종료: " + playerId);
            } finally {
                if (playerId != null) {
                    players.remove(playerId);
                    clientOutputs.remove(playerId);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }

    // 클라이언트 입력 처리 : 키 목록과 액션("attack", "skill") 처리
    private void handleInput(Player p, Map<String, Object> input) {
        if (input.containsKey("keys")) {
            Object keysObj = input.get("keys");
            if (keysObj instanceof List) {
                List<String> keys = (List<String>) keysObj;
                p.keys = new HashSet<>(keys);
            }
        }
        if (input.containsKey("action")) {
            String action = (String) input.get("action");
            if ("attack".equals(action)) {
                // 기존 총알 공격 처리 그대로 유지
                int bulletX = p.facingRight ? p.x + 50 - 70 : p.x - 70;
                int bulletY = p.y + 25 - 70;
                int dx = p.facingRight ? 10 : -10;
                bullets.add(new Bullet(bulletX, bulletY, dx));
            } else if ("skill".equals(action)) {
                // ✅ 스킬 데미지 처리
                int skillCenterX = p.facingRight ? p.x + 70 + 70 : p.x - 160 + 70;
                int skillCenterY = p.y - 45 + 70;
                for (Enemy e : enemies) {
                    int ex = e.x + 50;
                    int ey = e.y + 50;
                    int distance = (int) Math.sqrt(Math.pow(skillCenterX - ex, 2) + Math.pow(skillCenterY - ey, 2));
                    if (distance < 70) {
                        e.health -= 30;
                    }
                }
                enemies.removeIf(e -> e.health <= 0);
                if (p.mp >= 10) {
                    p.mp -= 10;
                    int skillX = p.facingRight ? p.x + 70 : p.x - 160;
                    int skillY = p.y - 45;
                    // 스킬 이벤트를 생성하여 모든 클라이언트에 전파
                    skillEvents.add(new SkillEvent(p.id, skillX, skillY, p.facingRight));
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new GameServer().start();
    }

    // ✅ 총알과 슬라임 충돌 처리 (총알 데미지: 5)
    private void checkBulletEnemyCollision() {
        List<Enemy> deadEnemies = new ArrayList<>();
        for (Bullet b : bullets) {
            for (Enemy e : enemies) {
                int bx = b.x + 70;
                int by = b.y + 70;
                int ex = e.x + 50;
                int ey = e.y + 50;
                int distance = (int) Math.sqrt(Math.pow(bx - ex, 2) + Math.pow(by - ey, 2));
                if (distance < 50) {
                    e.health -= 5;
                    b.createdTime = 0;
                    if (e.health <= 0 && !deadEnemies.contains(e)) {
                        deadEnemies.add(e);

                        // ✅ 슬라임이 죽은 시점에 가장 가까운 플레이어에게 mp 회복
                        Player nearest = null;
                        double minDist = Double.MAX_VALUE;
                        for (Player p : players.values()) {
                            double d = Math.hypot(p.x - e.x, p.y - e.y);
                            if (d < minDist) {
                                minDist = d;
                                nearest = p;
                            }
                        }
                        if (nearest != null) {
                            nearest.mp = Math.min(100, nearest.mp + 1); // 🔹 최대 100 제한
                        }
                    }
                }
            }
        }
        enemies.removeAll(deadEnemies);
    }

}