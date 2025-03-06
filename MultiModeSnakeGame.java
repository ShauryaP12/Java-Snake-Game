import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

public class MultiModeSnakeGame extends JFrame {
    public MultiModeSnakeGame() {
        setTitle("Multi-Mode Snake Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        add(new GamePanel());
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MultiModeSnakeGame());
    }
}

class GamePanel extends JPanel implements Runnable {
    // Screen and grid settings
    static final int SCREEN_WIDTH = 800;
    static final int SCREEN_HEIGHT = 800;
    static final int UNIT_SIZE = 25;
    static final int GAME_UNITS = (SCREEN_WIDTH * SCREEN_HEIGHT) / (UNIT_SIZE * UNIT_SIZE);

    // Game loop settings
    int initialDelayMs = 150;
    long updateInterval = initialDelayMs * 1_000_000L; // in nanoseconds
    Thread gameThread;

    // Game states and modes
    private enum GameState {
        MENU, PLAYING, GAMEOVER
    }

    private enum GameMode {
        CLASSIC, WRAP, OBSTACLE, BONUS
    }

    private GameState gameState = GameState.MENU;
    private GameMode selectedMode = GameMode.CLASSIC; // default mode

    volatile boolean paused = false;
    volatile boolean running = false;

    // Snake properties
    int[] x = new int[GAME_UNITS];
    int[] y = new int[GAME_UNITS];
    int bodyParts = 6;
    int applesEaten;
    int highScore = 0;
    char direction = 'R'; // U, D, L, R

    // Timer for elapsed time
    long startTime;
    long elapsedTime; // seconds

    // Apple properties
    int appleX;
    int appleY;

    // Bonus fruit properties (for BONUS mode)
    boolean bonusActive = false;
    int bonusX;
    int bonusY;
    int bonusTimer = 0;
    final int BONUS_DURATION = 200; // update cycles
    final int BONUS_SCORE = 5;
    final int BONUS_EXTRA_PARTS = 2;

    // Obstacles properties (for OBSTACLE mode)
    int obstacleCount = 5;
    int[] obstacleX = new int[obstacleCount];
    int[] obstacleY = new int[obstacleCount];

    // Shield power-up (extra feature available in all modes)
    boolean shieldPowerActive = false; // shield power-up is on screen
    int shieldX;
    int shieldY;
    int shieldTimer = 0;
    final int SHIELD_DURATION = 150; // update cycles
    boolean hasShield = false; // collected shield

    Random random;

    public GamePanel() {
        random = new Random();
        setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        setBackground(Color.black);
        setFocusable(true);
        // Enable double buffering for smoother rendering
        setDoubleBuffered(true);
        addKeyListener(new MyKeyAdapter());
    }

    // Initializes or resets the game
    public void startGame() {
        applesEaten = 0;
        bodyParts = 6;
        direction = 'R';
        for (int i = 0; i < bodyParts; i++) {
            x[i] = 100 - i * UNIT_SIZE;
            y[i] = 100;
        }
        newApple();
        bonusActive = false;
        bonusTimer = 0;
        shieldPowerActive = false;
        shieldTimer = 0;
        hasShield = false;

        // Generate obstacles if OBSTACLE mode is selected
        if (selectedMode == GameMode.OBSTACLE) {
            generateObstacles();
        }

        updateInterval = initialDelayMs * 1_000_000L;
        paused = false;
        running = true;
        gameState = GameState.PLAYING;
        startTime = System.currentTimeMillis();

        gameThread = new Thread(this);
        gameThread.start();
    }

    // The fixed time step game loop
    public void run() {
        long lastUpdate = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            if (gameState == GameState.PLAYING && !paused && now - lastUpdate >= updateInterval) {
                move();
                checkApple();
                if (selectedMode == GameMode.BONUS) {
                    checkBonus();
                }
                // Spawn shield power-up every 15 apples if not already active or collected
                if (!shieldPowerActive && applesEaten != 0 && applesEaten % 15 == 0 && !hasShield) {
                    spawnShield();
                }
                if (shieldPowerActive) {
                    shieldTimer--;
                    if (shieldTimer <= 0) {
                        shieldPowerActive = false;
                    }
                }
                checkCollisions();
                // Bonus fruit countdown
                if (bonusActive) {
                    bonusTimer--;
                    if (bonusTimer <= 0) {
                        bonusActive = false;
                    }
                }
                elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
                lastUpdate = now;
            }
            repaint();
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // Randomly positions a new apple on the grid
    public void newApple() {
        appleX = random.nextInt(SCREEN_WIDTH / UNIT_SIZE) * UNIT_SIZE;
        appleY = random.nextInt(SCREEN_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
    }

    // Generates obstacles for OBSTACLE mode
    public void generateObstacles() {
        for (int i = 0; i < obstacleCount; i++) {
            boolean valid;
            do {
                valid = true;
                obstacleX[i] = random.nextInt(SCREEN_WIDTH / UNIT_SIZE) * UNIT_SIZE;
                obstacleY[i] = random.nextInt(SCREEN_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
                // Avoid overlapping with the snake's starting position
                for (int j = 0; j < bodyParts; j++) {
                    if (obstacleX[i] == x[j] && obstacleY[i] == y[j]) {
                        valid = false;
                        break;
                    }
                }
                // Avoid overlapping with the apple
                if (obstacleX[i] == appleX && obstacleY[i] == appleY) {
                    valid = false;
                }
            } while (!valid);
        }
    }

    // Spawns a bonus fruit (for BONUS mode)
    public void spawnBonusFruit() {
        bonusActive = true;
        bonusX = random.nextInt(SCREEN_WIDTH / UNIT_SIZE) * UNIT_SIZE;
        bonusY = random.nextInt(SCREEN_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
        bonusTimer = BONUS_DURATION;
    }

    // Spawns a shield power-up
    public void spawnShield() {
        shieldPowerActive = true;
        shieldX = random.nextInt(SCREEN_WIDTH / UNIT_SIZE) * UNIT_SIZE;
        shieldY = random.nextInt(SCREEN_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
        shieldTimer = SHIELD_DURATION;
    }

    // Shifts the snake's body and updates the head position
    public void move() {
        for (int i = bodyParts; i > 0; i--) {
            x[i] = x[i - 1];
            y[i] = y[i - 1];
        }
        switch (direction) {
            case 'U':
                y[0] -= UNIT_SIZE;
                break;
            case 'D':
                y[0] += UNIT_SIZE;
                break;
            case 'L':
                x[0] -= UNIT_SIZE;
                break;
            case 'R':
                x[0] += UNIT_SIZE;
                break;
        }
        // In WRAP mode, allow the snake to appear on the opposite side
        if (selectedMode == GameMode.WRAP) {
            if (x[0] < 0)
                x[0] = SCREEN_WIDTH - UNIT_SIZE;
            if (x[0] >= SCREEN_WIDTH)
                x[0] = 0;
            if (y[0] < 0)
                y[0] = SCREEN_HEIGHT - UNIT_SIZE;
            if (y[0] >= SCREEN_HEIGHT)
                y[0] = 0;
        }
    }

    // Checks if the snake has eaten an apple
    public void checkApple() {
        if (x[0] == appleX && y[0] == appleY) {
            bodyParts++;
            applesEaten++;
            if (applesEaten > highScore)
                highScore = applesEaten;
            newApple();
            // Increase game speed every 5 apples
            if (applesEaten % 5 == 0 && updateInterval > 50_000_000L) {
                updateInterval -= 10_000_000L;
            }
            // In BONUS mode, spawn a bonus fruit every 10 apples if not active
            if (selectedMode == GameMode.BONUS && applesEaten % 10 == 0 && !bonusActive) {
                spawnBonusFruit();
            }
            Toolkit.getDefaultToolkit().beep();
        }
    }

    // Checks if the snake has collected the bonus fruit
    public void checkBonus() {
        if (bonusActive && x[0] == bonusX && y[0] == bonusY) {
            bonusActive = false;
            applesEaten += BONUS_SCORE;
            bodyParts += BONUS_EXTRA_PARTS;
            if (applesEaten > highScore)
                highScore = applesEaten;
            Toolkit.getDefaultToolkit().beep();
        }
    }

    // Checks if the snake has collected the shield power-up
    public void checkShield() {
        if (shieldPowerActive && x[0] == shieldX && y[0] == shieldY) {
            shieldPowerActive = false;
            hasShield = true;
            Toolkit.getDefaultToolkit().beep();
        }
    }

    // Checks for collisions (with self, borders, obstacles) and applies shield if
    // available
    public void checkCollisions() {
        // Self-collision (always fatal)
        for (int i = bodyParts; i > 0; i--) {
            if (x[0] == x[i] && y[0] == y[i]) {
                gameState = GameState.GAMEOVER;
                running = false;
                return;
            }
        }
        // Border collision (except in WRAP mode)
        if (selectedMode != GameMode.WRAP) {
            if (x[0] < 0 || x[0] >= SCREEN_WIDTH || y[0] < 0 || y[0] >= SCREEN_HEIGHT) {
                gameState = GameState.GAMEOVER;
                running = false;
                return;
            }
        }
        // Obstacles (in OBSTACLE mode)
        if (selectedMode == GameMode.OBSTACLE) {
            for (int i = 0; i < obstacleCount; i++) {
                if (x[0] == obstacleX[i] && y[0] == obstacleY[i]) {
                    if (hasShield) { // consume shield to avoid death
                        hasShield = false;
                    } else {
                        gameState = GameState.GAMEOVER;
                        running = false;
                        return;
                    }
                }
            }
        }
        // Check for shield power-up collection (available in all modes)
        checkShield();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    // Renders game elements based on the current state
    public void draw(Graphics g) {
        // Draw a clear white border around the playing area
        g.setColor(Color.white);
        g.drawRect(0, 0, SCREEN_WIDTH - 1, SCREEN_HEIGHT - 1);

        if (gameState == GameState.MENU) {
            // Menu screen with mode selection
            g.setColor(Color.white);
            g.setFont(new Font("Ink Free", Font.BOLD, 50));
            FontMetrics fm = getFontMetrics(g.getFont());
            String title = "Multi-Mode Snake Game";
            g.drawString(title, (SCREEN_WIDTH - fm.stringWidth(title)) / 2, SCREEN_HEIGHT / 4);

            g.setFont(new Font("Ink Free", Font.BOLD, 30));
            fm = getFontMetrics(g.getFont());
            String modeText = "Select Game Mode:";
            g.drawString(modeText, (SCREEN_WIDTH - fm.stringWidth(modeText)) / 2, SCREEN_HEIGHT / 4 + 50);

            String[] modes = { "1: Classic", "2: Wrap", "3: Obstacle", "4: Bonus" };
            for (int i = 0; i < modes.length; i++) {
                g.drawString(modes[i], (SCREEN_WIDTH - fm.stringWidth(modes[i])) / 2, SCREEN_HEIGHT / 4 + 100 + i * 40);
            }

            String instruct = "Press ENTER to Start";
            g.drawString(instruct, (SCREEN_WIDTH - fm.stringWidth(instruct)) / 2, SCREEN_HEIGHT / 4 + 300);
            String selected = "Selected Mode: " + selectedMode.toString();
            g.drawString(selected, (SCREEN_WIDTH - fm.stringWidth(selected)) / 2, SCREEN_HEIGHT / 4 + 350);

        } else if (gameState == GameState.PLAYING) {
            // Draw apple
            g.setColor(Color.red);
            g.fillOval(appleX, appleY, UNIT_SIZE, UNIT_SIZE);

            // Draw bonus fruit (for BONUS mode)
            if (selectedMode == GameMode.BONUS && bonusActive) {
                g.setColor(Color.yellow);
                g.fillOval(bonusX, bonusY, UNIT_SIZE, UNIT_SIZE);
            }

            // Draw obstacles (for OBSTACLE mode)
            if (selectedMode == GameMode.OBSTACLE) {
                g.setColor(Color.gray);
                for (int i = 0; i < obstacleCount; i++) {
                    g.fillRect(obstacleX[i], obstacleY[i], UNIT_SIZE, UNIT_SIZE);
                }
            }

            // Draw shield power-up if active
            if (shieldPowerActive) {
                g.setColor(Color.blue);
                g.fillRect(shieldX, shieldY, UNIT_SIZE, UNIT_SIZE);
            }

            // Draw snake
            for (int i = 0; i < bodyParts; i++) {
                if (i == 0) {
                    g.setColor(Color.green);
                    g.fillRect(x[i], y[i], UNIT_SIZE, UNIT_SIZE);
                } else {
                    g.setColor(new Color(45, 180, 0));
                    g.fillRect(x[i], y[i], UNIT_SIZE, UNIT_SIZE);
                }
            }

            // Draw game info: score, high score, elapsed time, game mode, and shield status
            // if active
            g.setColor(Color.white);
            g.setFont(new Font("Ink Free", Font.BOLD, 25));
            FontMetrics fm = getFontMetrics(g.getFont());
            String scoreText = "Score: " + applesEaten;
            g.drawString(scoreText, (SCREEN_WIDTH - fm.stringWidth(scoreText)) / 2, g.getFont().getSize());
            String highScoreText = "High Score: " + highScore;
            g.drawString(highScoreText, 10, g.getFont().getSize());
            String timeText = "Time: " + elapsedTime + "s";
            g.drawString(timeText, SCREEN_WIDTH - fm.stringWidth(timeText) - 10, g.getFont().getSize());
            String modeTextDisplay = "Mode: " + selectedMode.toString();
            g.drawString(modeTextDisplay, 10, SCREEN_HEIGHT - 10);
            if (hasShield) {
                String shieldText = "Shield: ON";
                g.drawString(shieldText, SCREEN_WIDTH - fm.stringWidth(shieldText) - 10, SCREEN_HEIGHT - 10);
            }
        } else if (gameState == GameState.GAMEOVER) {
            // Game over screen
            g.setColor(Color.red);
            g.setFont(new Font("Ink Free", Font.BOLD, 75));
            FontMetrics fm = getFontMetrics(g.getFont());
            String overText = "Game Over";
            g.drawString(overText, (SCREEN_WIDTH - fm.stringWidth(overText)) / 2, SCREEN_HEIGHT / 2);
            g.setFont(new Font("Ink Free", Font.BOLD, 40));
            fm = getFontMetrics(g.getFont());
            String scoreText = "Score: " + applesEaten;
            g.drawString(scoreText, (SCREEN_WIDTH - fm.stringWidth(scoreText)) / 2, SCREEN_HEIGHT / 2 + 50);
            String restartText = "Press R to Restart";
            g.drawString(restartText, (SCREEN_WIDTH - fm.stringWidth(restartText)) / 2, SCREEN_HEIGHT / 2 + 100);
        }
    }

    class MyKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            if (gameState == GameState.MENU) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_1:
                        selectedMode = GameMode.CLASSIC;
                        repaint(); // update menu display
                        break;
                    case KeyEvent.VK_2:
                        selectedMode = GameMode.WRAP;
                        repaint();
                        break;
                    case KeyEvent.VK_3:
                        selectedMode = GameMode.OBSTACLE;
                        repaint();
                        break;
                    case KeyEvent.VK_4:
                        selectedMode = GameMode.BONUS;
                        repaint();
                        break;
                    case KeyEvent.VK_ENTER:
                        startGame();
                        break;
                }
            } else if (gameState == GameState.PLAYING) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT:
                        if (direction != 'R')
                            direction = 'L';
                        break;
                    case KeyEvent.VK_RIGHT:
                        if (direction != 'L')
                            direction = 'R';
                        break;
                    case KeyEvent.VK_UP:
                        if (direction != 'D')
                            direction = 'U';
                        break;
                    case KeyEvent.VK_DOWN:
                        if (direction != 'U')
                            direction = 'D';
                        break;
                    case KeyEvent.VK_P:
                        paused = !paused;
                        break;
                }
            } else if (gameState == GameState.GAMEOVER) {
                // Fix: On game over, press R to restart the game immediately
                if (e.getKeyCode() == KeyEvent.VK_R) {
                    startGame();
                }
            }
        }
    }
}
