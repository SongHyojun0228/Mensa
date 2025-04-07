package multi;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GameMainMenu extends JFrame {

    private JTextField nameField;
    private JButton startButton;
    private JLabel backgroundLabel;

    public GameMainMenu() {
        setTitle("망한게임");
        setSize(1280, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(null);

        // 배경 이미지 설정**
        ImageIcon backgroundImage = new ImageIcon(getClass().getResource("/images/메인화면2.png"));
        backgroundLabel = new JLabel(backgroundImage);
        backgroundLabel.setBounds(0, 0, 1280, 720);

        // 플레이어 이름 입력 필드**
        nameField = new JTextField("Guest" + (int) (Math.random() * 10000));
        nameField.setBounds(500, 500, 300, 40);
        nameField.setFont(new Font("Arial", Font.BOLD, 20));

        // 게임 시작 버튼**
        startButton = new JButton("Game Start");
        startButton.setBounds(550, 560, 200, 50);
        startButton.setFont(new Font("Arial", Font.BOLD, 20));

        // 버튼 클릭 이벤트**
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String playerName = nameField.getText().trim();
                if (!playerName.isEmpty()) {
                    dispose(); // 메인 화면 닫기**
                    new GameClient(playerName); // 게임 클라이언트 실행**, 클라이언트 자바 파일 자동 실행함
                } else {
                    JOptionPane.showMessageDialog(null, "플레이어 이름을 입력하세요.", "경고", JOptionPane.WARNING_MESSAGE);
                }
            }
        });
        add(nameField);
        add(startButton);
        add(backgroundLabel);
        backgroundLabel.setOpaque(false);

        setVisible(true);
    }

    public static void main(String[] args) {
        new GameMainMenu();
    }
}
