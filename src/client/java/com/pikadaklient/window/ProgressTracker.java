package com.pikadaklient.window;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ProgressTracker {

    // === Instance Fields (Non-Static) ===
    private String currentState = "Idle";
    private String currentAction = "None";
    private int prestige = 0;
    private int ascension = 0;
    private int requiredPrestige = 0;
    private int coins = 0;

    // === Swing Components (Non-Static) ===
    private JFrame frame;
    private JTextArea textArea;
    private JToggleButton pinButton;
    private boolean initialized = false;

    // For dragging window
    private Point mouseClickPoint;

    // === Singleton Holder ===
    // Use a Singleton pattern to manage the single instance of the window
    private static ProgressTracker instance;

    /**
     * Public accessor for the single instance of the ProgressTracker.
     */
    public static ProgressTracker getInstance() {
        if (instance == null) {
            instance = new ProgressTracker();
        }
        return instance;
    }

    // Private constructor to enforce Singleton
    private ProgressTracker() {}

    // === Initialization ===

    /**
     * Initializes and shows the window. Must be called once.
     * Subsequent calls only make the existing window visible.
     */
    public void initWindow() {
        if (initialized) {
            // If already initialized, just ensure it's visible
            SwingUtilities.invokeLater(() -> frame.setVisible(true));
            return;
        }
        initialized = true;

        // Ensure all component creation and manipulation is on the EDT
        SwingUtilities.invokeLater(this::createAndShowGUI);
    }

    private void createAndShowGUI() {
        frame = new JFrame("Progress Tracker");
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setSize(280, 200); // Slightly larger for better fit
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        frame.setUndecorated(true); // Undecorated for a cleaner look

        // ===== Text Area =====
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        textArea.setBackground(Color.BLACK);
        textArea.setForeground(Color.GREEN);
        textArea.setMargin(new Insets(5, 5, 5, 5));

        // ===== Top Bar with Pin Button and Title =====
        // Use a box layout for better control over title and button alignment
        JPanel topBar = new JPanel();
        topBar.setLayout(new BoxLayout(topBar, BoxLayout.X_AXIS));
        topBar.setBackground(Color.DARK_GRAY.darker());
        topBar.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        // Title Label
        JLabel titleLabel = new JLabel("PikadaClient Tracker");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        // Pin Button
        pinButton = new JToggleButton("📌");
        pinButton.setFocusPainted(false);
        pinButton.setFont(new Font("SansSerif", Font.PLAIN, 12));
        pinButton.setForeground(Color.WHITE);
        pinButton.setBackground(Color.GRAY);
        pinButton.setPreferredSize(new Dimension(30, 20)); // Fixed size
        pinButton.setMargin(new Insets(0, 0, 0, 0));
        pinButton.setToolTipText("Toggle Always On Top");

        pinButton.addActionListener(e -> {
            boolean pinned = pinButton.isSelected();
            frame.setAlwaysOnTop(pinned);
            pinButton.setBackground(pinned ? Color.GREEN.darker() : Color.GRAY);
        });

        // Add components to Top Bar
        topBar.add(titleLabel);
        topBar.add(Box.createHorizontalGlue()); // Pushes the pin button to the right
        topBar.add(pinButton);

        // ===== Add Components to Frame =====
        frame.add(topBar, BorderLayout.NORTH);
        frame.add(new JScrollPane(textArea), BorderLayout.CENTER);

        // ===== Make Window Draggable Anywhere (for undecorated frame) =====
        MouseAdapter dragListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                mouseClickPoint = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                Point current = e.getLocationOnScreen();
                frame.setLocation(current.x - mouseClickPoint.x, current.y - mouseClickPoint.y);
            }
        };

        // Attach listener to the top bar (where it's easiest to click and drag)
        topBar.addMouseListener(dragListener);
        topBar.addMouseMotionListener(dragListener);

        // Update display once before showing
        updateDisplay();

        // ===== Show Window =====
        frame.setVisible(true);

        // ===== Update Loop (Timer) =====
        // The Timer runs on the EDT, so it's safe to call updateDisplay()
        new Timer(500, e -> updateDisplay()).start();
    }

    // === Display Update (Always on EDT) ===
    private void updateDisplay() {
        if (textArea == null) return;

        // Use a block to synchronize access to shared fields (though less critical
        // here since the Timer is EDT, it's good practice for reading state)
        // A lock is not strictly necessary if all setters below also use EDT, but
        // for safety across all threads, let's just make the fields volatile/final
        // OR rely on the setters/getters being strictly single-threaded (EDT)

        // Since the setters will be called from another thread (Minecraft),
        // they MUST wrap updates in invokeLater. The fields themselves do NOT need
        // to be static anymore, which is the main threading safety improvement.

        String text = String.format(
                " Current State:  %s%n" +
                        " Current Action: %s%n" +
                        " Req. Prestige:  %d%n" +
                        " Prestige:       %d%n" +
                        " Ascension:      %d%n" +
                        " Coins:          %d%n", // Use comma for thousands separator
                currentState, currentAction, requiredPrestige, prestige, ascension, coins
        );
        textArea.setText(text);
    }

    // === Setters (MUST use SwingUtilities.invokeLater) ===
    // These methods will be called from the Minecraft thread, so they must
    // delegate the state change to the EDT to prevent race conditions with updateDisplay()

    public void setCurrentState(String state) {
        SwingUtilities.invokeLater(() -> this.currentState = state);
    }

    public void setCurrentAction(String action) {
        SwingUtilities.invokeLater(() -> this.currentAction = action);
    }

    public void setPrestige(int value) {
        SwingUtilities.invokeLater(() -> this.prestige = value);
    }

    public void setAscension(int value) {
        SwingUtilities.invokeLater(() -> this.ascension = value);
    }

    public void setCoins(int value) {
        SwingUtilities.invokeLater(() -> this.coins = value);
    }

    public void setRequiredPrestige(int value){
        SwingUtilities.invokeLater(() -> this.requiredPrestige = value);
    }
}