import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

public class RogueSnakeGame extends JFrame {
    public RogueSnakeGame() {
        setTitle("Rogue Snake Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        add(new GamePanel());
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RogueSnakeGame());
    }
}

class GamePanel extends JPanel implements Runnable {
    // Screen and grid settings
    static final int SCREEN_WIDTH = 800;
    static final int SCREEN_HEIGHT = 800;
    static final int UNIT_SIZE = 25;
    static final int GAME_UNITS = (SCREEN_WIDTH * SCREEN_HEIGHT) / (UNIT_SIZE * UNIT_SIZE);

    Thread gameThread;
    int initialDelayMs = 150;
    long updateInterval = initialDelayMs * 1_000_000L; // in nanoseconds

    // Snake properties
    int[] x = new int[GAME_UNITS];
    int[] y = new int[GAME_UNITS];
    int bodyParts = 6;
    int applesEaten = 0;
    int highScore = 0;
    char direction = 'R'; // U, D, L, R

    // Game state
    volatile boolean running = false;
    volatile boolean paused = false;
    volatile boolean gameOver = false;

    // Health system
    int health = 3;
    final int maxHealth = 5;

    // Apple properties
    int appleX;
    int appleY;

    // Chasing enemy properties
    int enemyX;
    int enemyY;

    // Health potion properties
    boolean potionActive = false;
    int potionX;
    int potionY;
    int potionTimer = 0;
    final int POTION_DURATION = 300; // update cycles

    Random random;

    public GamePanel() {
        random = new Random();
        setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        setBackground(Color.black);
        setFocusable(true);
        addKeyListener(new MyKeyAdapter());
        startGame();
    }

    // Initialize or reset the game
    public void startGame() {
        applesEaten = 0;
        bodyParts = 6;
        direction = 'R';
        health = 3;
        for (int i = 0; i < bodyParts; i++) {
            x[i] = 100 - i * UNIT_SIZE;
            y[i] = 100;
        }
        newApple();
        spawnEnemy();
        potionActive = false;
        gameOver = false;
        running = true;
        updateInterval = initialDelayMs * 1_000_000L;

        gameThread = new Thread(this);
        gameThread.start();
    }

    // Main game loop
    public void run() {
        long lastUpdate = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            if (!paused && now - lastUpdate >= updateInterval && !gameOver) {
                move();
                checkApple();
                enemyMove();
                checkCollisions();
                checkPotion();
                // Spawn a potion every 7 apples if not already active
                if (!potionActive && applesEaten != 0 && applesEaten % 7 == 0) {
                    spawnPotion();
                }
                // Increase speed every 5 apples (minimum delay enforced)
                if (applesEaten % 5 == 0 && updateInterval > 50_000_000L) {
                    updateInterval -= 10_000_000L;
                }
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

    // Update snake position
    public void move() {
        for (int i = bodyParts; i > 0; i--) {
            x[i] = x[i - 1];
            y[i] = y[i - 1];
        }
        switch (direction) {
            case 'U': y[0] -= UNIT_SIZE; break;
            case 'D': y[0] += UNIT_SIZE; break;
            case 'L': x[0] -= UNIT_SIZE; break;
            case 'R': x[0] += UNIT_SIZE; break;
        }
        // Check border collision (game over)
        if (x[0] < 0 || x[0] >= SCREEN_WIDTH || y[0] < 0 || y[0] >= SCREEN_HEIGHT) {
            gameOver = true;
            running = false;
        }
    }

    // Check if apple is eaten
    public void checkApple() {
        if (x[0] == appleX && y[0] == appleY) {
            bodyParts++;
            applesEaten++;
            if (applesEaten > highScore) highScore = applesEaten;
            newApple();
            Toolkit.getDefaultToolkit().beep();
        }
    }

    // Simple enemy movement: enemy moves one grid step toward snake's head
    public void enemyMove() {
        if (enemyX < x[0]) enemyX += UNIT_SIZE;
        else if (enemyX > x[0]) enemyX -= UNIT_SIZE;
        if (enemyY < y[0]) enemyY += UNIT_SIZE;
        else if (enemyY > y[0]) enemyY -= UNIT_SIZE;
    }

    // Check collisions (with self and enemy)
    public void checkCollisions() {
        // Collision with self is fatal
        for (int i = bodyParts; i > 0; i--) {
            if (x[0] == x[i] && y[0] == y[i]) {
                gameOver = true;
                running = false;
                return;
            }
        }
        // Collision with enemy: lose one health and reposition enemy
        if (x[0] == enemyX && y[0] == enemyY) {
            health--;
            spawnEnemy();
            if (health <= 0) {
                gameOver = true;
                running = false;
                return;
            }
        }
    }

    // Check if a potion is collected or expired
    public void checkPotion() {
        if (potionActive) {
            potionTimer--;
            if (potionTimer <= 0) {
                potionActive = false;
            }
            if (x[0] == potionX && y[0] == potionY) {
                if (health < maxHealth) {
                    health++;
                }
                potionActive = false;
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }

    // Place a new apple at a random location
    public void newApple() {
        appleX = random.nextInt(SCREEN_WIDTH / UNIT_SIZE) * UNIT_SIZE;
        appleY = random.nextInt(SCREEN_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
    }

    // Spawn the enemy at a random location
    public void spawnEnemy() {
        enemyX = random.nextInt(SCREEN_WIDTH / UNIT_SIZE) * UNIT_SIZE;
        enemyY = random.nextInt(SCREEN_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
    }

    // Spawn a health potion
    public void spawnPotion() {
        potionActive = true;
        potionX = random.nextInt(SCREEN_WIDTH / UNIT_SIZE) * UNIT_SIZE;
        potionY = random.nextInt(SCREEN_HEIGHT / UNIT_SIZE) * UNIT_SIZE;
        potionTimer = POTION_DURATION;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        draw(g);
    }

    // Render game objects and UI
    public void draw(Graphics g) {
        // Draw border
        g.setColor(Color.white);
        g.drawRect(0, 0, SCREEN_WIDTH - 1, SCREEN_HEIGHT - 1);

        // Draw apple
        g.setColor(Color.red);
        g.fillOval(appleX, appleY, UNIT_SIZE, UNIT_SIZE);

        // Draw enemy
        g.setColor(Color.magenta);
        g.fillRect(enemyX, enemyY, UNIT_SIZE, UNIT_SIZE);

        // Draw potion if active
        if (potionActive) {
            g.setColor(Color.green);
            g.fillOval(potionX, potionY, UNIT_SIZE, UNIT_SIZE);
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

        // Draw score, health, and high score
        g.setColor(Color.white);
        g.setFont(new Font("Ink Free", Font.BOLD, 25));
        FontMetrics fm = getFontMetrics(g.getFont());
        String scoreText = "Score: " + applesEaten;
        g.drawString(scoreText, (SCREEN_WIDTH - fm.stringWidth(scoreText)) / 2, g.getFont().getSize());
        String healthText = "Health: " + health;
        g.drawString(healthText, 10, g.getFont().getSize());
        String highScoreText = "High Score: " + highScore;
        g.drawString(highScoreText, SCREEN_WIDTH - fm.stringWidth(highScoreText) - 10, g.getFont().getSize());

        // If game over, display game over message and restart instruction
        if (gameOver) {
            g.setColor(Color.red);
            g.setFont(new Font("Ink Free", Font.BOLD, 75));
            fm = getFontMetrics(g.getFont());
            String overText = "Game Over";
            g.drawString(overText, (SCREEN_WIDTH - fm.stringWidth(overText)) / 2, SCREEN_HEIGHT / 2);
            g.setFont(new Font("Ink Free", Font.BOLD, 40));
            fm = getFontMetrics(g.getFont());
            String restartText = "Press R to Restart";
            g.drawString(restartText, (SCREEN_WIDTH - fm.stringWidth(restartText)) / 2, SCREEN_HEIGHT / 2 + 50);
        }
    }

    class MyKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            if (!gameOver) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT:
                        if (direction != 'R') direction = 'L';
                        break;
                    case KeyEvent.VK_RIGHT:
                        if (direction != 'L') direction = 'R';
                        break;
                    case KeyEvent.VK_UP:
                        if (direction != 'D') direction = 'U';
                        break;
                    case KeyEvent.VK_DOWN:
                        if (direction != 'U') direction = 'D';
                        break;
                    case KeyEvent.VK_P:
                        paused = !paused;
                        break;
                }
            }
            if (gameOver && e.getKeyCode() == KeyEvent.VK_R) {
                startGame();
            }
        }
    }
}
