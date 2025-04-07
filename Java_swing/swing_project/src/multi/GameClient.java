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
    private String playerName;

    public GameClient(String playerName) {
        this.playerName = playerName;
        setTitle("Game Client - " + playerName);
        setSize(1280, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        gamePanel = new GamePanel(playerName);
        add(gamePanel);
        addKeyListener(gamePanel);
        // 30프레임으로 다시 그리기
        new javax.swing.Timer(33, e -> gamePanel.repaint()).start();
        setFocusable(true);
        setVisible(true);

        connectToServer();
        gamePanel.setOutputStream(out);
        new Thread(new ServerListener()).start();
    }

    private void connectToServer() {
        try {
            // IP : 192.168.107.10로 수정 (학원 IP)
            socket = new Socket("192.168.107.5", 7777);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            out.writeObject(playerName);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class ServerListener implements Runnable {

        @Override
        public void run() {
            try {
                while (true) {
                    Object obj = in.readObject();
                    if (obj instanceof Map) {
                        gamePanel.updateGameState((Map<String, Object>) obj);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new GameClient("Guest" + (int) (Math.random() * 10000));
    }
}

class GamePanel extends JPanel implements KeyListener {

    private List<GameServer.Player> players = new ArrayList<>();
    private List<GameServer.Enemy> enemies = new ArrayList<>();
    private List<GameServer.Bullet> bullets = new ArrayList<>();
    private List<SkillEffect> skillEffects = new ArrayList<>();
    private Set<String> pressedKeys = new HashSet<>();

    private BufferedImage backgroundImage, runImage, idleImage, enemyImage;
    private int currentFrame = 0;
    private Timer animationTimer;
    private ObjectOutputStream out;
    private long lastAttackTime = 0, lastSkillTime = 0;
    private String playerId;
    private static final int CHARACTER_WIDTH = 50, CHARACTER_HEIGHT = 50;

    // 죽는 이펙트 관련**
    private boolean isDead = false;
    private BufferedImage deathImage;
    private List<BufferedImage> deathFrames = new ArrayList<>();
    private int deathFrameIndex = 0;
    private Timer deathAnimationTimer;
    private JButton restartButton;
    private int deathX, deathY;

    // 피격 이펙트 관련
    private boolean isHit = false;
    private Timer hitEffectTimer;
    private int hitEffectCounter = 0;

    public void setOutputStream(ObjectOutputStream out) {
        this.out = out;
    }

    public GamePanel(String playerId) {
        this.playerId = playerId;
        setDoubleBuffered(true);
        loadImages();
        animationTimer = new Timer(200, e -> {
            currentFrame = (currentFrame + 1) % 6;
            repaint();
        });

        // 피격 타이머 (100간격 6번 깜빡이면 종료)**
        hitEffectTimer = new Timer(100, e -> {
            hitEffectCounter++;
            if (hitEffectCounter >= 6) {
                isHit = false;
                hitEffectTimer.stop();
            }
            repaint();
        });
        animationTimer.start();
        new Timer(50, e -> sendInput()).start();
        new Timer(100, e -> repaint()).start();
    }

    private void loadImages() {
        try {
            backgroundImage = ImageIO.read(getClass().getResource("/images/background.png"));
            runImage = ImageIO.read(getClass().getResource("/images/character_run.png"));
            idleImage = ImageIO.read(getClass().getResource("/images/character_idle.png"));
            enemyImage = ImageIO.read(getClass().getResource("/images/slime_moving.png"));
            deathImage = ImageIO.read(getClass().getResource("/images/Pink_Monster_Death_8.png"));// 이미지 추가**
            for (int i = 0; i < 8; i++) {
                deathFrames.add(deathImage.getSubimage(i * 32, 0, 32, 32));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateGameState(Map<String, Object> state) {
        players = (List<GameServer.Player>) state.get("players");
        enemies = (List<GameServer.Enemy>) state.get("enemies");
        bullets = (List<GameServer.Bullet>) state.get("bullets");

        for (GameServer.Player p : players) {// *죽는 이펙스 실행 */
            if (p.id.equals(playerId)) {
                if (!isDead && p.isDead) {
                    deathX = p.x;
                    deathY = p.y;
                    startDeathAnimation();
                }
                // 피격 시 isHit을 타이머가 끝날 때까지 유지
                if (p.isHit) {
                    if (!isHit) {
                        isHit = true;
                        hitEffectCounter = 0;
                        hitEffectTimer.start();
                    }
                }
                isDead = p.isDead;
            }
        }
        repaint();

        if (state.containsKey("skillEvents")) {
            List<GameServer.SkillEvent> events = (List<GameServer.SkillEvent>) state.get("skillEvents");
            for (GameServer.SkillEvent event : events) {
                if (!event.playerId.equals(playerId)) {
                    skillEffects.add(new SkillEffect(event.x, event.y, event.facingRight));
                }
            }
        }
        repaint();
    }

    // 사망 애니메이션 함수**
    private void startDeathAnimation() {
        deathFrameIndex = 0;
        deathAnimationTimer = new Timer(200, e -> {
            if (deathFrameIndex < deathFrames.size() - 1) {
                deathFrameIndex++;
            } else {
                deathAnimationTimer.stop();
            }
            repaint();
        });
        deathAnimationTimer.start();
    }

    private void sendInput() {
        if (out == null)
            return;
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

    // private void updateWalkAnimation() {
    // currentFrame = (currentFrame + 1) % 6;
    // currentFrameImage = getCharacterFrame(currentFrame);
    // repaint();
    // }
    private Image getCharacterFrame(int frame) {
        int frameWidth = 32, frameHeight = 32;
        return runImage.getSubimage(frame * frameWidth, 0, frameWidth, frameHeight);
    }

    class SkillEffect {
        int x, y, currentFrame = 0;
        boolean facingRight;
        Timer timer;
        List<BufferedImage> frames = new ArrayList<>();

        public SkillEffect(int x, int y, boolean facingRight) {
            this.x = x;
            this.y = y;
            this.facingRight = facingRight;
            for (int i = 1; i <= 10; i++) {
                try {
                    frames.add(ImageIO.read(getClass().getResource("/images/Explosion_" + i + ".png")));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            timer = new Timer(100, e -> {
                currentFrame++;
                if (currentFrame >= frames.size()) {
                    timer.stop();
                    skillEffects.remove(this);
                }
                repaint();
            });
            timer.start();
        }

        public void draw(Graphics g) {
            if (currentFrame < frames.size()) {
                BufferedImage frame = frames.get(currentFrame);
                g.drawImage(facingRight ? frame : flipImage(frame), x, y, 140, 140, null);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g; // 이펙트 툴**
        g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);

        if (isDead) {
            g.drawImage(deathFrames.get(deathFrameIndex), deathX, deathY, CHARACTER_WIDTH, CHARACTER_HEIGHT, this);
            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 50));
            g.drawString("Game Over", getWidth() / 2 - 100, getHeight() / 2);

            if (restartButton == null) {
                restartButton = new JButton("Try again?");
                restartButton.setFont(new Font("Arial", Font.BOLD, 20));
                restartButton.setBounds(getWidth() / 2 - 75, getHeight() / 2 + 50, 150, 50);
                restartButton.addActionListener(e -> restartGame());
                setLayout(null);
                add(restartButton);
            }
            return;
        }

        for (GameServer.Player p : players) {
            boolean isMoving = !p.keys.isEmpty();
            Image baseImage = isMoving ? getCharacterFrame(currentFrame) : idleImage;
            Image playerImage = p.facingRight ? baseImage : flipImage((BufferedImage) baseImage);

            g.setColor(Color.WHITE);
            g.drawString(p.id, p.x, p.y + CHARACTER_HEIGHT + 10);
            drawBar(g, p.x, p.y - 15, CHARACTER_WIDTH, 10, p.health, Color.RED);
            drawBar(g, p.x, p.y - 5, CHARACTER_WIDTH, 4, p.mp, Color.BLUE);

            // 플레이어 그리는 로직 수정, 위에 기본값으로 그려주던걸 삭제하고 hitEffectCouter가 짝수이면 안보이게함
            if (!(p.isHit && (hitEffectCounter % 2 == 0))) {
                g.drawImage(playerImage, p.x, p.y, CHARACTER_WIDTH, CHARACTER_HEIGHT, this);
            }
        }
        for (GameServer.Enemy enemy : enemies) {
            g.drawImage(getEnemyFrame(enemy.frame), enemy.x, enemy.y, 100, 100, this);
        }
        for (GameServer.Bullet bullet : bullets) {
            try {
                BufferedImage bulletImg = ImageIO.read(getClass().getResource("/images/nomal_1.png"));
                g.drawImage(bulletImg, bullet.x, bullet.y, 140, 140, this);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        for (SkillEffect effect : skillEffects) {
            effect.draw(g);
        }
    }

    // 다시 시작 버튼을 눌렀을 때 서버에 재시작 요청을 보내도록 수정
    private void restartGame() {
        isDead = false;
        deathFrameIndex = 0;
        remove(restartButton);
        restartButton = null;

        if (out != null) {
            try {
                Map<String, Object> input = new HashMap<>();
                input.put("restart", true);
                out.reset();
                out.writeObject(input);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        repaint();
    }

    private void drawBar(Graphics g, int x, int y, int width, int height, int value, Color color) {
        g.setColor(Color.BLACK);
        g.drawRect(x, y, width, height);
        g.setColor(color);
        g.fillRect(x + 1, y + 1, (int) ((width - 2) * (value / 100.0)), height - 1);
    }

    private Image getEnemyFrame(int frame) {
        int frameWidth = 64, frameHeight = 64;
        return enemyImage.getSubimage(frame * frameWidth, 0, frameWidth, frameHeight);
    }

    private BufferedImage flipImage(BufferedImage image) {
        AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
        tx.translate(-image.getWidth(), 0);
        return new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR)
                .filter(image, null);
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
                if (now - lastAttackTime >= 200) {
                    sendAction("attack");
                    lastAttackTime = now;
                }
                break;
            case KeyEvent.VK_X:
                if (now - lastSkillTime >= 300) {
                    // 로컬 플레이어의 스킬 효과도 즉시 표시
                    GameServer.Player localPlayer = players.stream()
                            .filter(p -> p.id.equals(playerId))
                            .findFirst().orElse(null);
                    if (localPlayer != null && localPlayer.mp >= 10) {
                        localPlayer.mp -= 10;
                        int skillX = localPlayer.facingRight ? localPlayer.x + 70 : localPlayer.x - 160;
                        int skillY = localPlayer.y - 45;
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
            out.writeUnshared(input);
            out.flush();
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
