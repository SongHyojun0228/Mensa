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
            socket = new Socket("127.0.0.1", 7777);
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
    
    private boolean showClear = false; // 🔹 클리어 출력용
    private long gameStartTime = System.currentTimeMillis(); // 🔹 생존 시간 표시용

    
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

    private List<DeathEffect> deathEffects = new ArrayList<>();
    int myScore = 0;
    List<ScoreText> scoreTexts = new ArrayList<>();
    Set<Long> handledKillEffects = new HashSet<>();
    Set<String> handledKillEffectIds = new HashSet<>();

    public void setOutputStream(ObjectOutputStream out) {
        this.out = out;
    }

    public class ScoreText {
        int x, y;
        long startTime;
        int duration = 1000; // 1초 동안 표시
        String text;

        public ScoreText(int x, int y, String text) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.startTime = System.currentTimeMillis();
        }

        public boolean isAlive() {
            return System.currentTimeMillis() - startTime < duration;
        }

        public int getYOffset() {
            return (int) ((System.currentTimeMillis() - startTime) / 10); // 시간에 따라 떠오르게
        }
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

    long lastKillHandledTime = 0;

    public void updateGameState(Map<String, Object> state) {
        players = (List<GameServer.Player>) state.get("players");
        enemies = (List<GameServer.Enemy>) state.get("enemies");
        bullets = (List<GameServer.Bullet>) state.get("bullets");

        for (GameServer.Player p : players) {// *죽는 이펙스 실행 */

            for (GameServer.KillEffect ke : p.killEffects) {
                if (!handledKillEffectIds.contains(ke.uuid)) {
                    scoreTexts.add(new ScoreText(ke.x, ke.y, "+100"));
                    handledKillEffectIds.add(ke.uuid);
                }
            }

            if (p.isDead) {
                boolean alreadyAdded = deathEffects.stream().anyMatch(d -> d.startTime == p.deathTime && d.x == p.x);
                if (!alreadyAdded) {
                    deathEffects.add(new DeathEffect(p.x, p.y, p.deathTime));
                }
            }

            if (p.id.equals(playerId)) {
                if (!isDead && p.isDead) {
                	if (p.isClear) {
                        showClear = true; // 🔹 클리어 플래그 true
                    }
                    deathX = p.x;
                    deathY = p.y;
                    startDeathAnimation();
                }

                Iterator<GameServer.KillEffect> it = p.killEffects.iterator();
                while (it.hasNext()) {
                    GameServer.KillEffect ke = it.next();
                    if (!handledKillEffects.contains(ke.time)) {
                        scoreTexts.add(new ScoreText(ke.x, ke.y, "+100"));
                        handledKillEffects.add(ke.time);
                    }
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
                myScore = p.score;
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

    public class DeathEffect {
        int x, y;
        long startTime;
        int frameIndex = 0;
        boolean finished = false;
        private static final int FRAME_COUNT = 8;
        private static final int FRAME_DURATION = 150;

        public DeathEffect(int x, int y, long startTime) {
            this.x = x;
            this.y = y;
            this.startTime = startTime;
        }

        public void update() {
            long elapsed = System.currentTimeMillis() - startTime;
            frameIndex = (int) (elapsed / FRAME_DURATION);
            if (frameIndex >= FRAME_COUNT) {
                finished = true;
            }
        }

        public void draw(Graphics g, List<BufferedImage> deathFrames) {
            if (!finished && frameIndex < deathFrames.size()) {
                g.drawImage(deathFrames.get(frameIndex), x, y, 50, 50, null);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g; // 이펙트 툴**
        g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);

        if (isDead) {
            if (showClear) {
                g.setColor(Color.GREEN);
                g.setFont(new Font("Arial", Font.BOLD, 50));
                g.drawString("CLEAR", getWidth() / 2 - 100, getHeight() / 2);
            } else {
                g.drawImage(deathFrames.get(deathFrameIndex), deathX, deathY, CHARACTER_WIDTH, CHARACTER_HEIGHT, this);
                g.setColor(Color.RED);
                g.setFont(new Font("Arial", Font.BOLD, 50));
                g.drawString("Game Over", getWidth() / 2 - 100, getHeight() / 2);
            }
            
            // 🔹 점수와 잡은 슬라임 수 표시
            g.setColor(Color.YELLOW);
            g.setFont(new Font("Dialog", Font.BOLD, 28));
            String resultText = "Score: " + myScore + "  |  잡은 슬라임 수: " + (myScore / 100);
            int resultWidth = g.getFontMetrics().stringWidth(resultText);
            g.drawString(resultText, (getWidth() - resultWidth) / 2, getHeight() / 2 + 50);


            if (restartButton == null) {
                restartButton = new JButton("Try again?");
                restartButton.setFont(new Font("Arial", Font.BOLD, 20));
                restartButton.setBounds(getWidth() / 2 - 75, getHeight() / 2 + 100, 150, 50);  // 위치 조금 내림
                // 메뉴으로 이동
                restartButton.addActionListener(e -> {
                    JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
                    topFrame.dispose(); // 현재 창 닫기
                    new GameMainMenu(); // 메인 메뉴 열기
                });

                setLayout(null);
                add(restartButton);
            }
            return;
        }


        for (GameServer.Player p : players) {
            if (p.isDead)
                continue; // 죽은 플레이어는 다시 안그릴거임

            boolean isMoving = !p.keys.isEmpty();
            Image baseImage = isMoving ? getCharacterFrame(currentFrame) : idleImage;
            Image playerImage = p.facingRight ? baseImage : flipImage((BufferedImage) baseImage);

            // 이름을 캐릭터 중심에 정렬되도록 조정**
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(p.id);
            int nameX = p.x + (CHARACTER_WIDTH - textWidth) / 2;
            g.setColor(Color.WHITE);
            g.drawString(p.id, nameX, p.y + CHARACTER_HEIGHT + 10);

            drawBar(g, p.x, p.y - 15, CHARACTER_WIDTH, 10, p.health, Color.RED);
            drawBar(g, p.x, p.y - 5, CHARACTER_WIDTH, 4, p.mp, Color.BLUE);

            // 플레이어 그리는 로직 수정, 위에 기본값으로 그려주던걸 삭제하고 hitEffectCouter가 짝수이면 안보이게함
            if (p.isDead) {
                int frameIdx = deathFrameIndex % deathFrames.size(); // 모든 죽은 플레이어에 공통 deathFrame 사용
                g.drawImage(deathFrames.get(frameIdx), p.x, p.y, CHARACTER_WIDTH, CHARACTER_HEIGHT, this);
            } else {
                if (!(p.isHit && (hitEffectCounter % 2 == 0))) {
                    g.drawImage(playerImage, p.x, p.y, CHARACTER_WIDTH, CHARACTER_HEIGHT, this);
                }
            }
        }

        // 뒤지는 플레이어 같이 그려주기
        Iterator<DeathEffect> it = deathEffects.iterator();
        while (it.hasNext()) {
            DeathEffect d = it.next();
            d.update();
            d.draw(g, deathFrames);
            if (d.finished) {
                it.remove();
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

        // 점수 텍스트 표시
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.setColor(Color.YELLOW);
        Iterator<ScoreText> iter = scoreTexts.iterator();
        while (iter.hasNext()) {
            ScoreText st = iter.next();
            if (st.isAlive()) {
                g.drawString(st.text, st.x, st.y - st.getYOffset());
            } else {
                iter.remove();
            }
        }

        // 총 점수 왼쪽 위에 표시
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.drawString("Score: " + myScore, 20, 30);
        
        // 🔹 생존 시간 화면 상단 중앙에 출력
        long elapsed = System.currentTimeMillis() - gameStartTime;
        int seconds = (int)(elapsed / 1000) % 60;
        int minutes = (int)(elapsed / 1000) / 60;
        String timerText = String.format("%02d:%02d", minutes, seconds);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 28));
        int textWidth = g.getFontMetrics().stringWidth(timerText);
        g.drawString(timerText, (getWidth() - textWidth) / 2, 40);


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
