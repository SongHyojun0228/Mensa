import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.*;

public class GameWindow extends JFrame {
    private GamePanel gamePanel;

    // 11
    public GameWindow() {
        setTitle("게임");
        setSize(1280, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        gamePanel = new GamePanel();
        add(gamePanel);
        addKeyListener(gamePanel);
        setFocusable(true);

        setVisible(true);
    }

    public static void main(String[] args) {
        new GameWindow();
    }
}

class GamePanel extends JPanel implements KeyListener {
    // 기본 이미지 및 캐릭터 관련 변수
    private BufferedImage backgroundImage;
    private BufferedImage runImage;
    private BufferedImage idleImage;
    private BufferedImage enemyImage;
    private Image currentFrameImage;
    private int characterX = 100;
    private int characterY = 300;
    private final int MOVE_SPEED = 5;
    private int currentFrame = 0;
    private Timer animationTimer;
    private Timer moveTimer;
    private boolean isMoving = false;
    private boolean facingRight = true;
    private Set<Integer> pressedKeys = new HashSet<>();
    private final int CHARACTER_WIDTH = 50;
    private final int CHARACTER_HEIGHT = 50;

    // 플레이어 체력 (초기값 100)
    private int playerHealth = 100;

    // 공격 이펙트 관련 (스킬 이펙트 & 총알)
    private List<SkillEffect> skillEffects = new ArrayList<>();
    private List<Bullet> bullets = new ArrayList<>();
    private final int BULLET_SPEED = 10;
    private final int BULLET_LIFETIME = 2000; // 2초 후 삭제
    private final int BULLET_SIZE = 140;

    // 스킬 이펙트 클래스
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

            // 스킬 이펙트 이미지 로드 (Explosion_1.png ~ Explosion_10.png)
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
                g.drawImage(displayedFrame, x, y, BULLET_SIZE, BULLET_SIZE, null);
            }
        }
    }

    // 총알 클래스 (공격 효과)
    class Bullet {
        int x, y, dx;
        long createdTime;
        Image image;

        public Bullet(int x, int y, int dx) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.createdTime = System.currentTimeMillis();
            try {
                this.image = ImageIO.read(getClass().getResource("/images/nomal_1.png"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void update() {
            x += dx;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdTime > BULLET_LIFETIME;
        }
    }

    // 슬라임(Enemy) 관련 변수 및 클래스 (HP 감소 기능 포함)
    private final int ENEMY_SPEED = 2;
    private final int ENEMY_SIZE = 100;
    private List<Enemy> enemies = new ArrayList<>();
    private Timer enemyAnimationTimer; // 슬라임 애니메이션 타이머
    private Timer spawnTimer; // 20초마다 슬라임 추가 생성 타이머
    private Timer damageTimer; // 0.5초마다 타격 범위 내 슬라임 공격에 따른 체력 감소 적용 Timer

    class Enemy {
        int x, y;
        int frame = 0;
        boolean moving = false;
        int attackPower = 10; // 슬라임 한 마리의 공격력

        public Enemy(int x, int y) {
            this.x = x;
            this.y = y;
        }

        // 플레이어 중앙을 목표로 이동 (약간의 오프셋 적용)
        public void update() {
            int targetX = characterX - CHARACTER_WIDTH / 2;
            int targetY = characterY - CHARACTER_HEIGHT / 2 + 20;
            int dx = targetX - x;
            int dy = targetY - y;
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance > 0) {
                x += (int) (ENEMY_SPEED * dx / distance);
                y += (int) (ENEMY_SPEED * dy / distance);
                moving = true;
            } else {
                moving = false;
            }
        }

        // 슬라임 애니메이션 프레임 업데이트
        public void updateAnimation() {
            frame = (frame + 1) % 6;
        }
    }

    public GamePanel() {
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

        animationTimer = new Timer(200, e -> updateWalkAnimation());

        // 슬라임 애니메이션 타이머 (모든 슬라임 애니메이션 업데이트)
        enemyAnimationTimer = new Timer(200, e -> {
            for (Enemy enemy : enemies) {
                if (enemy.moving) {
                    enemy.updateAnimation();
                }
            }
            repaint();
        });
        enemyAnimationTimer.start();

        // 이동 타이머 : 16ms마다 캐릭터, 슬라임, 총알 이동 업데이트
        moveTimer = new Timer(16, e -> {
            moveCharacter();
            moveEnemies();
            moveBullets();
        });
        moveTimer.start();

        // 초기 슬라임 5마리 생성
        spawnEnemies(5);

        // 20초마다 슬라임 10마리씩 추가 생성
        spawnTimer = new Timer(20000, e -> spawnEnemies(10));
        spawnTimer.start();

        // 0.5초마다 타격 범위 내 슬라임의 공격으로 체력 감소 적용
        damageTimer = new Timer(500, e -> applyDamage());
        damageTimer.start();
    }

    @Override
    public void addNotify() {
        super.addNotify();
    }

    // 슬라임의 프레임을 가져오기 위한 메서드
    private Image getEnemyFrame(int frame) {
        int frameWidth = 64;
        int frameHeight = 64;
        int frameX = frame * frameWidth;
        int frameY = 0;
        return enemyImage.getSubimage(frameX, frameY, frameWidth, frameHeight);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // 배경 그리기
        g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);

        // 캐릭터 이미지 그리기 (좌우 반전 처리)
        Graphics2D g2d = (Graphics2D) g;
        Image displayedImage = facingRight ? currentFrameImage : flipImage((BufferedImage) currentFrameImage);
        g2d.drawImage(displayedImage, characterX, characterY, CHARACTER_WIDTH, CHARACTER_HEIGHT, this);

        // 체력바 그리기 (플레이어 이미지 위)
        drawHealthBar(g);

        // 슬라임(Enemy) 그리기
        for (Enemy enemy : enemies) {
            g.drawImage(getEnemyFrame(enemy.frame), enemy.x, enemy.y, ENEMY_SIZE, ENEMY_SIZE, this);
        }

        // 총알 그리기
        for (Bullet bullet : bullets) {
            g.drawImage(bullet.image, bullet.x, bullet.y, BULLET_SIZE, BULLET_SIZE, this);
        }

        // 스킬 이펙트 그리기
        for (SkillEffect effect : skillEffects) {
            effect.draw(g);
        }
    }

    // 플레이어 체력바 그리기
    private void drawHealthBar(Graphics g) {
        int barWidth = CHARACTER_WIDTH;
        int barHeight = 10;
        int x = characterX;
        int y = characterY - barHeight - 5;
        g.setColor(Color.BLACK);
        g.drawRect(x, y, barWidth, barHeight);
        int healthWidth = (int) (barWidth * (playerHealth / 100.0));
        g.setColor(Color.RED);
        g.fillRect(x + 1, y + 1, healthWidth - 1, barHeight - 1);
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

    // 캐릭터 이동 처리 (키 입력 기반)
    private void moveCharacter() {
        if (pressedKeys.isEmpty()) {
            if (isMoving) {
                isMoving = false;
                animationTimer.stop();
                currentFrameImage = idleImage;
                repaint();
            }
            return;
        }

        int dx = 0, dy = 0;

        if (pressedKeys.contains(KeyEvent.VK_LEFT)) {
            dx -= MOVE_SPEED;
            facingRight = false;
        }
        if (pressedKeys.contains(KeyEvent.VK_RIGHT)) {
            dx += MOVE_SPEED;
            facingRight = true;
        }
        if (pressedKeys.contains(KeyEvent.VK_UP)) {
            dy -= MOVE_SPEED;
        }
        if (pressedKeys.contains(KeyEvent.VK_DOWN)) {
            dy += MOVE_SPEED;
        }

        if (dx != 0 && dy != 0) {
            dx *= 0.7071;
            dy *= 0.7071;
        }

        characterX += dx;
        characterY += dy;

        if (!isMoving) {
            isMoving = true;
            animationTimer.start();
        }

        repaint();
    }

    // 모든 슬라임 이동 업데이트
    private void moveEnemies() {
        for (Enemy enemy : enemies) {
            enemy.update();
        }
        repaint();
    }

    // 슬라임 생성 (지정한 개수만큼)
    private void spawnEnemies(int count) {
        Random rand = new Random();
        int width = getWidth() > 0 ? getWidth() : 1280;
        int height = getHeight() > 0 ? getHeight() : 720;
        for (int i = 0; i < count; i++) {
            int side = rand.nextInt(4);
            int spawnX = 0;
            int spawnY = 0;
            switch (side) {
                case 0:
                    spawnX = -ENEMY_SIZE;
                    spawnY = rand.nextInt(height);
                    break;
                case 1:
                    spawnX = width;
                    spawnY = rand.nextInt(height);
                    break;
                case 2:
                    spawnX = rand.nextInt(width);
                    spawnY = -ENEMY_SIZE;
                    break;
                case 3:
                    spawnX = rand.nextInt(width);
                    spawnY = height;
                    break;
            }
            enemies.add(new Enemy(spawnX, spawnY));
        }
    }

    // 총알 업데이트 (이동 및 만료 처리)
    private void moveBullets() {
        bullets.removeIf(Bullet::isExpired);
        for (Bullet bullet : bullets) {
            bullet.update();
        }
        repaint();
    }

    // 0.5초마다 플레이어 중심 기준 타격 범위 내 슬라임 공격으로 체력 감소 적용
    private void applyDamage() {
        int centerX = characterX + CHARACTER_WIDTH / 2;
        int centerY = characterY + CHARACTER_HEIGHT / 2;
        int hitLeft = centerX - 25;
        int hitRight = centerX + 25;
        int hitTop = centerY - 45;
        int hitBottom = centerY + 20;
        int count = 0;
        for (Enemy enemy : enemies) {
            int enemyCenterX = enemy.x + ENEMY_SIZE / 2;
            int enemyCenterY = enemy.y + ENEMY_SIZE / 2;
            if (enemyCenterX >= hitLeft && enemyCenterX <= hitRight &&
                    enemyCenterY >= hitTop && enemyCenterY <= hitBottom) {
                count++;
            }
        }
        int effectiveCount = Math.min(count, 5);
        int damage = effectiveCount * (effectiveCount > 0 ? enemies.get(0).attackPower : 0);
        if (damage > 0) {
            playerHealth = Math.max(0, playerHealth - damage);
        }
        System.out.println("Player health: " + playerHealth + " (Damage: " + damage + " from " + count + " 슬라임)");
        repaint();
    }

    // 이미지 좌우 반전 처리 메서드
    private BufferedImage flipImage(BufferedImage image) {
        AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
        tx.translate(-image.getWidth(), 0);
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(image, null);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        pressedKeys.add(e.getKeyCode());

        // Z 키 : 총알 발사
        if (e.getKeyCode() == KeyEvent.VK_Z) {
            int bulletX = facingRight ? characterX + CHARACTER_WIDTH - BULLET_SIZE / 2
                    : characterX - BULLET_SIZE / 2;
            int bulletY = characterY + CHARACTER_HEIGHT / 2 - BULLET_SIZE / 2 - 13;
            int bulletDX = facingRight ? BULLET_SPEED : -BULLET_SPEED;
            bullets.add(new Bullet(bulletX, bulletY, bulletDX));
        }
        // X 키 : 스킬 이펙트 생성
        else if (e.getKeyCode() == KeyEvent.VK_X) {
            int skillX = facingRight ? characterX + CHARACTER_WIDTH + 20
                    : characterX - BULLET_SIZE - 20;
            int skillY = characterY + CHARACTER_HEIGHT / 2 - BULLET_SIZE / 2 - 20;
            skillEffects.add(new SkillEffect(skillX, skillY, facingRight));
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        pressedKeys.remove(e.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }
}