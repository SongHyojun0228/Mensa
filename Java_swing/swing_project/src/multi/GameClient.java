// GameClient.java
package multi;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;

public class GameClient extends JFrame {

    private GamePanel gamePanel;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Socket socket;

    // 플레이어 이름(자동 생성)
    private String playerName;

    public GameClient(String playerName) {
        this.playerName = playerName;    // 사용자 이름 설정
        setTitle("Game Client - " + playerName);
        setSize(1280, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 패널 생성 및 설정
        gamePanel = new GamePanel(playerName);
        add(gamePanel);
        addKeyListener(gamePanel);
        setFocusable(true);
        setVisible(true);

        // 서버 연결 
        connectToServer();

        // GamePanel에서 서버로 키 입력 등을 전송할 수 있도록 out 스트림 전달
        gamePanel.setOutputStream(out);

        // 서버로부터 상태 수신하는 리스너 스레드 실행
        new Thread(new ServerListener()).start();
    }

    private void connectToServer() {
        try {
            // 서버 IP/포트는 상황에 맞게 수정
            // 서버 IP 주소 입력 필요 (자기 자신이면 127.0.0.1)
            // IP : 192.168.107.10 로 수정 (학원 IP)
            socket = new Socket("192.168.107.10", 7777);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // 플레이어 이름 서버로 전송
            out.writeObject(playerName);
            out.flush();

            // 게임 패널 생성 및 추가
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 서버에서 지속적으로 상태를 받는 스레드
    class ServerListener implements Runnable {

        @Override
        public void run() {
            try {
                while (true) {
                    Object obj = in.readObject();
                    if (obj instanceof Map) {
                        Map<String, Object> state = (Map<String, Object>) obj;
                        // 수신한 게임 상태를 패널에 반영
                        gamePanel.updateGameState(state);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        String randomName = "Guest" + (int) (Math.random() * 10000);
        new GameClient(randomName);
    }
}

// 게임 상태를 그리고 클라이언트 입력(키/액션)을 서버로 전송하는 패널
class GamePanel extends JPanel implements KeyListener {

    private List<GameServer.Player> players = new ArrayList<>();

    private BufferedImage backgroundImage;
    private BufferedImage runImage;
    private BufferedImage idleImage;
    private BufferedImage enemyImage;
    private Image currentFrameImage;
    private final int CHARACTER_WIDTH = 50;
    private final int CHARACTER_HEIGHT = 50;

    private int currentFrame = 0;
    private Timer animationTimer;

    // 플레이어 체력/마나
    private int playerHealth = 100;
    private int playerMP = 100;

    // 로컬에서 플레이어의 방향 저장 (서버에서 업데이트된 값을 사용)
    private boolean facingRight = true;

    // 서버에서 수신한 슬라임과 총알 정보
    private List<GameServer.Enemy> enemies = new ArrayList<>();
    private List<GameServer.Bullet> bullets = new ArrayList<>();

    // 스킬 이펙트 리스트 (클라이언트 전용 시각 효과)
    private List<SkillEffect> skillEffects = new ArrayList<>();

    // 클라이언트가 누른 키 목록 (서버에 전송)
    private Set<String> pressedKeys = new HashSet<>();

    // 서버에 입력을 전송하기 위한 출력 스트림 (GameClient에서 설정)
    private ObjectOutputStream out;

    // Cool Down
    private long lastAttackTime = 0;
    private long lastSkillTime = 0;

    public void setOutputStream(ObjectOutputStream out) {
        this.out = out;
    }

    private String playerId;  // 내 플레이어 ID 저장

    public GamePanel(String playerId) {
        this.playerId = playerId;

        setDoubleBuffered(true);
        try {
            backgroundImage = ImageIO.read(getClass().getResource("/images/5.png"));
            runImage = ImageIO.read(getClass().getResource("/images/Pink_Monster_Run_6.png"));
            idleImage = ImageIO.read(getClass().getResource("/images/Pink_Monster.png"));
            enemyImage = ImageIO.read(getClass().getResource("/images/Slime1_Walk_full[1].png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        currentFrameImage = idleImage;

        // 캐릭터 달리기 애니메이션 타이머 (200ms 간격으로 프레임 변경)
        animationTimer = new Timer(200, e -> updateWalkAnimation());

        // 입력 반복 전송 (50ms 간격)
        new Timer(50, e -> sendInput()).start();

        // 화면 갱신 (100ms 간격)
        new Timer(100, e -> repaint()).start();
    }

    // 서버로부터 전달받은 게임 상태 업데이트
    public void updateGameState(Map<String, Object> state) {
        players = (List<GameServer.Player>) state.get("players");
        enemies = (List<GameServer.Enemy>) state.get("enemies");
        bullets = (List<GameServer.Bullet>) state.get("bullets");
        repaint();
    }

    private void sendInput() {
        if (out == null) {
            return;
        }
        try {
            Map<String, Object> input = new HashMap<>();
            input.put("keys", new ArrayList<>(pressedKeys));
            out.reset();
            out.writeObject(input);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateWalkAnimation() {
        currentFrame = (currentFrame + 1) % 6;
        currentFrameImage = getCharacterFrame(currentFrame);
        repaint();
    }

    private Image getCharacterFrame(int frame) {
        int frameWidth = 32;
        int frameHeight = 32;
        int frameX = frame * frameWidth;
        int frameY = 0;
        return runImage.getSubimage(frameX, frameY, frameWidth, frameHeight);
    }

    // 스킬 이펙트 클래스 (클라이언트 전용 시각 효과)
    class SkillEffect {

        int x, y;
        boolean facingRight;
        int currentFrame = 0;
        Timer animationTimer;
        List<BufferedImage> frames = new ArrayList<>();

        public SkillEffect(int x, int y, boolean facingRight) {
            this.x = x;
            this.y = y;
            this.facingRight = facingRight;
            for (int i = 1; i <= 10; i++) {
                try {
                    BufferedImage img = ImageIO.read(getClass().getResource("/images/Explosion_" + i + ".png"));
                    frames.add(img);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            animationTimer = new Timer(100, e -> {
                currentFrame++;
                if (currentFrame >= frames.size()) {
                    animationTimer.stop();
                    skillEffects.remove(this);
                }
                repaint();
            });
            animationTimer.start();
        }

        public void draw(Graphics g) {
            if (currentFrame < frames.size()) {
                BufferedImage frame = frames.get(currentFrame);
                BufferedImage displayedFrame = facingRight ? frame : flipImage(frame);
                g.drawImage(displayedFrame, x, y, 140, 140, null);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // 배경 그리기
        g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);

        if (!players.isEmpty()) {
            for (GameServer.Player p : players) {
                boolean isLocal = p.equals(players.get(0));
                boolean isMoving = !p.keys.isEmpty();  // 키 입력 여부로 움직임 판단

                // 본인 캐릭터인 경우에만 애니메이션 처리
                Image baseImage;
                if (isLocal) {
                    baseImage = isMoving ? currentFrameImage : idleImage;

                    // 애니메이션 타이머 조정
                    if (isMoving && !animationTimer.isRunning()) {
                        animationTimer.start();
                    } else if (!isMoving && animationTimer.isRunning()) {
                        animationTimer.stop();
                        currentFrameImage = idleImage;
                    }
                } else {
                    baseImage = idleImage; // 다른 플레이어는 기본 이미지 사용
                }

                Image playerImage = p.facingRight ? baseImage : flipImage((BufferedImage) baseImage);
                g.drawImage(playerImage, p.x, p.y, CHARACTER_WIDTH, CHARACTER_HEIGHT, this);

                // 플레이어 이름 표시
                g.setColor(Color.WHITE);
                g.drawString(p.id, p.x, p.y - 10);

                // 체력바, 마나바
                drawHealthBar(g, p.x, p.y, p.health);
                drawMPBar(g, p.x, p.y, p.mp);
            }
        }

        // 슬라임(Enemy) 그리기
        if (enemies != null) {
            for (GameServer.Enemy enemy : enemies) {
                int ex = enemy.x;
                int ey = enemy.y;
                int frame = enemy.frame;
                g.drawImage(getEnemyFrame(frame), ex, ey, 100, 100, this);
            }
        }

        // 총알 그리기
        if (bullets != null) {
            for (GameServer.Bullet bullet : bullets) {
                int bx = bullet.x;
                int by = bullet.y;
                try {
                    BufferedImage bulletImg = ImageIO.read(getClass().getResource("/images/nomal_1.png"));
                    g.drawImage(bulletImg, bx, by, 140, 140, this);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // 스킬 이펙트 그리기
        for (SkillEffect effect : skillEffects) {
            effect.draw(g);
        }
    }

    private void drawHealthBar(Graphics g, int x, int y, int health) {
        int barWidth = CHARACTER_WIDTH;
        int barHeight = 10;
        int barX = x;
        int barY = y - barHeight - 5;
        g.setColor(Color.BLACK);
        g.drawRect(barX, barY, barWidth, barHeight);
        int healthWidth = (int) (barWidth * (health / 100.0));
        g.setColor(Color.RED);
        g.fillRect(barX + 1, barY + 1, healthWidth - 1, barHeight - 1);
    }

    // 플레이어 마나바를 그리는 메서드 (플레이어 좌표와 MP 값 사용)
    private void drawMPBar(Graphics g, int x, int y, int mp) {
        int barWidth = CHARACTER_WIDTH;
        int barHeight = 4;
        int barX = x;
        int barY = y - 5 - 10 + 10;
        g.setColor(Color.BLACK);
        g.drawRect(barX, barY, barWidth, barHeight);
        int mpWidth = (int) (barWidth * (mp / 100.0));
        g.setColor(Color.BLUE);
        g.fillRect(barX + 1, barY + 1, mpWidth - 1, barHeight - 1);
    }

    private Image getEnemyFrame(int frame) {
        int frameWidth = 64;
        int frameHeight = 64;
        int frameX = frame * frameWidth;
        int frameY = 0;
        return enemyImage.getSubimage(frameX, frameY, frameWidth, frameHeight);
    }

    // 좌우 반전 이미지 처리
    private BufferedImage flipImage(BufferedImage image) {
        AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
        tx.translate(-image.getWidth(), 0);
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(image, null);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        long now = System.currentTimeMillis();

        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT:
                pressedKeys.add("LEFT");
                break;
            case KeyEvent.VK_RIGHT:
                pressedKeys.add("RIGHT");
                break;
            case KeyEvent.VK_UP:
                pressedKeys.add("UP");
                break;
            case KeyEvent.VK_DOWN:
                pressedKeys.add("DOWN");
                break;
            case KeyEvent.VK_Z:
                if (now - lastAttackTime >= 200) { // 0.2초 쿨다운
                    sendAction("attack");
                    lastAttackTime = now;
                }
                break;
            case KeyEvent.VK_X:
                if (now - lastSkillTime >= 300) { // 0.3초 쿨다운
                    GameServer.Player localPlayer = null;

                    // 🔍 내 ID와 일치하는 플레이어 탐색
                    for (GameServer.Player p : players) {
                        if (p.id.equals(playerId)) {
                            localPlayer = p;
                            break;
                        }
                    }

                    if (localPlayer != null && localPlayer.mp >= 10) {
                        localPlayer.mp -= 10;
                        int skillX = localPlayer.facingRight ? localPlayer.x + CHARACTER_WIDTH + 20 : localPlayer.x - 140 - 20;
                        int skillY = localPlayer.y + CHARACTER_HEIGHT / 2 - 70;
                        skillEffects.add(new SkillEffect(skillX, skillY, localPlayer.facingRight));
                        sendAction("skill");
                        lastSkillTime = now;
                    }
                }
                break;

        }
    }

    private void sendAction(String action) {
        if (out == null) {
            return;
        }
        try {
            Map<String, Object> input = new HashMap<>();
            input.put("action", action);
            out.writeUnshared(input);  // writeObject보다 가볍고 버벅임 감소
            out.flush();               // flush는 유지
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT:
                pressedKeys.remove("LEFT");
                break;
            case KeyEvent.VK_RIGHT:
                pressedKeys.remove("RIGHT");
                break;
            case KeyEvent.VK_UP:
                pressedKeys.remove("UP");
                break;
            case KeyEvent.VK_DOWN:
                pressedKeys.remove("DOWN");
                break;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }
}
