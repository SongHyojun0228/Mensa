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

        public String id;               // 플레이어 이름
        public int x = 100, y = 300;   // 초기 좌표
        public int health = 100;       // 체력
        public int mp = 100;           // 마나
        public boolean facingRight = true; // 오른쪽 방향 바라보는지 여부
        public long lastHitTime = 0;   // 마지막 피격 시간
        public Set<String> keys = new HashSet<>(); // 누르고 있는 키 목록
    }

    // 슬라임(Enemy) 몬스터 객체
    public static class Enemy implements Serializable {

        public int x, y;               // 위치 좌표
        public int frame = 0;          // 애니메이션 프레임 인덱스
        public boolean moving = false;
        public int attackPower = 10;   // 공격력

        public String targetId = null;           // 추적할 플레이어 ID
        public long lastTargetChangeTime = 0;    // 마지막 타겟 변경 시간
        private long lastFrameUpdate = 0;        // 마지막 프레임 갱신 시간

        public Enemy(int x, int y) {
            this.x = x;
            this.y = y;
        }

        // 타겟 플레이어를 따라 이동하고 프레임 갱신
        public void update(Player target) {
            // Window와 동일한 방식으로 캐릭터 중심 + 하단 계산
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

            // 프레임 변경은 쿨타임 적용되어 있다면 그대로 둠
            long now = System.currentTimeMillis();
            if (now - lastFrameUpdate >= 200) {
                frame = (frame + 1) % 6;
                lastFrameUpdate = now;
            }
        }
    }

    // 총알(Bullet)
    public static class Bullet implements Serializable {

        public int x, y, dx;               // 위치, 이동 방향
        public long createdTime;           // 생성 시간

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
            // 2초 지나면 제거
            return System.currentTimeMillis() - createdTime > 2000;
        }
    }

    // 서버에서 관리하는 전체 게임 상태
    // 각 클라이언트의 플레이어
    private ConcurrentHashMap<String, Player> players = new ConcurrentHashMap<>();

    // 모든 슬라임과 총알 (전역적으로 동일한 게임 세계로 가정)
    private List<Enemy> enemies = new CopyOnWriteArrayList<>();
    private List<Bullet> bullets = new CopyOnWriteArrayList<>();

    // 클라이언트별 출력 스트림 (상태 브로드캐스트용)
    private ConcurrentHashMap<String, ObjectOutputStream> clientOutputs = new ConcurrentHashMap<>();

    // 서버 시작 
    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("게임 서버 시작 (포트 " + PORT + ")");

        // 초기 슬라임 5마리 생성
        spawnEnemies(5);

        // 게임 루프 스레드 실행
        // 별도 스레드에서 게임 루프 실행 (약 60FPS)
        new Thread(() -> gameLoop()).start();

        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("클라이언트 접속: " + socket.getInetAddress());
            // 연결된 클라이언트 핸들러 실행
            new ClientHandler(socket).start();
        }
    }

    // 게임 루프 : 상태 업데이트 후 모든 클라이언트에 브로드캐스트 (30FPS)
    private void gameLoop() {
        while (true) {
            long startTime = System.currentTimeMillis();

            updatePlayers();
            updateEnemies();
            updateBullets();
            checkDamage();

            // 슬라임 주기적 생성 (10초마다 10마리)
            long now = System.currentTimeMillis();
            if (now - lastSpawnTime >= 10000) {
                spawnEnemies(10);
                lastSpawnTime = now;
            }

            // 클라이언트에 현재 게임 상태 전송
            broadcastGameState();

            long elapsed = System.currentTimeMillis() - startTime;
            try {
                // 30FPS 유지
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
        }
    }

    // 슬라임 이동 처리
    private void updateEnemies() {
        if (players.isEmpty()) {
            return;
        }

        List<Player> playerList = new ArrayList<>(players.values());
        long now = System.currentTimeMillis();
        Random rand = new Random();

        for (Enemy e : enemies) {
            // 타겟 없거나 10초 경과 시 새로 설정
            if (e.targetId == null || now - e.lastTargetChangeTime >= 10000) {
                Player newTarget = playerList.get(rand.nextInt(playerList.size()));
                e.targetId = newTarget.id;
                e.lastTargetChangeTime = now;
            }

            // 타겟 ID로 플레이어 찾기
            Player target = null;
            for (Player p : playerList) {
                if (p.id.equals(e.targetId)) {
                    target = p;
                    break;
                }
            }

            // 혹시 타겟이 없어진 경우 fallback 처리
            if (target == null) {
                target = playerList.get(0);
            }

            e.update(target);
        }
    }

    // 총알 이동 및 삭제
    private void updateBullets() {
        Iterator<Bullet> it = bullets.iterator();
        for (Bullet b : bullets) {
            b.update();
        }
        bullets.removeIf(Bullet::isExpired);
    }

    // 슬라임의 근접 공격에 의한 체력 감소 처리 (각 플레이어별)
    private void checkDamage() {
        long currentTime = System.currentTimeMillis();
        for (Player p : players.values()) {
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

            // 쿨다운: 0.5초마다만 피격 허용
            if (damage > 0 && currentTime - p.lastHitTime >= 500) {
                p.health = Math.max(0, p.health - damage);
                p.lastHitTime = currentTime;
            }
        }
    }

    // 모든 클라이언트들에 게임 상태 브로드캐스트
    private void broadcastGameState() {
        Map<String, Object> state = new HashMap<>();
        // 플레이어 상태는 List<Player>
        state.put("players", new ArrayList<>(players.values()));
        state.put("enemies", enemies);
        state.put("bullets", bullets);

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
                    spawnY = rand.nextInt(HEIGHT);
                    break;
                case 1:
                    spawnX = WIDTH;
                    spawnY = rand.nextInt(HEIGHT);
                    break;
                case 2:
                    spawnX = rand.nextInt(WIDTH);
                    spawnY = -100;
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
        Socket socket;      // 연결됐을 때 서버 측에서 생성할 소켓
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
                        Map<String, Object> input = (Map<String, Object>) obj;
                        handleInput(p, input);
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
                int bulletX = p.facingRight ? p.x + 50 - 70 : p.x - 70;
                int bulletY = p.y + 25 - 70;
                int dx = p.facingRight ? 10 : -10;
                bullets.add(new Bullet(bulletX, bulletY, dx));
            } else if ("skill".equals(action)) {
                if (p.mp >= 10) {
                    p.mp -= 10;
                    // 스킬 이펙트는 클라이언트에서 시각 효과로 처리하므로 서버에서는 MP 차감만 적용
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new GameServer().start();
    }
}
