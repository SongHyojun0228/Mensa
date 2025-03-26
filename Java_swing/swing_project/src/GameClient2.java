
// import java.awt.*;
// import java.awt.event.*;
// import java.awt.geom.AffineTransform;
// import java.awt.image.AffineTransformOp;
// import java.awt.image.BufferedImage;
// import java.io.*;
// import java.net.*;
// import java.util.*;
// import java.util.List;
// import javax.imageio.ImageIO;
// import javax.swing.*;
// import javax.swing.Timer;

// public class GameClient2 extends JFrame {

//     private GamePanel gamePanel;

//     public GameClient2() {
//         setTitle("GameClient");
//         setSize(1280, 720);
//         setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

//         gamePanel = new GamePanel();
//         add(gamePanel);
//         addKeyListener(gamePanel);

//         setFocusable(true);
//         setVisible(true);
//     }

//     public static void main(String[] args) {
//         new GameClient2();
//     }
// }

// class GamePanel extends JPanel implements KeyListener {

//     private BufferedImage backgroundImage;
//     private BufferedImage runImage;
//     private BufferedImage idleImage;
//     private BufferedImage enemyImage;
//     private BufferedImage bulletImage;
//     private List<BufferedImage> skillFrames = new ArrayList<>();

//     private int characterX = 100;
//     private int characterY = 300;
//     private boolean facingRight = true;
//     private int playerHealth = 100;
//     private int playerMP = 100;

//     private final int CHARACTER_WIDTH = 50;
//     private final int CHARACTER_HEIGHT = 50;
//     private final int ENEMY_SIZE = 100;
//     private final int BULLET_SIZE = 140;

//     private List<Map<String, Object>> enemies = new ArrayList<>();
//     private List<Map<String, Object>> bullets = new ArrayList<>();
//     private List<Map<String, Object>> otherPlayers = new ArrayList<>();
//     private List<SkillEffect> skillEffects = new ArrayList<>();

//     private Set<String> pressedKeys = new HashSet<>();

//     private Socket socket;
//     private ObjectOutputStream out;
//     private ObjectInputStream in;

//     private Timer syncTimer;
//     private Timer animationTimer;
//     private int currentFrame = 0;
//     private Image currentFrameImage;

//     public GamePanel() {
//         setDoubleBuffered(true);

//         try {
//             backgroundImage = ImageIO.read(getClass().getResource("/images/5.png"));
//             runImage = ImageIO.read(getClass().getResource("/images/Pink_Monster_Run_6.png"));
//             idleImage = ImageIO.read(getClass().getResource("/images/Pink_Monster.png"));
//             enemyImage = ImageIO.read(getClass().getResource("/images/Slime1_Walk_full[1].png"));
//             bulletImage = ImageIO.read(getClass().getResource("/images/nomal_1.png"));
//             for (int i = 1; i <= 10; i++) {
//                 skillFrames.add(ImageIO.read(getClass().getResource("/images/Explosion_" + i + ".png")));
//             }
//         } catch (IOException e) {
//             e.printStackTrace();
//         }

//         currentFrameImage = idleImage;

//         connectToServer();
//         startTimers();
//     }

//     private void connectToServer() {
//         try {
//             socket = new Socket("localhost", 2003);
//             out = new ObjectOutputStream(socket.getOutputStream());
//             in = new ObjectInputStream(socket.getInputStream());

//             new Thread(() -> {
//                 while (true) {
//                     try {
//                         Object obj = in.readObject();
//                         if (obj instanceof Map) {
//                             @SuppressWarnings("unchecked")
//                             Map<String, Object> state = (Map<String, Object>) obj;
//                             updateGameState(state);
//                         }
//                     } catch (Exception e) {
//                         System.err.println("서버 연결 종료");
//                         break;
//                     }
//                 }
//             }).start();

//         } catch (IOException e) {
//             e.printStackTrace();
//         }
//     }

//     private void startTimers() {
//         animationTimer = new Timer(200, e -> {
//             if (pressedKeys.isEmpty()) {
//                 currentFrameImage = idleImage;
//                 repaint();
//                 return;
//             }
//             currentFrame = (currentFrame + 1) % 6;
//             currentFrameImage = getCharacterFrame(currentFrame);
//             repaint();
//         });
//         animationTimer.start();

//         syncTimer = new Timer(50, e -> sendInput());
//         syncTimer.start();
//     }

//     private void sendInput() {
//         try {
//             Map<String, Object> input = new HashMap<>();
//             input.put("keys", new ArrayList<>(pressedKeys));
//             out.reset();
//             out.writeObject(input);
//             out.flush();
//         } catch (IOException e) {
//             e.printStackTrace();
//         }
//     }

//     private void sendAction(String action) {
//         try {
//             Map<String, Object> input = new HashMap<>();
//             input.put("action", action);
//             out.reset();
//             out.writeObject(input);
//             out.flush();
//         } catch (IOException e) {
//             e.printStackTrace();
//         }
//     }

//     private void updateGameState(Map<String, Object> state) {
//         otherPlayers = (List<Map<String, Object>>) state.get("players");
//         if (!otherPlayers.isEmpty()) {
//             Map<String, Object> self = otherPlayers.get(0);
//             characterX = (int) self.get("x");
//             characterY = (int) self.get("y");
//             playerHealth = (int) self.get("health");
//             facingRight = (boolean) self.get("facingRight");
//             playerMP = (int) self.get("mp");
//         }
//         enemies = (List<Map<String, Object>>) state.get("enemies");
//         bullets = (List<Map<String, Object>>) state.get("bullets");
//         repaint();
//     }

//     private void drawMPBar(Graphics g, int x, int y, int mp) {
//         int barWidth = CHARACTER_WIDTH;
//         int barHeight = 4;
//         int barX = characterX;
//         int barY = characterY - 5 - 10 + 10;

//         g.setColor(Color.BLACK);
//         g.drawRect(x, barY, barWidth, barHeight);
//         int mpWidth = (int) (barWidth * (mp / 100.0));
//         g.setColor(Color.BLUE);
//         g.fillRect(x + 1, barY + 1, mpWidth - 1, barHeight - 1);
//     }

//     @Override
//     protected void paintComponent(Graphics g) {
//         super.paintComponent(g);
//         g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);

//         for (int i = 0; i < otherPlayers.size(); i++) {
//             Map<String, Object> p = otherPlayers.get(i);
//             int px = (int) p.get("x");
//             int py = (int) p.get("y");
//             boolean dir = (boolean) p.get("facingRight");
//             int hp = (int) p.get("health");

//             Image frame = dir ? currentFrameImage : flipImage((BufferedImage) currentFrameImage);
//             g.drawImage(frame, px, py, CHARACTER_WIDTH, CHARACTER_HEIGHT, this);
//             drawHealthBar(g, px, py, hp);

//             if (i == 0) {
//                 drawMPBar(g, px, py, playerMP);
//             }
//         }

//         for (Map<String, Object> e : enemies) {
//             int ex = (int) e.get("x");
//             int ey = (int) e.get("y");
//             int frame = (int) e.get("frame");
//             g.drawImage(getEnemyFrame(frame), ex, ey, ENEMY_SIZE, ENEMY_SIZE, this);
//         }

//         for (Map<String, Object> b : bullets) {
//             int bx = (int) b.get("x");
//             int by = (int) b.get("y");
//             g.drawImage(bulletImage, bx, by, BULLET_SIZE, BULLET_SIZE, this);
//         }

//         for (SkillEffect effect : skillEffects) {
//             effect.draw(g);
//         }
//     }

//     private void drawHealthBar(Graphics g, int x, int y, int health) {
//         int barWidth = CHARACTER_WIDTH;
//         int barHeight = 10;
//         int barY = y - barHeight - 5;
//         g.setColor(Color.BLACK);
//         g.drawRect(x, barY, barWidth, barHeight);
//         int healthWidth = (int) (barWidth * (health / 100.0));
//         g.setColor(Color.RED);
//         g.fillRect(x + 1, barY + 1, healthWidth - 1, barHeight - 1);
//     }

//     private Image getCharacterFrame(int frame) {
//         int fw = 32;
//         int fh = 32;
//         int fx = frame * fw;
//         return runImage.getSubimage(fx, 0, fw, fh);
//     }

//     private Image getEnemyFrame(int frame) {
//         int fw = 64;
//         int fh = 64;
//         int fx = frame * fw;
//         return enemyImage.getSubimage(fx, 0, fw, fh);
//     }

//     private BufferedImage flipImage(BufferedImage image) {
//         AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
//         tx.translate(-image.getWidth(), 0);
//         AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
//         return op.filter(image, null);
//     }

//     @Override
//     public void keyPressed(KeyEvent e) {
//         switch (e.getKeyCode()) {
//             case KeyEvent.VK_LEFT:
//                 pressedKeys.add("LEFT");
//                 break;
//             case KeyEvent.VK_RIGHT:
//                 pressedKeys.add("RIGHT");
//                 break;
//             case KeyEvent.VK_UP:
//                 pressedKeys.add("UP");
//                 break;
//             case KeyEvent.VK_DOWN:
//                 pressedKeys.add("DOWN");
//                 break;
//             case KeyEvent.VK_Z:
//                 sendAction("attack");
//                 break;
//             case KeyEvent.VK_X:
//                 if (playerMP >= 10) {
//                     playerMP -= 10;
//                     sendAction("skill");
//                     int skillX = facingRight ? characterX + CHARACTER_WIDTH + 20 : characterX - BULLET_SIZE - 20;
//                     int skillY = characterY + CHARACTER_HEIGHT / 2 - BULLET_SIZE / 2 - 20;
//                     skillEffects.add(new SkillEffect(skillX, skillY, facingRight));
//                 }
//                 break;
//         }
//     }

//     @Override
//     public void keyReleased(KeyEvent e) {
//         switch (e.getKeyCode()) {
//             case KeyEvent.VK_LEFT:
//                 pressedKeys.remove("LEFT");
//                 break;
//             case KeyEvent.VK_RIGHT:
//                 pressedKeys.remove("RIGHT");
//                 break;
//             case KeyEvent.VK_UP:
//                 pressedKeys.remove("UP");
//                 break;
//             case KeyEvent.VK_DOWN:
//                 pressedKeys.remove("DOWN");
//                 break;
//         }
//     }

//     @Override
//     public void keyTyped(KeyEvent e) {
//     }

//     private class SkillEffect {

//         int x, y, currentFrame = 0;
//         boolean facingRight;
//         Timer animationTimer;

//         public SkillEffect(int x, int y, boolean facingRight) {
//             this.x = x;
//             this.y = y;
//             this.facingRight = facingRight;

//             animationTimer = new Timer(100, e -> {
//                 currentFrame++;
//                 if (currentFrame >= skillFrames.size()) {
//                     animationTimer.stop();
//                     skillEffects.remove(this);
//                 }
//                 repaint();
//             });
//             animationTimer.start();
//         }

//         public void draw(Graphics g) {
//             if (currentFrame < skillFrames.size()) {
//                 BufferedImage frame = skillFrames.get(currentFrame);
//                 BufferedImage displayed = facingRight ? frame : flipImage(frame);
//                 g.drawImage(displayed, x, y, BULLET_SIZE, BULLET_SIZE, null);
//             }
//         }
//     }
// }
