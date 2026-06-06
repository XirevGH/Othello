package Algoritmdesigntekniker;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class OthelloGUI extends JFrame {
    // Constants placed at the very top to prevent illegal forward reference compiler errors
    private final int BLACK = 1;
    private final int WHITE = 2;
    private final int EMPTY = 0; 

    private OthelloBoard game; 
    private BoardSquare[][] squares = new BoardSquare[8][8];
    private JLabel statusLabel;
    private JLabel aiInfoLabel; 
    private JButton stopAIButton; 
    private JPanel boardPanel;
    private Timer aiProgressTimer; 
    
    private JRadioButton bitboardRadio;
    private JRadioButton ooRadio;
    private JRadioButton nestedRadio;
    private JCheckBox visualizerCheckbox;
    private JCheckBox alphaBetaCheckbox;
    private JCheckBox moveOrderingCheckbox;

    private JSlider depthSlider;
    private JLabel depthLabel;

    private SwingWorker<Integer, Void> aiWorker; 
    private boolean aiSuspended = false; 

    // Selected engine type tracking (Renamed for structural precision)
    private enum EngineType { BITBOARD, FLAT_ARRAY, NESTED_OBJECT }
    private EngineType selectedEngine = EngineType.BITBOARD;

    // Remembers the user's last manual Move Ordering preference
    private boolean lastMoveOrderingPreference = true;

    private long aiStartTime = 0; 

    // Visualizer Mode Tracking Variables
    private boolean visualizerMode = false;
    private int[][] debugHighlights = new int[8][8]; 
    private int currentPlayer = BLACK; 
    private int aiDepth = 7; 
    private volatile boolean isProcessing = false; // volatile protects thread visibility

    // Header buttons promoted to class fields for state locking
    private JButton restartBtn;
    private JButton loadRecordBtn;
    private JButton benchmarkBtn;

    // Benchmark Thread and Worker Tracking
    private Thread activeBenchmarkThread;
    private SwingWorker<Integer, Void> activeBenchmarkWorker;

    // VCR Control Components
    private JPanel row3; 
    private JButton prevButton;
    private JButton playPauseButton;
    private JButton nextButton;
    private JSlider speedSlider;
    
    private volatile boolean isAutoPlaying = false; 
    private Timer autoPlayTimer;
    private int visualizerSpeedMs = 500; 

    // State History Storage
    private static class VizState {
        int r, c, dir, step;
        boolean[][] persistentValids;
        boolean[][] persistentInvalids;
        int[][] highlights;
        String explanation;
        
        // Advanced graphic overlay metrics
        List<int[]> arrows = new ArrayList<>();   // Stores [startRow, startCol, endRow, endCol, colorType]
        List<int[]> wallHits = new ArrayList<>(); // Stores [wallRow, wallCol, edgeRow, edgeCol, offscreenRow, offscreenCol]
        String[][] markers = new String[8][8];    // Stores "X", "✓" or null
        String[][] tileTexts = new String[8][8];  // Dictates the exact text label on each tile
        
        VizState(int r, int c, int dir, int step, boolean[][] valids, boolean[][] invalids, int[][] highlights, String explanation) {
            this.r = r;
            this.c = c;
            this.dir = dir;
            this.step = step;
            this.persistentValids = new boolean[8][8];
            for (int i = 0; i < 8; i++) {
                System.arraycopy(valids[i], 0, this.persistentValids[i], 0, 8);
            }
            this.persistentInvalids = new boolean[8][8];
            for (int i = 0; i < 8; i++) {
                System.arraycopy(invalids[i], 0, this.persistentInvalids[i], 0, 8);
            }
            this.highlights = new int[8][8];
            for (int i = 0; i < 8; i++) {
                System.arraycopy(highlights[i], 0, this.highlights[i], 0, 8);
            }
            this.explanation = explanation;
        }

        // Deep-copy setters to guarantee isolated framing states
        public void setTileTexts(String[][] src) {
            for (int i = 0; i < 8; i++) {
                System.arraycopy(src[i], 0, this.tileTexts[i], 0, 8);
            }
        }

        public void setMarkers(String[][] src) {
            for (int i = 0; i < 8; i++) {
                System.arraycopy(src[i], 0, this.markers[i], 0, 8);
            }
        }
    }
    private List<VizState> vizHistory = new ArrayList<>();
    private int historyIndex = -1;

    // Clockwise directions starting from East
    private static final int[] DR = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DC = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final String[] DIR_NAMES = {
        "East", "South-East", "South", "South-West", "West", "North-West", "North", "North-East"
    };
    private static final String[] DIR_ARROWS = {
        "→", "↘", "↓", "↙", "←", "↖", "↑", "↗"
    };

    // Standard vector icons custom drawn directly on components
    private static class PlayIcon implements Icon {
        private final int width;
        private final int height;
        private final Color color;

        public PlayIcon(int width, int height, Color color) {
            this.width = width;
            this.height = height;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            int[] xPoints = { x + 2, x + 2, x + width - 2 };
            int[] yPoints = { y + 2, y + height - 2, y + height / 2 };
            g2.fillPolygon(xPoints, yPoints, 3);
            g2.dispose();
        }

        @Override public int getIconWidth() { return width; }
        @Override public int getIconHeight() { return height; }
    }

    private static class StopIcon implements Icon {
        private final int width;
        private final int height;
        private final Color color;

        public StopIcon(int width, int height, Color color) {
            this.width = width;
            this.height = height;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRect(x + 2, y + 2, width - 4, height - 4);
            g2.dispose();
        }

        @Override public int getIconWidth() { return width; }
        @Override public int getIconHeight() { return height; }
    }

    public OthelloGUI() {
        game = new OthelloBitboard(); 
        
        setTitle("Othello AI - State Visualization");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        createHeaderPanel(); 
        createBoard();
        createStatusBar();

        // Apply initial configurations cleanly
        boolean abSelected = alphaBetaCheckbox.isSelected();
        moveOrderingCheckbox.setEnabled(abSelected);
        if (abSelected) {
            moveOrderingCheckbox.setSelected(lastMoveOrderingPreference);
        } else {
            moveOrderingCheckbox.setSelected(false);
        }
        
        game.setUseAlphaBeta(abSelected);
        game.setUseMoveOrdering(abSelected && lastMoveOrderingPreference);

        pack();
        setSize(950, 740); 
        setLocationRelativeTo(null); 
        updateStatus();
    }

    private void applyHistoryFrame() {
        if (historyIndex < 0 || historyIndex >= vizHistory.size()) return;

        VizState state = vizHistory.get(historyIndex);
        
        for (int r = 0; r < 8; r++) {
            System.arraycopy(state.highlights[r], 0, debugHighlights[r], 0, 8);
        }
        
        // Appends active directional Unicode arrows to status explanations
        String arrowSym = "";
        if (state.dir >= 0 && state.dir < 8) {
            arrowSym = " " + DIR_ARROWS[state.dir];
        }
        
        aiInfoLabel.setText(String.format("Step %d/%d: %s%s", historyIndex + 1, vizHistory.size(), state.explanation, arrowSym));
        
        // Prev/Next buttons are locked if autoplay is running
        prevButton.setEnabled(!isAutoPlaying && historyIndex > 0);
        nextButton.setEnabled(!isAutoPlaying && historyIndex < vizHistory.size() - 1);
        
        boardPanel.repaint();
    }

    private void stepForward() {
        if (historyIndex < vizHistory.size() - 1) {
            historyIndex++;
            applyHistoryFrame();
        } else {
            stopAutoPlay();
        }
    }

    private void stepBackward() {
        if (historyIndex > 0) {
            historyIndex--;
            applyHistoryFrame();
        }
    }

    private void toggleAutoPlay() {
        if (isAutoPlaying) {
            stopAutoPlay();
        } else {
            startAutoPlay();
        }
    }

    private void startAutoPlay() {
        isAutoPlaying = true;
        playPauseButton.setIcon(new StopIcon(14, 14, new Color(220, 20, 60))); // Red Stop icon
        
        prevButton.setEnabled(false);
        nextButton.setEnabled(false);
        
        if (autoPlayTimer != null) {
            autoPlayTimer.stop();
        }
        
        if (historyIndex >= vizHistory.size() - 1) {
            historyIndex = 0;
        }

        autoPlayTimer = new Timer(visualizerSpeedMs, e -> stepForward());
        autoPlayTimer.start();
    }

    private void stopAutoPlay() {
        isAutoPlaying = false;
        playPauseButton.setIcon(new PlayIcon(14, 14, new Color(34, 139, 34))); // Green Play icon
        
        if (autoPlayTimer != null) {
            autoPlayTimer.stop();
        }
        
        // Re-evaluate boundary conditions for step buttons
        prevButton.setEnabled(historyIndex > 0);
        nextButton.setEnabled(historyIndex < vizHistory.size() - 1);
    }

    private void createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(245, 245, 245));
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        actionPanel.setOpaque(false);

        restartBtn = new JButton("Restart Game");
        styleHeaderButton(restartBtn);
        restartBtn.addActionListener(e -> restartGame());

        loadRecordBtn = new JButton("Load Record (33)");
        styleHeaderButton(loadRecordBtn);
        loadRecordBtn.addActionListener(e -> loadTakizawaRecord());

        benchmarkBtn = new JButton("Run Benchmark");
        styleHeaderButton(benchmarkBtn);
        benchmarkBtn.addActionListener(e -> {
            // If currently executing, act as a Stop button
            if (isProcessing && benchmarkBtn.getText().equals("Stop Benchmark")) {
                stopAutomatedBenchmark();
            } else {
                runAutomatedBenchmark();
            }
        });

        JPanel depthPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        depthPanel.setOpaque(false);

        depthLabel = new JLabel("Depth: " + aiDepth);
        depthLabel.setFont(new Font("Arial", Font.BOLD, 12));
        depthLabel.setPreferredSize(new Dimension(65, 20));

        depthSlider = new JSlider(JSlider.HORIZONTAL, 1, 15, aiDepth);
        depthSlider.setPreferredSize(new Dimension(140, 40));
        depthSlider.setOpaque(false);
        depthSlider.setMinorTickSpacing(1);
        depthSlider.setPaintTicks(true);

        java.util.Hashtable<Integer, JLabel> labelTable = new java.util.Hashtable<>();
        Font tickFont = new Font("Arial", Font.PLAIN, 9);
        labelTable.put(1, new JLabel("1"));
        labelTable.put(5, new JLabel("5"));
        labelTable.put(10, new JLabel("10"));
        labelTable.put(15, new JLabel("15"));
        for (JLabel lbl : labelTable.values()) lbl.setFont(tickFont);
        
        depthSlider.setLabelTable(labelTable);
        depthSlider.setPaintLabels(true);
        depthSlider.setCursor(new Cursor(Cursor.HAND_CURSOR));
        depthSlider.addChangeListener(e -> {
            aiDepth = depthSlider.getValue();
            depthLabel.setText("Depth: " + aiDepth);
        });

        depthPanel.add(depthLabel);
        depthPanel.add(depthSlider);

        actionPanel.add(restartBtn);
        actionPanel.add(loadRecordBtn);
        actionPanel.add(benchmarkBtn);
        actionPanel.add(depthPanel);

        JPanel enginePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12)); 
        enginePanel.setOpaque(false);

        visualizerCheckbox = new JCheckBox("Visualizer Mode");
        visualizerCheckbox.setFont(new Font("Arial", Font.BOLD, 12));
        visualizerCheckbox.setOpaque(false);
        visualizerCheckbox.setCursor(new Cursor(Cursor.HAND_CURSOR));
        visualizerCheckbox.addActionListener(e -> {
            if (isProcessing) {
                visualizerCheckbox.setSelected(visualizerMode); // Revert selection if simulation is underway
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            visualizerMode = visualizerCheckbox.isSelected();
            if (visualizerMode) {
                if (aiWorker != null) {
                    aiWorker.cancel(true);
                }
                aiSuspended = true;
                stopAIButton.setEnabled(false);
                depthSlider.setEnabled(false);
                alphaBetaCheckbox.setEnabled(false);
                moveOrderingCheckbox.setEnabled(false);
                
                // Route to the new history generators depending on your active engine selection
                if (selectedEngine == EngineType.BITBOARD) {
                    generateBitboardHistory();
                } else if (selectedEngine == EngineType.FLAT_ARRAY) {
                    generateFlatArrayOptimizedHistory(); // Plays our new optimized segment sweep
                } else {
                    generateOOHistory(); // Plays the traditional 8-direction neighbor search
                }

                row3.setVisible(true);
                historyIndex = 0;
                applyHistoryFrame();
                stopAutoPlay(); // Start visualizer in a stopped (paused) state
            }
            else {
                stopAutoPlay();
                row3.setVisible(false);
                clearHighlights();
                aiSuspended = false;
                isProcessing = false;
                depthSlider.setEnabled(true);
                
                boolean abActive = alphaBetaCheckbox.isSelected();
                alphaBetaCheckbox.setEnabled(true);
                moveOrderingCheckbox.setEnabled(abActive);
                if (abActive) {
                    moveOrderingCheckbox.setSelected(lastMoveOrderingPreference);
                } else {
                    moveOrderingCheckbox.setSelected(false);
                }

                updateStatus();
                boardPanel.repaint();
                
                if (isAITurn()) {
                    playAITurn();
                }
            }
            revalidate();
            repaint();
        });

        alphaBetaCheckbox = new JCheckBox("Alpha-Beta Pruning", true);
        alphaBetaCheckbox.setFont(new Font("Arial", Font.BOLD, 12));
        alphaBetaCheckbox.setOpaque(false);
        alphaBetaCheckbox.setCursor(new Cursor(Cursor.HAND_CURSOR));
        alphaBetaCheckbox.addActionListener(e -> {
            if (isProcessing) {
                alphaBetaCheckbox.setSelected(!alphaBetaCheckbox.isSelected()); // Revert state
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            boolean abSelected = alphaBetaCheckbox.isSelected();
            game.setUseAlphaBeta(abSelected);
            
            moveOrderingCheckbox.setEnabled(abSelected);
            if (abSelected) {
                moveOrderingCheckbox.setSelected(lastMoveOrderingPreference);
            } else {
                moveOrderingCheckbox.setSelected(false);
            }
            
            game.setUseMoveOrdering(abSelected && lastMoveOrderingPreference);
        });

        moveOrderingCheckbox = new JCheckBox("Move Ordering", true);
        moveOrderingCheckbox.setFont(new Font("Arial", Font.BOLD, 12));
        moveOrderingCheckbox.setOpaque(false);
        moveOrderingCheckbox.setCursor(new Cursor(Cursor.HAND_CURSOR));
        moveOrderingCheckbox.addActionListener(e -> {
            if (isProcessing) {
                moveOrderingCheckbox.setSelected(!moveOrderingCheckbox.isSelected()); // Revert state
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            
            boolean selected = moveOrderingCheckbox.isSelected();
            lastMoveOrderingPreference = selected;
            
            game.setUseMoveOrdering(selected);
        });

        JLabel engineLabel = new JLabel("Engine:");
        engineLabel.setFont(new Font("Arial", Font.BOLD, 12));

        bitboardRadio = new JRadioButton("Bitboard", true);
        bitboardRadio.setFont(new Font("Arial", Font.PLAIN, 12));
        bitboardRadio.setOpaque(false);
        bitboardRadio.setCursor(new Cursor(Cursor.HAND_CURSOR));
        bitboardRadio.addActionListener(e -> {
            if (isProcessing) {
                bitboardRadio.setSelected(selectedEngine == EngineType.BITBOARD);
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            if (selectedEngine != EngineType.BITBOARD) {
                selectedEngine = EngineType.BITBOARD;
                switchEngine(); 
            }
        });

        ooRadio = new JRadioButton("1D Flat Array", false); 
        ooRadio.setFont(new Font("Arial", Font.PLAIN, 12));
        ooRadio.setOpaque(false);
        ooRadio.setCursor(new Cursor(Cursor.HAND_CURSOR));
        ooRadio.addActionListener(e -> {
            if (isProcessing) {
                ooRadio.setSelected(selectedEngine == EngineType.FLAT_ARRAY);
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            if (selectedEngine != EngineType.FLAT_ARRAY) {
                selectedEngine = EngineType.FLAT_ARRAY;
                switchEngine(); 
            }
        });

        nestedRadio = new JRadioButton("2D Cell Objects", false); 
        nestedRadio.setFont(new Font("Arial", Font.PLAIN, 12));
        nestedRadio.setOpaque(false);
        nestedRadio.setCursor(new Cursor(Cursor.HAND_CURSOR));
        nestedRadio.addActionListener(e -> {
            if (isProcessing) {
                nestedRadio.setSelected(selectedEngine == EngineType.NESTED_OBJECT);
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            if (selectedEngine != EngineType.NESTED_OBJECT) {
                selectedEngine = EngineType.NESTED_OBJECT;
                switchEngine(); 
            }
        });

        ButtonGroup engineGroup = new ButtonGroup();
        engineGroup.add(bitboardRadio);
        engineGroup.add(ooRadio);
        engineGroup.add(nestedRadio);

        enginePanel.add(visualizerCheckbox);
        enginePanel.add(alphaBetaCheckbox);
        enginePanel.add(moveOrderingCheckbox);
        enginePanel.add(Box.createHorizontalStrut(10));
        enginePanel.add(engineLabel);
        enginePanel.add(bitboardRadio);
        enginePanel.add(ooRadio);
        enginePanel.add(nestedRadio);

        headerPanel.add(actionPanel, BorderLayout.WEST);
        headerPanel.add(enginePanel, BorderLayout.EAST);

        // NEW: Adaptive responsive header layout observer
        headerPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int width = headerPanel.getWidth();
                if (width < 950) {
                    if (headerPanel.getLayout() instanceof BorderLayout) {
                        headerPanel.removeAll();
                        headerPanel.setLayout(new GridLayout(2, 1, 0, 2));
                        headerPanel.add(actionPanel);
                        headerPanel.add(enginePanel);
                        headerPanel.revalidate();
                        headerPanel.repaint();
                    }
                } else {
                    if (!(headerPanel.getLayout() instanceof BorderLayout)) {
                        headerPanel.removeAll();
                        headerPanel.setLayout(new BorderLayout());
                        headerPanel.add(actionPanel, BorderLayout.WEST);
                        headerPanel.add(enginePanel, BorderLayout.EAST);
                        headerPanel.revalidate();
                        headerPanel.repaint();
                    }
                }
            }
        });

        add(headerPanel, BorderLayout.NORTH);
    }

    private void styleHeaderButton(JButton btn) {
        btn.setFont(new Font("Arial", Font.BOLD, 12));
        btn.setFocusPainted(false); // Removes focus ring
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBackground(Color.WHITE);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
    }

    private void loadTakizawaRecord() {
        if (isProcessing) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        if (aiWorker != null) {
            aiWorker.cancel(true);
            aiWorker = null;
        }
        if (aiProgressTimer != null && aiProgressTimer.isRunning()) {
            aiProgressTimer.stop();
        }
        stopAutoPlay();
        vizHistory.clear();
        historyIndex = -1;
        clearHighlights();

        // 1. Reset Board to fresh engine
        if (selectedEngine == EngineType.BITBOARD) {
            game = new OthelloBitboard(); 
        } else if (selectedEngine == EngineType.FLAT_ARRAY) {
            game = new OthelloFlatArray(); 
        } else {
            game = new OthelloCellObjects(); 
        }

        // 2. Play the 26-move Takizawa record sequence
        String[] moves = {
            "F5", "D6", "C4", "F3", "C5", "B4", "B3", "E6", "C6", "G5", "F6", "C7", "C3", "D2", "C2", "B2", "F4", "G4", "G3", "G7", "G6", "E7", "D3", "G2", "H3", "B6"
        };

        int activePlayer = BLACK;
        for (String move : moves) {
            int idx = OthelloBitboard.algebraicToIndex(move); 
            if (idx != -1) {
                int r = idx / 8;
                int c = idx % 8;
                if (game.isValidMove(r, c, activePlayer)) {
                    game.makeMove(r, c, activePlayer);
                }
                activePlayer = (activePlayer == BLACK) ? WHITE : BLACK;
            }
        }

        currentPlayer = activePlayer; // Turns pass correctly (Black's turn to move)
        isProcessing = false;

        // 3. Re-initialize visualizer or active play modes
        if (visualizerMode) {
            aiSuspended = true;
            stopAIButton.setEnabled(false);
            stopAIButton.setVisible(false); // Hide during non-game visualizer runs
            if (depthSlider != null) depthSlider.setEnabled(false);
            
            if (selectedEngine == EngineType.BITBOARD) {
                    generateBitboardHistory();
                } else if (selectedEngine == EngineType.FLAT_ARRAY) {
                    generateFlatArrayOptimizedHistory(); // New segment-scan visualizer
                } else {
                    generateOOHistory();
                }
            
            historyIndex = 0;
            applyHistoryFrame();
            stopAutoPlay(); // Start visualizer in a stopped (paused) state
            row3.setVisible(true);
        } else {
            aiSuspended = false; 
            aiInfoLabel.setText("Takizawa Record-33 loaded. Black's turn.");
            stopAIButton.setEnabled(false);
            stopAIButton.setVisible(false); // Hide by default
            if (depthSlider != null) depthSlider.setEnabled(true);
            if (bitboardRadio != null) bitboardRadio.setEnabled(true);
            if (ooRadio != null) ooRadio.setEnabled(true);
            if (nestedRadio != null) nestedRadio.setEnabled(true);
            if (visualizerCheckbox != null) visualizerCheckbox.setEnabled(true);
            
            boolean abSelected = alphaBetaCheckbox.isSelected();
            alphaBetaCheckbox.setEnabled(true);
            moveOrderingCheckbox.setEnabled(abSelected);
            if (abSelected) {
                moveOrderingCheckbox.setSelected(lastMoveOrderingPreference);
            } else {
                moveOrderingCheckbox.setSelected(false);
            }
            
            game.setUseAlphaBeta(abSelected);
            game.setUseMoveOrdering(abSelected && lastMoveOrderingPreference);
            row3.setVisible(false);
        }

        boardPanel.repaint();
        updateStatus();
        revalidate();
        repaint();

        if (isAITurn()) {
            playAITurn();
        }
    }

    private void createBoard() {
        boardPanel = new JPanel(new GridLayout(8, 8)) {
            @Override
            public void paint(Graphics g) {
                super.paint(g); // Draws background grids and discs first
                drawVisualizerOverlays((Graphics2D) g); // Overlays visualizer wall arrows cleanly
            }
        };
        boardPanel.setBackground(Color.BLACK);
        boardPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                squares[row][col] = new BoardSquare(row, col);
                boardPanel.add(squares[row][col]);
            }
        }
        add(boardPanel, BorderLayout.CENTER);
    }

    private void createStatusBar() {
        JPanel statusPanel = new JPanel(new GridLayout(3, 1, 4, 4)); 
        statusPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JPanel row1 = new JPanel(new BorderLayout());
        row1.setOpaque(false);
        statusLabel = new JLabel("Welcome to Othello!", SwingConstants.LEFT);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        row1.add(statusLabel, BorderLayout.WEST);

        JPanel row2 = new JPanel(new BorderLayout());
        row2.setOpaque(false);
        
        aiInfoLabel = new JLabel("", SwingConstants.LEFT); 
        aiInfoLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        aiInfoLabel.setForeground(new Color(100, 100, 100));
        row2.add(aiInfoLabel, BorderLayout.WEST);

        stopAIButton = new JButton("Stop AI");
        stopAIButton.setEnabled(false);
        stopAIButton.setVisible(false); // Hidden by default
        stopAIButton.setFocusPainted(false); // Removes focus ring
        stopAIButton.setFont(new Font("Arial", Font.BOLD, 11));
        stopAIButton.addActionListener(e -> {
            if (aiWorker != null && !aiWorker.isDone()) {
                aiWorker.cancel(true); 
            }
        });
        row2.add(stopAIButton, BorderLayout.EAST);

        row3 = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        row3.setOpaque(false);
        row3.setVisible(false); 

        prevButton = new JButton("<< Prev");
        prevButton.setFont(new Font("Arial", Font.BOLD, 11));
        prevButton.setFocusPainted(false); // Removes focus ring
        prevButton.addActionListener(e -> {
            if (isProcessing) return; // MODIFIED: Disable clicks during benchmark
            stepBackward();
        });

        playPauseButton = new JButton();
        playPauseButton.setFocusPainted(false); // Removes focus ring
        playPauseButton.setIcon(new PlayIcon(14, 14, new Color(34, 139, 34))); // Green Play icon
        playPauseButton.setPreferredSize(new Dimension(50, 30));
        playPauseButton.addActionListener(e -> {
            if (isProcessing) return; // MODIFIED: Disable clicks during benchmark
            toggleAutoPlay();
        });

        nextButton = new JButton("Next >>");
        nextButton.setFont(new Font("Arial", Font.BOLD, 11));
        nextButton.setFocusPainted(false); // Removes focus ring
        nextButton.addActionListener(e -> {
            if (isProcessing) return; // MODIFIED: Disable clicks during benchmark
            stepForward();
        });

        JLabel speedLabel = new JLabel("Speed:");
        speedLabel.setFont(new Font("Arial", Font.BOLD, 11));

        // Slider value directly represents steps/frames per second (1 to 40)
        // 1 = 2000ms delay (Slowest) | 40 = 50ms delay (Fastest, 2x faster than previous 100ms)
        // Default set to 31, which corresponds to exactly 500ms delay (default speed)
        speedSlider = new JSlider(JSlider.HORIZONTAL, 1, 40, 31);
        speedSlider.setPreferredSize(new Dimension(100, 30));
        speedSlider.setOpaque(false);
        speedSlider.addChangeListener(e -> {
            int val = speedSlider.getValue();
            visualizerSpeedMs = 2000 - (val - 1) * 50; // Linear mapping
            if (autoPlayTimer != null && autoPlayTimer.isRunning()) {
                autoPlayTimer.setDelay(visualizerSpeedMs);
            }
        });

        row3.add(prevButton);
        row3.add(playPauseButton);
        row3.add(nextButton);
        row3.add(speedLabel);
        row3.add(speedSlider);

        statusPanel.add(row1);
        statusPanel.add(row2);
        statusPanel.add(row3);
        
        add(statusPanel, BorderLayout.SOUTH);
    }

    private void restartGame() {
        if (aiWorker != null) {
            aiWorker.cancel(true);
            aiWorker = null;
        }
        if (aiProgressTimer != null && aiProgressTimer.isRunning()) {
            aiProgressTimer.stop();
        }
        stopAutoPlay();
        vizHistory.clear();
        historyIndex = -1;

        clearHighlights();

        if (selectedEngine == EngineType.BITBOARD) {
            if (bitboardRadio != null) bitboardRadio.setSelected(true);
            game = new OthelloBitboard(); 
        } else if (selectedEngine == EngineType.FLAT_ARRAY) {
            if (ooRadio != null) ooRadio.setSelected(true);
            game = new OthelloFlatArray(); 
        } else {
            if (nestedRadio != null) nestedRadio.setSelected(true);
            game = new OthelloCellObjects(); 
        }

        currentPlayer = BLACK;
        isProcessing = false;
        
        if (visualizerMode) {
            aiSuspended = true;
            stopAIButton.setEnabled(false);
            stopAIButton.setVisible(false);
            if (depthSlider != null) depthSlider.setEnabled(false);
            
            if (selectedEngine == EngineType.BITBOARD) {
                generateBitboardHistory();
            } else if (selectedEngine == EngineType.FLAT_ARRAY) {
                generateFlatArrayOptimizedHistory(); // Added routing
            } else {
                generateOOHistory();
            }
            
            historyIndex = 0;
            applyHistoryFrame();
            stopAutoPlay();
            row3.setVisible(true);
        } else {
            aiSuspended = false; 
            aiInfoLabel.setText("");
            stopAIButton.setEnabled(false);
            stopAIButton.setVisible(false); // Hide by default
            if (depthSlider != null) depthSlider.setEnabled(true);
            if (bitboardRadio != null) bitboardRadio.setEnabled(true);
            if (ooRadio != null) ooRadio.setEnabled(true);
            if (nestedRadio != null) nestedRadio.setEnabled(true);
            if (visualizerCheckbox != null) visualizerCheckbox.setEnabled(true);
            
            boolean abSelected = alphaBetaCheckbox.isSelected();
            alphaBetaCheckbox.setEnabled(true);
            moveOrderingCheckbox.setEnabled(abSelected);
            if (abSelected) {
                moveOrderingCheckbox.setSelected(lastMoveOrderingPreference);
            } else {
                moveOrderingCheckbox.setSelected(false);
            }
            
            game.setUseAlphaBeta(abSelected);
            game.setUseMoveOrdering(abSelected && lastMoveOrderingPreference);
            row3.setVisible(false);
        }

        boardPanel.repaint();
        updateStatus();
        revalidate();
        repaint();

        if (isAITurn()) {
            playAITurn();
        }
    }

    private void switchEngine() {
        if (aiWorker != null) {
            aiWorker.cancel(true);
            aiWorker = null;
        }
        if (aiProgressTimer != null && aiProgressTimer.isRunning()) {
            aiProgressTimer.stop();
        }
        stopAutoPlay();

        // 1. Back up the active board layout
        int[][] tempBoard = new int[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                tempBoard[r][c] = game.getPieceAt(r, c);
            }
        }

        // 2. Instantiate the newly selected engine class
        if (selectedEngine == EngineType.BITBOARD) {
            if (bitboardRadio != null) bitboardRadio.setSelected(true);
            game = new OthelloBitboard(); 
        } else if (selectedEngine == EngineType.FLAT_ARRAY) {
            if (ooRadio != null) ooRadio.setSelected(true);
            game = new OthelloFlatArray(); 
        } else {
            if (nestedRadio != null) nestedRadio.setSelected(true);
            game = new OthelloCellObjects(); 
        }
        game.setUseAlphaBeta(alphaBetaCheckbox.isSelected());
        game.setUseMoveOrdering(moveOrderingCheckbox.isSelected());

        // 3. Write the backed-up piece configuration into the new engine
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                game.setPieceAt(r, c, tempBoard[r][c]);
            }
        }

        // 4. If visualizer is active, regenerate the history frames on the new engine instantly
        if (visualizerMode) {
            aiSuspended = true;
            stopAIButton.setEnabled(false);
            if (depthSlider != null) depthSlider.setEnabled(false);
            
            if (selectedEngine == EngineType.BITBOARD) {
                generateBitboardHistory();
            } else if (selectedEngine == EngineType.FLAT_ARRAY) {
                generateFlatArrayOptimizedHistory(); // Added routing
            } else {
                generateOOHistory();
            }
            
            historyIndex = 0;
            applyHistoryFrame();
            stopAutoPlay();
            row3.setVisible(true);
        } else {
            row3.setVisible(false);
            clearHighlights();
            aiSuspended = false;
            isProcessing = false;
            if (depthSlider != null) depthSlider.setEnabled(true);
            if (bitboardRadio != null) bitboardRadio.setEnabled(true);
            if (ooRadio != null) ooRadio.setEnabled(true);
            if (nestedRadio != null) nestedRadio.setEnabled(true);
            if (visualizerCheckbox != null) visualizerCheckbox.setEnabled(true);
            
            boolean abSelected = alphaBetaCheckbox.isSelected();
            alphaBetaCheckbox.setEnabled(true);
            moveOrderingCheckbox.setEnabled(abSelected);
            if (abSelected) {
                moveOrderingCheckbox.setSelected(lastMoveOrderingPreference);
            } else {
                moveOrderingCheckbox.setSelected(false);
            }
            
            game.setUseAlphaBeta(abSelected);
            game.setUseMoveOrdering(abSelected && lastMoveOrderingPreference);
        }

        boardPanel.repaint();
        updateStatus();
        revalidate();
        repaint();

        // If it was the AI's turn during toggle, resume its search immediately on the new engine
        if (isAITurn()) {
            playAITurn();
        }
    }

    private boolean isAITurn() {
        if (aiSuspended) return false; 
        return currentPlayer == WHITE;
    }

    private void handleHumanMove(int row, int col) {
        if (isProcessing || isAITurn() || visualizerMode) return;

        if (game.isValidMove(row, col, currentPlayer)) {
            game.makeMove(row, col, currentPlayer);
            boardPanel.repaint();
            advanceTurn();
        } else {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    private void handleSquareHover(int row, int col) {
        // Handled sequentially in visualizer history frame generations
    }

    private void advanceTurn() {
        aiSuspended = false; 
        
        if (game.isGameOver()) {
            handleGameOver();
            return;
        }

        currentPlayer = (currentPlayer == BLACK) ? WHITE : BLACK;
        boolean hasMoves = !game.getValidMoves(currentPlayer).isEmpty();

        if (!hasMoves) {
            String skipped = (currentPlayer == BLACK) ? "Black" : "White";
            JOptionPane.showMessageDialog(this, skipped + " has no valid moves. Turn passes.");
            currentPlayer = (currentPlayer == BLACK) ? WHITE : BLACK;
            
            if (game.getValidMoves(currentPlayer).isEmpty()) {
                handleGameOver();
                return;
            }
        }

        updateStatus();
        boardPanel.repaint();

        if (isAITurn()) {
            playAITurn();
        }
    }

    private void playAITurn() {
        isProcessing = true;
        stopAIButton.setVisible(true); // Visible when active AI starts thinking
        stopAIButton.setEnabled(true); 
        depthSlider.setEnabled(false); 
        bitboardRadio.setEnabled(false);
        ooRadio.setEnabled(false);
        nestedRadio.setEnabled(false);
        visualizerCheckbox.setEnabled(false);
        alphaBetaCheckbox.setEnabled(false);
        moveOrderingCheckbox.setEnabled(false);
        restartBtn.setEnabled(false);
        loadRecordBtn.setEnabled(false);

        aiStartTime = System.nanoTime();

        aiProgressTimer = new Timer(50, e -> {
            long checked = game.getEvaluatedNodes();
            long evals = game.getEvaluationCount();
            long elapsedNanos = System.nanoTime() - aiStartTime;
            double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
            
            long nps = 0;
            if (elapsedNanos > 1_000_000) { 
                nps = Math.round(checked / elapsedSeconds);
            }
            
            aiInfoLabel.setText(String.format("Evaluating... %,d states, %,d evals (%,d/sec)", checked, evals, nps));
        });
        aiProgressTimer.start();

        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                return game.findBestMove(currentPlayer, aiDepth);
            }

            @Override
            protected void done() {
                aiProgressTimer.stop(); 
                stopAIButton.setEnabled(false); 
                stopAIButton.setVisible(false); // Hidden when AI finishes
                depthSlider.setEnabled(true); 
                bitboardRadio.setEnabled(true);
                ooRadio.setEnabled(true);
                nestedRadio.setEnabled(true);
                visualizerCheckbox.setEnabled(true);
                alphaBetaCheckbox.setEnabled(true);
                moveOrderingCheckbox.setEnabled(alphaBetaCheckbox.isSelected());
                restartBtn.setEnabled(true);
                loadRecordBtn.setEnabled(true);

                if (aiWorker != this) {
                    return; 
                }

                if (isCancelled()) {
                    aiSuspended = true; 
                    isProcessing = false;
                    aiInfoLabel.setText("AI Stopped. Click to make manual move.");
                    updateStatus();
                    boardPanel.repaint();
                    return;
                }

                try {
                    int bestMoveIndex = get();
                    long checked = game.getEvaluatedNodes();
                    long evals = game.getEvaluationCount();
                    long elapsedNanos = System.nanoTime() - aiStartTime;
                    double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
                    
                    long nps = 0;
                    if (elapsedNanos > 1_000_000) {
                        nps = Math.round(checked / elapsedSeconds);
                    }
                    
                    aiInfoLabel.setText(String.format("Evaluated %,d states (%,d evals) at depth %d in %.3fs (%,d/sec)", 
                            checked, evals, aiDepth, elapsedSeconds, nps));

                    if (bestMoveIndex != -1) {
                        int r = bestMoveIndex / 8;
                        int c = bestMoveIndex % 8;
                        game.makeMove(r, c, currentPlayer);
                    }
                    boardPanel.repaint();
                    isProcessing = false;
                    advanceTurn();
                } catch (Exception e) {
                    e.printStackTrace();
                    isProcessing = false;
                }
            }
        };
        
        aiWorker = worker; 
        worker.execute();
    }

    private void handleGameOver() {
        isProcessing = true;
        boardPanel.repaint();
        int winner = game.getWinner();
        int blackCount = game.countDiscs(BLACK);
        int whiteCount = game.countDiscs(WHITE);

        String message;
        if (winner == 0) {
            message = "Game ended in a draw!\nBlack: " + blackCount + " | White: " + whiteCount;
        } else {
            String winnerName = (winner == BLACK) ? "Black" : "White";
            message = winnerName + " wins!\nBlack: " + blackCount + " | White: " + whiteCount;
        }

        statusLabel.setText("Game Over!");
        JOptionPane.showMessageDialog(this, message, "Game Over", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateStatus() {
        int blackCount = game.countDiscs(BLACK);
        int whiteCount = game.countDiscs(WHITE);
        String turn = (currentPlayer == BLACK) ? "Black's turn" : "White's turn";
        String playerType = isAITurn() ? "[AI]" : (aiSuspended ? "[Manual Pause]" : "[You]");
        
        String engineName;
        if (selectedEngine == EngineType.BITBOARD) {
            engineName = "Bitboard";
        } else if (selectedEngine == EngineType.FLAT_ARRAY) {
            engineName = "1D Flat Array";
        } else {
            engineName = "2D Cell Objects";
        }

        statusLabel.setText(String.format("Black: %d  |  White: %d   ---   %s %s   [%s Engine]", 
                blackCount, whiteCount, turn, playerType, engineName));
    }

    private void applyTransientOverlay(int[][] curHighlights, String[][] markers, String[][] tileTexts, 
                                        boolean[][] currentSpaceInvalids, boolean[][] currentSpaceFailedOpponents,
                                        String[][] currentSquareMarkers, String[][] currentSquareTexts,
                                        boolean[][] evaluated, boolean[][] persistentValids, boolean[][] persistentInvalids) {
        
        // 1. Apply highlights for currentSpaceInvalids (Red) and currentSpaceFailedOpponents (Red)
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (currentSpaceInvalids[i][j] || currentSpaceFailedOpponents[i][j]) {
                    curHighlights[i][j] = 4; // Red Highlight
                }
            }
        }

        // 2. Apply markers and texts for currentSpaceInvalids safely without overwriting evaluated squares
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (currentSpaceInvalids[i][j]) {
                    if (evaluated[i][j]) {
                        // Already evaluated (Valid/Invalid) - leave text and symbol completely untouched!
                    } else if ("Own Piece".equals(currentSquareTexts[i][j])) {
                        markers[i][j] = "X";
                        tileTexts[i][j] = "Own Piece";
                    } else {
                        markers[i][j] = "X";
                        tileTexts[i][j] = "Empty";
                    }
                }
            }
        }
    }
    
    private void clearHighlights() {
        clearArray(debugHighlights);
    }

    private void clearArray(int[][] arr) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                arr[i][j] = 0;
            }
        }
    }

    private boolean isPotentialBitboardTarget(int r, int c, int player, int dirIndex) {
        if (game.getPieceAt(r, c) != EMPTY) return false; // Target cell must be empty
        
        int dr = DR[dirIndex];
        int dc = DC[dirIndex];
        int currR = r - dr;
        int currC = c - dc;
        
        while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8) {
            int piece = game.getPieceAt(currR, currC);
            if (piece == EMPTY) {
                // An empty space in between blocks the bitboard shift propagation downstream
                return false; 
            }
            if (piece == player) {
                // Found our friendly piece connected via occupied pieces
                return true;
            }
            // If it is an opponent piece, we continue scanning backwards
            currR -= dr;
            currC -= dc;
        }
        return false;
    }

    // Highlights all empty tiles under the influence of the active directional shift as Blue ("Evaluating")
    private void applyPotentialTargets(int[][] curHighlights, String[][] tileTexts, boolean[][] valids, boolean[][] invalids, int dirIndex) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (isPotentialBitboardTarget(r, c, currentPlayer, dirIndex)) {
                    if (valids[r][c]) {
                        curHighlights[r][c] = 3; // Green (Valid)
                        tileTexts[r][c] = "Valid";
                    } else if (invalids[r][c]) {
                        curHighlights[r][c] = 4; // Red (Invalid)
                        tileTexts[r][c] = "Invalid";
                    } else {
                        curHighlights[r][c] = 1; // Turquoise (Evaluating)
                        tileTexts[r][c] = "Evaluating";
                    }
                }
            }
        }
    }

    private void applyOOCandidateHighlights(int[][] curHighlights, String[][] tileTexts, String[][] markers,
                                            boolean[][] evaluated,
                                            boolean[][] persistentValids, boolean[][] persistentInvalids,
                                            int nr, int nc, boolean isCurrentlyEvaluating) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (r == nr && c == nc && isCurrentlyEvaluating) {
                    curHighlights[r][c] = 1; // Blue (Evaluating)
                    tileTexts[r][c] = "Evaluating";
                    markers[r][c] = "?"; // Automatically add active "?" to evaluating tile
                } else if (evaluated[r][c]) {
                    if (persistentValids[r][c]) {
                        curHighlights[r][c] = 3; // Green (Valid)
                        tileTexts[r][c] = "Valid";
                        markers[r][c] = "✓";
                    } else if (persistentInvalids[r][c]) {
                        curHighlights[r][c] = 4; // Red (Invalid)
                        tileTexts[r][c] = "Invalid";
                        markers[r][c] = "X";
                    }
                } else {
                    // Unexplored cell - standard green with absolutely no highlight (0)
                    curHighlights[r][c] = 0; 
                    
                    // Preserve active local scan texts ("Own Piece", "Empty", "Valid")
                    if (tileTexts[r][c] != null && (tileTexts[r][c].equals("Own Piece") || tileTexts[r][c].equals("Empty") || tileTexts[r][c].equals("Valid"))) {
                        // Keep the active text during current neighbor evaluations
                    } else {
                        tileTexts[r][c] = ""; 
                    }
                    
                    // Preserve active local scan markers ("X", "✓")
                    if (markers[r][c] != null && (markers[r][c].equals("X") || markers[r][c].equals("✓") || markers[r][c].equals("?"))) {
                        // Keep the active marker during current neighbor evaluations
                    } else {
                        markers[r][c] = null;
                    }
                }
            }
        }
    }

    // Deep-copies arrow coordinate lists safely during history frame creation
    private List<int[]> cloneArrows(List<int[]> source) {
        List<int[]> clone = new ArrayList<>();
        for (int[] a : source) {
            clone.add(new int[]{a[0], a[1], a[2], a[3], a[4]});
        }
        return clone;
    }

    // Consolidated standard vector checkmark generator (Fixed "Tofu" Boxes)
    private void drawCheckmark(Graphics2D g2, int cx, int cy, int size) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(4.0f));
        
        // Shadow
        g2.setColor(new Color(0, 0, 0, 180));
        g2.drawLine(cx - size/3 + 1, cy + 1, cx - size/10 + 1, cy + size/3 + 1);
        g2.drawLine(cx - size/10 + 1, cy + size/3 + 1, cx + size/3 + 1, cy - size/3 + 1);
        
        // Foreground
        g2.setColor(new Color(0, 255, 0));
        g2.drawLine(cx - size/3, cy, cx - size/10, cy + size/3);
        g2.drawLine(cx - size/10, cy + size/3, cx + size/3, cy - size/3);
    }

    // Consolidated standard vector cross (Drawn with identical 4.0f stroke and shadow)
    private void drawCross(Graphics2D g2, int cx, int cy, int size) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(4.0f));
        
        // Shadow
        g2.setColor(new Color(0, 0, 0, 180));
        g2.drawLine(cx - size/3 + 1, cy - size/3 + 1, cx + size/3 + 1, cy + size/3 + 1);
        g2.drawLine(cx + size/3 + 1, cy - size/3 + 1, cx - size/3 + 1, cy + size/3 + 1);
        
        // Foreground
        g2.setColor(new Color(255, 50, 50));
        g2.drawLine(cx - size/3, cy - size/3, cx + size/3, cy + size/3);
        g2.drawLine(cx + size/3, cy - size/3, cx - size/3, cy + size/3);
    }

    private void drawVisualizerOverlays(Graphics2D g2) {
        if (!visualizerMode || historyIndex < 0 || historyIndex >= vizHistory.size()) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        VizState state = vizHistory.get(historyIndex);

        int w = boardPanel.getWidth() / 8;
        int h = boardPanel.getHeight() / 8;

        // LAYER 1: Draw all segmented check arrows and bitboard shift arrows
        g2.setStroke(new BasicStroke(4.0f)); 
        for (int[] arrow : state.arrows) {
            int startR = arrow[0];
            int startC = arrow[1];
            int endR = arrow[2];
            int endC = arrow[3];
            int colorType = arrow.length > 4 ? arrow[4] : 1; 

            int x1 = startC * w + w / 2;
            int y1 = startR * h + h / 2;
            int x2 = endC * w + w / 2;
            int y2 = endR * h + h / 2;

            if (colorType == 2) {
                g2.setColor(new Color(255, 50, 50));  // Red
            } else if (colorType == 3) {
                g2.setColor(new Color(0, 255, 0));    // Green
            } else {
                g2.setColor(new Color(255, 235, 0));   // Yellow
            }

            drawArrowLine(g2, x1, y1, x2, y2, 18, 9); 
        }

        // Draw offscreen wall hit vectors (Typo fixed)
        for (int[] wallHit : state.wallHits) {
            int startR = wallHit[0];
            int startC = wallHit[1];
            int edgeR = wallHit[2];
            int edgeC = wallHit[3];
            int offR = wallHit[4];
            int offC = wallHit[5];

            int xs = startC * w + w / 2;
            int ys = startR * h + h / 2;
            int x1 = edgeC * w + w / 2; // Corrected
            int y1 = edgeR * h + h / 2; // Corrected
            int x2 = offC * w + w / 2;  // Corrected
            int y2 = offR * h + h / 2;  // Corrected

            int edgeX = x1 + (x2 - x1) / 2;
            int edgeY = y1 + (y2 - y1) / 2;

            g2.setColor(new Color(255, 50, 50)); // Red
            drawArrowLine(g2, xs, ys, edgeX, edgeY, 18, 9);
            drawCross(g2, edgeX, edgeY, 16); 
        }

        // LAYER 2: Draw all vector outcome markers
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String marker = state.markers[r][c];
                if (marker != null) {
                    int cx = c * w + w / 2;
                    int cy = r * h + h / 2;
                    
                    if (marker.equals("✓")) {
                        int checkY = cy + 13;
                        drawCheckmark(g2, cx, checkY, 24); 
                    } else if (marker.equals("X")) {
                        int crossY = cy + 13;
                        drawCross(g2, cx, crossY, 20); 
                    } else if (marker.equals("?")) {
                        int qY = cy + 13;
                        g2.setFont(new Font("Arial", Font.BOLD, 22));
                        g2.setColor(Color.BLACK); 
                        g2.drawString("?", cx - 6 + 1, qY + 1); // Shadow
                        g2.setColor(Color.WHITE); 
                        g2.drawString("?", cx - 6, qY); // Foreground
                    } else if (marker.startsWith("Skip")) {
                        int axis = Integer.parseInt(marker.substring(4));
                        drawSkipDoubleArrow(g2, cx, cy, axis); // Draw vector double-arrow
                    } else {
                        // Render numerical sequence markers inside cell center
                        g2.setFont(new Font("Arial", Font.BOLD, 18));
                        g2.setColor(Color.BLACK); 
                        g2.drawString(marker, cx - 5 + 1, cy + 7 + 1); // Shadow
                        g2.setColor(new Color(255, 235, 0)); 
                        g2.drawString(marker, cx - 5, cy + 7); // Foreground
                    }
                }
            }
        }

        // LAYER 3: Draw visualizer text labels
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String text = state.tileTexts[r][c];
                boolean isPersistentValid = state.persistentValids[r][c];
                boolean isPersistentInvalid = state.persistentInvalids[r][c];

                if (text == null) text = "";

                if (!text.isEmpty()) {
                    drawCenteredMultiLineString(g2, text, w, h, r, c, state.highlights[r][c]);
                } else if (isPersistentValid) {
                    drawCenteredMultiLineString(g2, "Valid", w, h, r, c, 3);
                } else if (isPersistentInvalid) {
                    drawCenteredMultiLineString(g2, "Invalid", w, h, r, c, 4);
                }
            }
        }
    }

    private void drawSkipDoubleArrow(Graphics2D g2, int cx, int cy, int axis) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Center horizontally (cx) and shift down slightly (cy + 10) to clear the top-aligned "Skip" text
        int arrowCx = cx;
        int arrowCy = cy + 10;

        if (axis == 0) { // Horizontal
            arrowCx += 2;
            arrowCy -= 3;
        } else if (axis == 1) { // Vertical
            arrowCx += 3;
        } else { // Diagonal NW-SE (2) and NE-SW (3)
            arrowCx += 3; // Move right slightly
            arrowCy -= 3; // Move up slightly
        }

        int len = 12; // Good size
        g2.setStroke(new BasicStroke(3.0f));
        
        // Render foreground double-sided arrow only (Shadow removed)
        drawSingleAxisDoubleArrow(g2, arrowCx, arrowCy, len, axis, new Color(160, 32, 240)); // Purple
    }

    private void drawDoubleEndedArrow(Graphics2D g, int x1, int y1, int x2, int y2, int d, int h) {
        int dx = x2 - x1, dy = y2 - y1;
        double D = Math.sqrt(dx * dx + dy * dy);
        if (D < 1) return;
        
        double sin = dy / D, cos = dx / D;
        
        // Base of the arrowhead at (x1, y1) pointing towards (x2, y2)
        // Pulled back by d units to hide the line completely under the arrowhead polygon
        double x1_base = x1 + d * cos;
        double y1_base = y1 + d * sin;
        
        // Base of the arrowhead at (x2, y2) pointing towards (x1, y1)
        double x2_base = x2 - d * cos;
        double y2_base = y2 - d * sin;
        
        // Draw the line segment strictly between the arrowhead bases (zero gaps, zero overlaps)
        g.drawLine((int) x1_base, (int) y1_base, (int) x2_base, (int) y2_base);
        
        // Draw arrowhead 1 (at x1, y1 pointing out)
        drawSingleArrowHead(g, x2, y2, x1, y1, d, h);
        
        // Draw arrowhead 2 (at x2, y2 pointing out)
        drawSingleArrowHead(g, x1, y1, x2, y2, d, h);
    }

    private void drawSingleAxisDoubleArrow(Graphics2D g2, int cx, int cy, int len, int axis, Color color) {
        g2.setColor(color);
        int d = 8; // Arrowhead length
        int h = 5; // Arrowhead half-width

        if (axis == 0) { // Horizontal ↔
            int x1 = cx - len;
            int x2 = cx + len;
            g2.drawLine(x1 + d - 1, cy, x2 - d + 1, cy); // Pulled line back slightly to hide ends
            
            // Left arrowhead pointing West
            int[] xp1 = {x1, x1 + d, x1 + d};
            int[] yp1 = {cy, cy - h, cy + h};
            g2.fillPolygon(xp1, yp1, 3);
            
            // Right arrowhead pointing East
            int[] xp2 = {x2, x2 - d, x2 - d};
            int[] yp2 = {cy, cy - h, cy + h};
            g2.fillPolygon(xp2, yp2, 3);
            
        } else if (axis == 1) { // Vertical ↕
            int y1 = cy - len;
            int y2 = cy + len;
            g2.drawLine(cx, y1 + d - 1, cx, y2 - d + 1);
            
            // Top arrowhead pointing North
            int[] xp1 = {cx, cx - h, cx + h};
            int[] yp1 = {y1, y1 + d, y1 + d};
            g2.fillPolygon(xp1, yp1, 3);
            
            // Bottom arrowhead pointing South
            int[] xp2 = {cx, cx - h, cx + h};
            int[] yp2 = {y2, y2 - d, y2 - d};
            g2.fillPolygon(xp2, yp2, 3);
            
        } else if (axis == 2) { // NW-SE ⤡
            int offset = (int)(len * 0.707);
            int x1 = cx - offset;
            int y1 = cy - offset;
            int x2 = cx + offset;
            int y2 = cy + offset;
            
            int od = (int)(d * 0.707);
            int oh = (int)(h * 0.707);
            
            // Draw line between bases
            g2.drawLine(x1 + od - 1, y1 + od - 1, x2 - od + 1, y2 - od + 1);
            
            // Top-left arrowhead pointing NW (Tip at x1, y1)
            int[] xp1 = {x1, x1 + od - oh, x1 + od + oh};
            int[] yp1 = {y1, y1 + od + oh, y1 + od - oh};
            g2.fillPolygon(xp1, yp1, 3);
            
            // Bottom-right arrowhead pointing SE (Tip at x2, y2)
            int[] xp2 = {x2, x2 - od - oh, x2 - od + oh};
            int[] yp2 = {y2, y2 - od + oh, y2 - od - oh};
            g2.fillPolygon(xp2, yp2, 3);
            
        } else if (axis == 3) { // NE-SW ⤢
            int offset = (int)(len * 0.707);
            int x1 = cx + offset;
            int y1 = cy - offset;
            int x2 = cx - offset;
            int y2 = cy + offset;
            
            int od = (int)(d * 0.707);
            int oh = (int)(h * 0.707);
            
            // Draw line between bases
            g2.drawLine(x1 - od + 1, y1 + od - 1, x2 + od - 1, y2 - od + 1);
            
            // Top-right arrowhead pointing NE (Tip at x1, y1)
            int[] xp1 = {x1, x1 - od - oh, x1 - od + oh};
            int[] yp1 = {y1, y1 + od - oh, y1 + od + oh};
            g2.fillPolygon(xp1, yp1, 3);
            
            // Bottom-left arrowhead pointing SW (Tip at x2, y2)
            int[] xp2 = {x2, x2 + od - oh, x2 + od + oh};
            int[] yp2 = {y2, y2 - od - oh, y2 - od + oh};
            g2.fillPolygon(xp2, yp2, 3);
        }
    }

    private void drawArrowHeads(Graphics2D g, int x1, int y1, int x2, int y2, int d, int h) {
        drawSingleArrowHead(g, x2, y2, x1, y1, d, h); // Tip 1
        drawSingleArrowHead(g, x1, y1, x2, y2, d, h); // Tip 2
    }

    private void drawSingleArrowHead(Graphics2D g, int x1, int y1, int x2, int y2, int d, int h) {
        int dx = x2 - x1, dy = y2 - y1;
        double D = Math.sqrt(dx * dx + dy * dy);
        if (D < 1) return;
        double xm = D - d;
        double sin = dy / D, cos = dx / D;
        double ym = h, yn = -h;
        double x_m_rot = xm * cos - ym * sin + x1;
        double y_m_rot = xm * sin + ym * cos + y1;
        double x_n_rot = xm * cos - yn * sin + x1;
        double y_n_rot = xm * sin + yn * cos + y1;
        int[] xpoints = {x2, (int) x_m_rot, (int) x_n_rot};
        int[] ypoints = {y2, (int) y_m_rot, (int) y_n_rot};
        g.fillPolygon(xpoints, ypoints, 3);
    }

    // Mathematical standard arrowhead generator (Pulls line ends back slightly to look pristine on grid)
    private void drawArrowLine(Graphics2D g, int x1, int y1, int x2, int y2, int d, int h) {
        int dx = x2 - x1, dy = y2 - y1;
        double D = Math.sqrt(dx * dx + dy * dy);
        if (D < 1) return;
        
        int pad = 5;
        double x1_adj = x1 + (dx / D) * pad;
        double y1_adj = y1 + (dy / D) * pad;
        double x2_adj = x2 - (dx / D) * pad;
        double y2_adj = y2 - (dy / D) * pad;
        
        int x1_i = (int) x1_adj;
        int x2_i = (int) x2_adj;
        int y1_i = (int) y1_adj;
        int y2_i = (int) y2_adj;

        int dx_adj = x2_i - x1_i;
        int dy_adj = y2_i - y1_i;
        double D_adj = Math.sqrt(dx_adj * dx_adj + dy_adj * dy_adj);

        double xm = D_adj - d; // Base of the arrowhead
        double sin = dy_adj / D_adj, cos = dx_adj / D_adj;

        // Calculate the exact base point where the line meets the arrowhead
        int x_base = (int) (xm * cos + x1_i);
        int y_base = (int) (xm * sin + y1_i);

        // Draw line strictly to the base of the arrowhead so the square line cap is hidden
        g.drawLine(x1_i, y1_i, x_base, y_base);

        // Draw solid arrowhead polygon for a perfect, sharp point
        double ym = h, yn = -h;
        double x_m_rot = xm * cos - ym * sin + x1_i;
        double y_m_rot = xm * sin + ym * cos + y1_i;
        double x_n_rot = xm * cos - yn * sin + x1_i;
        double y_n_rot = xm * sin + yn * cos + y1_i;

        int[] xpoints = {x2_i, (int) x_m_rot, (int) x_n_rot};
        int[] ypoints = {y2_i, (int) y_m_rot, (int) y_n_rot};

        g.fillPolygon(xpoints, ypoints, 3);
    }

    private void drawCenteredMultiLineString(Graphics2D g2, String text, int cellWidth, int cellHeight, int row, int col, int highlight) {
        if (text.isEmpty()) return;

        int fontSize = 9;
        if (text.equals("Valid") || text.equals("Invalid") || text.equals("Evaluating") || text.equals("End of board") || text.equals("Edge of board") || text.equals("Empty") || text.equals("Own Piece") || text.equals("Skip")) {
            fontSize = 14; 
        }

        g2.setFont(new Font("Arial", Font.BOLD, fontSize));
        FontMetrics fm = g2.getFontMetrics();
        String[] lines = text.split("\n");
        int lineHeight = fm.getHeight() - 2; 
        int totalHeight = lineHeight * lines.length;
        
        int cellX = col * cellWidth;
        int cellY = row * cellHeight;
        
        // Shift text upwards by 13 pixels as default
        int startY = cellY + ((cellHeight - totalHeight) / 2) + fm.getAscent() - 13;
        
        if (text.equals("Skip")) {
            startY += 3; // Shifted up slightly from the previous value (+8)
        }

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int startX = cellX + (cellWidth - fm.stringWidth(line)) / 2;
            
            if (text.equals("Skip")) {
                startX += 3; // Move text slightly to the right
            }
            
            int currentY = startY + i * lineHeight;
            
            // Draw high-contrast drop shadow
            g2.setColor(new Color(0, 0, 0, 220));
            g2.drawString(line, startX + 1, currentY + 1);
            
            // Draw foreground text
            if (text.equals("Valid")) {
                g2.setColor(new Color(0, 255, 0)); // Bright Green
            } else if (text.equals("Own Piece") && highlight == 3) {
                g2.setColor(new Color(0, 255, 0)); // Bright Green on successful anchor
            } else if (text.equals("Own Piece") && highlight == 1) {
                g2.setColor(new Color(255, 215, 0)); // Yellow on active scan step
            } else if (text.equals("Own Piece") && highlight == 2) {
                g2.setColor(new Color(255, 215, 0)); // Yellow
            } else if (text.equals("Empty") && highlight == 2) {
                g2.setColor(new Color(255, 215, 0)); // Yellow
            } else if (text.equals("Empty") && highlight == 1) {
                g2.setColor(new Color(255, 215, 0)); // Yellow
            } else if (text.equals("Skip")) {
                g2.setColor(new Color(160, 32, 240)); // Purple text
            } else if (text.equals("Invalid") || text.equals("End of board") || text.equals("Edge of board") || text.equals("Empty") || text.equals("Own Piece")) {
                g2.setColor(new Color(255, 50, 50)); // Bright Red
            } else if (text.equals("Evaluating")) {
                g2.setColor(Color.WHITE); // Crisp White
            } else {
                g2.setColor(Color.WHITE);
            }
            g2.drawString(line, startX, currentY);
        }
    }

    private void applyEndpointPreviews(int[][] curHighlights, String[][] texts, String[][] markers,
                                       boolean termPlusOnBoard, int termPlusR, int termPlusC,
                                       boolean termMinusOnBoard, int termMinusR, int termMinusC,
                                       boolean[][] persistentValids) {
        if (termPlusOnBoard) {
            int type = game.getPieceAt(termPlusR, termPlusC);
            if (type == EMPTY && !persistentValids[termPlusR][termPlusC]) {
                curHighlights[termPlusR][termPlusC] = 1; // Turquoise
                texts[termPlusR][termPlusC] = "Evaluating";
                markers[termPlusR][termPlusC] = "?";
            } else if (type == currentPlayer && !persistentValids[termPlusR][termPlusC]) {
                curHighlights[termPlusR][termPlusC] = 1; // Turquoise
                texts[termPlusR][termPlusC] = "Own Piece";
                markers[termPlusR][termPlusC] = null;
            }
        }
        if (termMinusOnBoard) {
            int type = game.getPieceAt(termMinusR, termMinusC);
            if (type == EMPTY && !persistentValids[termMinusR][termMinusC]) {
                curHighlights[termMinusR][termMinusC] = 1; // Turquoise
                texts[termMinusR][termMinusC] = "Evaluating";
                markers[termMinusR][termMinusC] = "?";
            } else if (type == currentPlayer && !persistentValids[termMinusR][termMinusC]) {
                curHighlights[termMinusR][termMinusC] = 1; // Turquoise
                texts[termMinusR][termMinusC] = "Own Piece";
                markers[termMinusR][termMinusC] = null;
            }
        }
    }

    private void copyStringArray(String[][] source, String[][] dest) {
        for (int i = 0; i < 8; i++) {
            System.arraycopy(source[i], 0, dest[i], 0, 8);
        }
    }

    private void applyEndpointHighlights(int[][] curHighlights, 
                                         boolean termPlusOnBoard, int termPlusR, int termPlusC,
                                         boolean termMinusOnBoard, int termMinusR, int termMinusC,
                                         boolean[][] persistentValids) {
        if (termPlusOnBoard) {
            int type = game.getPieceAt(termPlusR, termPlusC);
            if ((type == EMPTY || type == currentPlayer) && !persistentValids[termPlusR][termPlusC]) {
                curHighlights[termPlusR][termPlusC] = 1; // Turquoise
            }
        }
        if (termMinusOnBoard) {
            int type = game.getPieceAt(termMinusR, termMinusC);
            if ((type == EMPTY || type == currentPlayer) && !persistentValids[termMinusR][termMinusC]) {
                curHighlights[termMinusR][termMinusC] = 1; // Turquoise
            }
        }
    }

    private void applyEndpointTextsAndMarkers(String[][] texts, String[][] markers,
                                              boolean termPlusOnBoard, int termPlusR, int termPlusC, boolean showPlusText,
                                              boolean termMinusOnBoard, int termMinusR, int termMinusC, boolean showMinusText,
                                              boolean[][] persistentValids) {
        if (termPlusOnBoard) {
            int type = game.getPieceAt(termPlusR, termPlusC);
            if (!persistentValids[termPlusR][termPlusC]) {
                if (type == EMPTY) {
                    texts[termPlusR][termPlusC] = "Evaluating";
                    markers[termPlusR][termPlusC] = "?";
                } else if (type == currentPlayer && showPlusText) {
                    texts[termPlusR][termPlusC] = "Own Piece";
                    markers[termPlusR][termPlusC] = null;
                }
            }
        }
        if (termMinusOnBoard) {
            int type = game.getPieceAt(termMinusR, termMinusC);
            if (!persistentValids[termMinusR][termMinusC]) {
                if (type == EMPTY) {
                    texts[termMinusR][termMinusC] = "Evaluating";
                    markers[termMinusR][termMinusC] = "?";
                } else if (type == currentPlayer && showMinusText) {
                    texts[termMinusR][termMinusC] = "Own Piece";
                    markers[termMinusR][termMinusC] = null;
                }
            }
        }
    }

    private void applySpecificEndpointPreview(int[][] curHighlights, String[][] texts, String[][] markers,
                                               boolean termOnBoard, int termR, int termC, boolean[][] persistentValids) {
        if (termOnBoard) {
            int type = game.getPieceAt(termR, termC);
            if (type == EMPTY && !persistentValids[termR][termC]) {
                curHighlights[termR][termC] = 1; // Turquoise
                texts[termR][termC] = "Evaluating";
                markers[termR][termC] = "?";
            } else if (type == currentPlayer && !persistentValids[termR][termC]) {
                curHighlights[termR][termC] = 1; // Turquoise
                texts[termR][termC] = "Own Piece";
                markers[termR][termC] = null;
            }
        }
    }

// Frame-by-Frame OO Segment-Scanning History Generator (1D Array Optimized Engine with Step Decomposition)
    private void generateFlatArrayOptimizedHistory() {
        vizHistory.clear();
        historyIndex = -1;

        int[][] curHighlights = new int[8][8];
        boolean[][] persistentValids = new boolean[8][8];
        boolean[][] persistentInvalids = new boolean[8][8];
        
        // processed[64][4] tracks processed axes safely
        boolean[][] processed = new boolean[64][4];
        boolean[] added = new boolean[64];
        
        int opponent = (currentPlayer == BLACK) ? WHITE : BLACK;
        
        int[] drAxes = {0, 1, 1, 1};
        int[] dcAxes = {1, 0, 1, -1};
        String[] AXIS_NAMES = {"Horizontal", "Vertical", "Diagonal NW-SE", "Diagonal NE-SW"};
        String[] PLUS_DIR_NAMES = {"East", "South", "South-East", "South-West"};
        String[] MINUS_DIR_NAMES = {"West", "North", "North-West", "North-East"};
        String[] AXIS_ARROWS = {"Skip0", "Skip1", "Skip2", "Skip3"};

        boolean[][] evaluated = new boolean[8][8];

        for (int i = 0; i < 64; i++) {
            int r = i / 8;
            int c = i % 8;
            String cellName = OthelloBitboard.indexToAlgebraic(i);
            int pieceAtCell = game.getPieceAt(r, c);

            // Sweep Frame (Runs sequentially through all cells)
            clearArray(curHighlights);
            applyOOCandidateHighlights(curHighlights, new String[8][8], new String[8][8], evaluated, persistentValids, persistentInvalids, -1, -1, false);
            curHighlights[r][c] = 2; // Yellow active sweep cursor
            
            String sweepExplanation;
            if (pieceAtCell == EMPTY) {
                sweepExplanation = String.format("Scanning board... %s is empty. Skipping.", cellName);
            } else if (pieceAtCell == currentPlayer) {
                sweepExplanation = String.format("Scanning board... %s contains Own Piece. Skipping.", cellName);
            } else {
                sweepExplanation = String.format("Scanning board... Found opponent piece at %s! Halting sweep to evaluate axes.", cellName);
            }

            VizState sweepState = new VizState(-1, -1, -1, 0, persistentValids, persistentInvalids, curHighlights, sweepExplanation);
            String[][] sweepTexts = new String[8][8];
            String[][] sweepMarkers = new String[8][8];
            applyOOCandidateHighlights(curHighlights, sweepTexts, sweepMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
            curHighlights[r][c] = 2;
            sweepState.setTileTexts(sweepTexts);
            sweepState.setMarkers(sweepMarkers);
            vizHistory.add(sweepState);

            if (pieceAtCell != opponent) {
                continue;
            }

            for (int axis = 0; axis < 4; axis++) {
                String axisName = AXIS_NAMES[axis];
                int dr = drAxes[axis];
                int dc = dcAxes[axis];

                if (processed[i][axis]) {
                    // Skip Frame
                    clearArray(curHighlights);
                    applyOOCandidateHighlights(curHighlights, new String[8][8], new String[8][8], evaluated, persistentValids, persistentInvalids, -1, -1, false);
                    curHighlights[r][c] = 6; // Purple (Skipping)
                    
                    VizState skipState = new VizState(r, c, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                            String.format("Axis %s on %s has already been processed. Skipping.", axisName, cellName));
                    String[][] skipTexts = new String[8][8];
                    String[][] skipMarkers = new String[8][8];
                    applyOOCandidateHighlights(curHighlights, skipTexts, skipMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                    
                    curHighlights[r][c] = 6;
                    skipTexts[r][c] = "Skip";
                    skipMarkers[r][c] = AXIS_ARROWS[axis]; // Tag double-sided arrow routing
                    
                    skipState.setTileTexts(skipTexts);
                    skipState.setMarkers(skipMarkers);
                    vizHistory.add(skipState);
                    continue;
                }

                // Scan + Direction
                List<int[]> scanPlus = new ArrayList<>();
                int currR = r + dr;
                int currC = c + dc;
                while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && game.getPieceAt(currR, currC) == opponent) {
                    scanPlus.add(new int[]{currR, currC});
                    currR += dr;
                    currC += dc;
                }
                int termPlusR = currR;
                int termPlusC = currC;
                boolean termPlusOnBoard = (termPlusR >= 0 && termPlusR < 8 && termPlusC >= 0 && termPlusC < 8);

                // Scan - Direction
                List<int[]> scanMinus = new ArrayList<>();
                currR = r - dr;
                currC = c - dc;
                while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && game.getPieceAt(currR, currC) == opponent) {
                    scanMinus.add(new int[]{currR, currC});
                    currR -= dr;
                    currC -= dc;
                }
                int termMinusR = currR;
                int termMinusC = currC;
                boolean termMinusOnBoard = (termMinusR >= 0 && termMinusR < 8 && termMinusC >= 0 && termMinusC < 8);

                // --- SUB-STEP 1: Sequential Discovery Scan in '+' Direction ---
                List<int[]> activePlus = new ArrayList<>();
                int plusPieceCount = 1;
                currR = r + dr;
                currC = c + dc;

                while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && game.getPieceAt(currR, currC) == opponent) {
                    activePlus.add(new int[]{currR, currC});
                    
                    clearArray(curHighlights);
                    String[][] step1Texts = new String[8][8];
                    String[][] step1Markers = new String[8][8];
                    applyOOCandidateHighlights(curHighlights, step1Texts, step1Markers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                    
                    curHighlights[r][c] = 5; // Highlight active root white piece in Orange
                    step1Markers[r][c] = "1"; // Current piece is always labeled '1'

                    // Apply active candidate previews in Turquoise (Value 1)
                    applyEndpointHighlights(curHighlights, 
                                            termPlusOnBoard, termPlusR, termPlusC, 
                                            termMinusOnBoard, termMinusR, termMinusC, 
                                            persistentValids);

                    // Highlight discovered opponents in Yellow and label sequentially
                    int labelIndex = 2;
                    for (int[] p : activePlus) {
                        curHighlights[p[0]][p[1]] = 2; // Yellow
                        step1Markers[p[0]][p[1]] = String.valueOf(labelIndex++);
                    }

                    // Draw yellow scan arrows extending outward
                    List<int[]> step1Arrows = new ArrayList<>();
                    int pr = r;
                    int pc = c;
                    for (int[] p : activePlus) {
                        step1Arrows.add(new int[]{pr, pc, p[0], p[1], 1});
                        pr = p[0];
                        pc = p[1];
                    }

                    VizState step1State = new VizState(r, c, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                            String.format("Axis %s: Tracing in [+] direction (towards %s). Opponent piece %d found.", 
                                    axisName, PLUS_DIR_NAMES[axis], plusPieceCount));
                    step1State.arrows = step1Arrows;
                    step1State.setTileTexts(step1Texts);
                    step1State.setMarkers(step1Markers);
                    
                    // Texts/markers are guarded: plusText remains hidden (false) during scan
                    applyEndpointTextsAndMarkers(step1State.tileTexts, step1State.markers,
                                                 termPlusOnBoard, termPlusR, termPlusC, false,
                                                 termMinusOnBoard, termMinusR, termMinusC, false,
                                                 persistentValids);
                    vizHistory.add(step1State);

                    currR += dr;
                    currC += dc;
                    plusPieceCount++;
                }
                
                // Add final frame for '+' terminal endpoint discovery (Scoping collision fixed)
                clearArray(curHighlights);
                String[][] termPlusTexts = new String[8][8];
                String[][] termPlusMarkers = new String[8][8];
                applyOOCandidateHighlights(curHighlights, termPlusTexts, termPlusMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                
                curHighlights[r][c] = 5; // Orange
                termPlusMarkers[r][c] = "1";
                
                applyEndpointHighlights(curHighlights, 
                                        termPlusOnBoard, termPlusR, termPlusC, 
                                        termMinusOnBoard, termMinusR, termMinusC, 
                                        persistentValids);

                int labelIdx = 2;
                for (int[] p : scanPlus) {
                    curHighlights[p[0]][p[1]] = 2; // Yellow
                    termPlusMarkers[p[0]][p[1]] = String.valueOf(labelIdx++);
                }
                
                List<int[]> termPlusArrows = new ArrayList<>();
                int pr = r;
                int pc = c;
                for (int[] p : scanPlus) {
                    termPlusArrows.add(new int[]{pr, pc, p[0], p[1], 1});
                    pr = p[0];
                    pc = p[1];
                }
                
                List<int[]> termPlusWallHits = new ArrayList<>();
                VizState termPlusState; // Scope declared cleanly
                
                if (termPlusOnBoard) {
                    termPlusArrows.add(new int[]{pr, pc, termPlusR, termPlusC, 1}); // Yellow arrow to terminal
                    
                    int targetType = game.getPieceAt(termPlusR, termPlusC);
                    if (targetType == currentPlayer && !persistentValids[termPlusR][termPlusC]) {
                        // Scan encounters Own Piece: Handled by applyEndpointPreviews safely
                    }
                    
                    termPlusState = new VizState(r, c, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                            String.format("Axis %s: Terminal endpoint in [+] direction is located at %s.", 
                                    axisName, OthelloBitboard.indexToAlgebraic(termPlusR * 8 + termPlusC)));
                } else {
                    termPlusState = new VizState(r, c, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                            String.format("Axis %s: Scan hits boundary edge in [+] direction.", axisName));
                            
                    // Fallback to active orange root cell (r, c) on immediate wall hits
                    int boundaryR = scanPlus.isEmpty() ? r : scanPlus.get(scanPlus.size() - 1)[0];
                    int boundaryC = scanPlus.isEmpty() ? c : scanPlus.get(scanPlus.size() - 1)[1];
                    termPlusWallHits.add(new int[]{boundaryR, boundaryC, boundaryR, boundaryC, termPlusR, termPlusC});
                    termPlusTexts[boundaryR][boundaryC] = "End of board";
                }
                
                termPlusState.arrows = termPlusArrows;
                termPlusState.wallHits = termPlusWallHits;
                termPlusState.setTileTexts(termPlusTexts);
                termPlusState.setMarkers(termPlusMarkers);
                
                // Scan reaches '+' terminal: reveal plusText (true) while keeping minusText hidden (false)
                applyEndpointTextsAndMarkers(termPlusState.tileTexts, termPlusState.markers,
                                             termPlusOnBoard, termPlusR, termPlusC, true,
                                             termMinusOnBoard, termMinusR, termMinusC, false,
                                             persistentValids);
                vizHistory.add(termPlusState);


                // --- SUB-STEP 2: Sequential Discovery Scan in '-' Direction ---
                List<int[]> activeMinus = new ArrayList<>();
                int minusPieceCount = 1;
                currR = r - dr;
                currC = c - dc;
                
                while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && game.getPieceAt(currR, currC) == opponent) {
                    activeMinus.add(new int[]{currR, currC});
                    
                    clearArray(curHighlights);
                    String[][] step2Texts = new String[8][8];
                    String[][] step2Markers = new String[8][8];
                    applyOOCandidateHighlights(curHighlights, step2Texts, step2Markers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                    
                    curHighlights[r][c] = 5; // Orange
                    step2Markers[r][c] = "1";

                    applyEndpointHighlights(curHighlights, 
                                            termPlusOnBoard, termPlusR, termPlusC, 
                                            termMinusOnBoard, termMinusR, termMinusC, 
                                            persistentValids);

                    // Continue sequence labels from step 1
                    int sequentialLabel = 2;
                    for (int[] p : scanPlus) {
                        curHighlights[p[0]][p[1]] = 2;
                        step2Markers[p[0]][p[1]] = String.valueOf(sequentialLabel++);
                    }
                    for (int[] p : activeMinus) {
                        curHighlights[p[0]][p[1]] = 2;
                        step2Markers[p[0]][p[1]] = String.valueOf(sequentialLabel++);
                    }

                    List<int[]> step2Arrows = new ArrayList<>();
                    // + arrows
                    int ar = r;
                    int ac = c;
                    for (int[] p : scanPlus) {
                        step2Arrows.add(new int[]{ar, ac, p[0], p[1], 1});
                        ar = p[0];
                        ac = p[1];
                    }
                    if (termPlusOnBoard) {
                        step2Arrows.add(new int[]{ar, ac, termPlusR, termPlusC, 1});
                    }
                    // - arrows
                    ar = r;
                    ac = c;
                    for (int[] p : activeMinus) {
                        step2Arrows.add(new int[]{ar, ac, p[0], p[1], 1});
                        ar = p[0];
                        ac = p[1];
                    }

                    VizState step2State = new VizState(r, c, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                            String.format("Axis %s: Tracing in [-] direction (towards %s). Opponent piece %d found.", 
                                    axisName, MINUS_DIR_NAMES[axis], minusPieceCount));
                    step2State.arrows = step2Arrows;
                    step2State.setTileTexts(step2Texts);
                    step2State.setMarkers(step2Markers);
                    
                    // Keep plusText visible (true) while keeping minusText hidden (false) during scan
                    applyEndpointTextsAndMarkers(step2State.tileTexts, step2State.markers,
                                                 termPlusOnBoard, termPlusR, termPlusC, true,
                                                 termMinusOnBoard, termMinusR, termMinusC, false,
                                                 persistentValids);
                    vizHistory.add(step2State);

                    currR -= dr;
                    currC -= dc;
                    minusPieceCount++;
                }
                
                // Add final frame for '-' terminal endpoint discovery (Removed duplicate type definitions)
                termMinusR = currR;
                termMinusC = currC;
                termMinusOnBoard = (termMinusR >= 0 && termMinusR < 8 && termMinusC >= 0 && termMinusC < 8);
                
                clearArray(curHighlights);
                String[][] termMinusTexts = new String[8][8];
                String[][] termMinusMarkers = new String[8][8];
                applyOOCandidateHighlights(curHighlights, termMinusTexts, termMinusMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                
                curHighlights[r][c] = 5; // Orange
                termMinusMarkers[r][c] = "1";

                applyEndpointHighlights(curHighlights, 
                                        termPlusOnBoard, termPlusR, termPlusC, 
                                        termMinusOnBoard, termMinusR, termMinusC, 
                                        persistentValids);

                int seqLabel = 2;
                for (int[] p : scanPlus) {
                    curHighlights[p[0]][p[1]] = 2;
                    termMinusMarkers[p[0]][p[1]] = String.valueOf(seqLabel++);
                }
                for (int[] p : scanMinus) {
                    curHighlights[p[0]][p[1]] = 2;
                    termMinusMarkers[p[0]][p[1]] = String.valueOf(seqLabel++);
                }
                
                List<int[]> termMinusArrows = new ArrayList<>();
                // + arrows
                int ar = r;
                int ac = c;
                for (int[] p : scanPlus) {
                    termMinusArrows.add(new int[]{ar, ac, p[0], p[1], 1});
                    ar = p[0];
                    ac = p[1];
                }
                if (termPlusOnBoard) {
                    termMinusArrows.add(new int[]{ar, ac, termPlusR, termPlusC, 1});
                }
                // - arrows
                ar = r;
                ac = c;
                for (int[] p : scanMinus) {
                    termMinusArrows.add(new int[]{ar, ac, p[0], p[1], 1});
                    ar = p[0];
                    ac = p[1];
                }
                
                List<int[]> termMinusWallHits = new ArrayList<>();
                VizState termMinusState; // Scope declared cleanly
                
                if (termMinusOnBoard) {
                    termMinusArrows.add(new int[]{ar, ac, termMinusR, termMinusC, 1});
                    
                    termMinusState = new VizState(r, c, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                            String.format("Axis %s: Other terminal endpoint in [-] direction is located at %s.", 
                                    axisName, OthelloBitboard.indexToAlgebraic(termMinusR * 8 + termMinusC)));
                } else {
                    termMinusState = new VizState(r, c, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                            String.format("Axis %s: Scan hits boundary edge in [-] direction.", axisName));
                            
                    int boundaryR = scanMinus.isEmpty() ? r : scanMinus.get(scanMinus.size() - 1)[0];
                    int boundaryC = scanMinus.isEmpty() ? c : scanMinus.get(scanMinus.size() - 1)[1];
                    termMinusWallHits.add(new int[]{boundaryR, boundaryC, boundaryR, boundaryC, termMinusR, termMinusC});
                    termMinusTexts[boundaryR][boundaryC] = "End of board";
                }
                
                termMinusState.arrows = termMinusArrows;
                termMinusState.wallHits = termMinusWallHits;
                termMinusState.setTileTexts(termMinusTexts);
                termMinusState.setMarkers(termMinusMarkers);
                
                // Scan reaches '-' terminal: reveal both plusText (true) and minusText (true)
                applyEndpointTextsAndMarkers(termMinusState.tileTexts, termMinusState.markers,
                                             termPlusOnBoard, termPlusR, termPlusC, true,
                                             termMinusOnBoard, termMinusR, termMinusC, true,
                                             persistentValids);
                vizHistory.add(termMinusState);


                // --- SUB-STEP 3: Evaluate Terminals ---
                processed[i][axis] = true;
                for (int[] p : scanPlus) processed[p[0] * 8 + p[1]][axis] = true;
                for (int[] p : scanMinus) processed[p[0] * 8 + p[1]][axis] = true;

                if (termPlusOnBoard && termMinusOnBoard) {
                    int plusIdx = termPlusR * 8 + termPlusC;
                    int minusIdx = termMinusR * 8 + termMinusC;

                    boolean match1 = (game.getPieceAt(termPlusR, termPlusC) == EMPTY && game.getPieceAt(termMinusR, termMinusC) == currentPlayer);
                    boolean match2 = (game.getPieceAt(termMinusR, termMinusC) == EMPTY && game.getPieceAt(termPlusR, termPlusC) == currentPlayer);

                    if (match1 || match2) {
                        // Valid segment found!
                        clearArray(curHighlights);
                        String[][] successTexts = new String[8][8];
                        String[][] successMarkers = new String[8][8];
                        applyOOCandidateHighlights(curHighlights, successTexts, successMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                        
                        List<int[]> successArrows = new ArrayList<>();

                        if (match1) {
                            evaluated[termPlusR][termPlusC] = true;
                            persistentValids[termPlusR][termPlusC] = true;
                            added[plusIdx] = true;
                            
                            curHighlights[termPlusR][termPlusC] = 3; // Green Valid
                            curHighlights[termMinusR][termMinusC] = 3; // Green Friendly Anchor
                            for (int[] p : scanPlus) curHighlights[p[0]][p[1]] = 3;
                            for (int[] p : scanMinus) curHighlights[p[0]][p[1]] = 3;
                            curHighlights[r][c] = 3;

                            // Arrows originate at EMPTY space pointing towards Anchor own piece
                            int tempR = termPlusR;
                            int tempC = termPlusC;
                            while (tempR != termMinusR || tempC != termMinusC) {
                                successArrows.add(new int[]{tempR, tempC, tempR - dr, tempC - dc, 3}); // Green arrow
                                tempR -= dr;
                                tempC -= dc;
                            }

                            successTexts[termPlusR][termPlusC] = "Valid";
                            successMarkers[termPlusR][termPlusC] = "✓";
                            
                            // Own pieces are colored Green with no error cross
                            successTexts[termMinusR][termMinusC] = "Own Piece";
                            successMarkers[termMinusR][termMinusC] = null;
                        } else {
                            evaluated[termMinusR][termMinusC] = true;
                            persistentValids[termMinusR][termMinusC] = true;
                            added[minusIdx] = true;

                            curHighlights[termMinusR][termMinusC] = 3; // Green Valid
                            curHighlights[termPlusR][termPlusC] = 3; // Green Friendly Anchor
                            for (int[] p : scanPlus) curHighlights[p[0]][p[1]] = 3;
                            for (int[] p : scanMinus) curHighlights[p[0]][p[1]] = 3;
                            curHighlights[r][c] = 3;

                            // Arrows originate at EMPTY space pointing towards Anchor own piece
                            int tempR = termMinusR;
                            int tempC = termMinusC;
                            while (tempR != termPlusR || tempC != termPlusC) {
                                successArrows.add(new int[]{tempR, tempC, tempR + dr, tempC + dc, 3}); // Green arrow
                                tempR += dr;
                                tempC += dc;
                            }

                            successTexts[termMinusR][termMinusC] = "Valid";
                            successMarkers[termMinusR][termMinusC] = "✓";
                            
                            successTexts[termPlusR][termPlusC] = "Own Piece";
                            successMarkers[termPlusR][termPlusC] = null;
                        }

                        VizState successState = new VizState(r, c, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                                String.format("Axis %s: Sandwiched segment found! Valid move registered.", axisName));
                        successState.arrows = successArrows;
                        successState.setTileTexts(successTexts);
                        successState.setMarkers(successMarkers);
                        vizHistory.add(successState);
                    } else {
                        // Invalid Segment
                        clearArray(curHighlights);
                        String[][] failTexts = new String[8][8];
                        String[][] failMarkers = new String[8][8];
                        applyOOCandidateHighlights(curHighlights, failTexts, failMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                        
                        List<int[]> failArrows = new ArrayList<>();

                        curHighlights[termPlusR][termPlusC] = 4; // Red
                        curHighlights[termMinusR][termMinusC] = 4; // Red
                        for (int[] p : scanPlus) curHighlights[p[0]][p[1]] = 4;
                        for (int[] p : scanMinus) curHighlights[p[0]][p[1]] = 4;
                        curHighlights[r][c] = 4;

                        // Draw red arrows in both directions from center
                        int tempR = r;
                        int tempC = c;
                        while (tempR != termPlusR || tempC != termPlusC) {
                            failArrows.add(new int[]{tempR, tempC, tempR + dr, tempC + dc, 2}); // Red arrow
                            tempR += dr;
                            tempC += dc;
                        }
                        tempR = r;
                        tempC = c;
                        while (tempR != termMinusR || tempC != termMinusC) {
                            failArrows.add(new int[]{tempR, tempC, tempR - dr, tempC - dc, 2}); // Red arrow
                            tempR -= dr;
                            tempC -= dc;
                        }

                        // Style empty failure terminals. Friendly pieces get no cross or red text.
                        int pPiece = game.getPieceAt(termPlusR, termPlusC);
                        if (pPiece == EMPTY) {
                            if (persistentValids[termPlusR][termPlusC]) {
                                failTexts[termPlusR][termPlusC] = "Valid";
                                failMarkers[termPlusR][termPlusC] = "✓";
                            } else {
                                failTexts[termPlusR][termPlusC] = "Invalid";
                                failMarkers[termPlusR][termPlusC] = "X";
                            }
                        }

                        int mPiece = game.getPieceAt(termMinusR, termMinusC);
                        if (mPiece == EMPTY) {
                            if (persistentValids[termMinusR][termMinusC]) {
                                failTexts[termMinusR][termMinusC] = "Valid";
                                failMarkers[termMinusR][termMinusC] = "✓";
                            } else {
                                failTexts[termMinusR][termMinusC] = "Invalid";
                                failMarkers[termMinusR][termMinusC] = "X";
                            }
                        }

                        VizState failState = new VizState(r, c, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                                String.format("Axis %s: Terminals fail to form a valid sandwich.", axisName));
                        failState.arrows = failArrows;
                        failState.setTileTexts(failTexts);
                        failState.setMarkers(failMarkers);
                        vizHistory.add(failState);
                    }
                } else {
                    // One of the terminals is off-board (wall hit)
                    clearArray(curHighlights);
                    String[][] failTexts = new String[8][8];
                    String[][] failMarkers = new String[8][8];
                    applyOOCandidateHighlights(curHighlights, failTexts, failMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                    
                    List<int[]> failArrows = new ArrayList<>();

                    for (int[] p : scanPlus) curHighlights[p[0]][p[1]] = 4;
                    for (int[] p : scanMinus) curHighlights[p[0]][p[1]] = 4;
                    curHighlights[r][c] = 4;

                    if (termPlusOnBoard) {
                        curHighlights[termPlusR][termPlusC] = 4;
                        int pPiece = game.getPieceAt(termPlusR, termPlusC);
                        if (pPiece == EMPTY) {
                            if (persistentValids[termPlusR][termPlusC]) {
                                failTexts[termPlusR][termPlusC] = "Valid";
                                failMarkers[termPlusR][termPlusC] = "✓";
                            } else {
                                failTexts[termPlusR][termPlusC] = "Invalid";
                                failMarkers[termPlusR][termPlusC] = "X";
                            }
                        } else if (pPiece == currentPlayer) {
                            failTexts[termPlusR][termPlusC] = "Own Piece"; // Colored Red automatically (Value 4)
                        }
                    } else {
                        // Fallback to active orange root cell (r, c) on immediate wall hits
                        int boundaryR = scanPlus.isEmpty() ? r : scanPlus.get(scanPlus.size() - 1)[0];
                        int boundaryC = scanPlus.isEmpty() ? c : scanPlus.get(scanPlus.size() - 1)[1];
                        failTexts[boundaryR][boundaryC] = "End of board"; // Fixed: Always visible on Step 3 wall failures
                    }
                    
                    if (termMinusOnBoard) {
                        curHighlights[termMinusR][termMinusC] = 4;
                        int mPiece = game.getPieceAt(termMinusR, termMinusC);
                        if (mPiece == EMPTY) {
                            if (persistentValids[termMinusR][termMinusC]) {
                                failTexts[termMinusR][termMinusC] = "Valid";
                                failMarkers[termMinusR][termMinusC] = "✓";
                            } else {
                                failTexts[termMinusR][termMinusC] = "Invalid";
                                failMarkers[termMinusR][termMinusC] = "X";
                            }
                        } else if (mPiece == currentPlayer) {
                            failTexts[termMinusR][termMinusC] = "Own Piece"; // Colored Red automatically (Value 4)
                        }
                    } else {
                        int boundaryR = scanMinus.isEmpty() ? r : scanMinus.get(scanMinus.size() - 1)[0];
                        int boundaryC = scanMinus.isEmpty() ? c : scanMinus.get(scanMinus.size() - 1)[1];
                        failTexts[boundaryR][boundaryC] = "End of board"; // Fixed: Always visible on Step 3 wall failures
                    }

                    // Trace arrows
                    int tempR = r;
                    int tempC = c;
                    while (tempR != termPlusR || tempC != termPlusC) {
                        if (tempR + dr < 0 || tempR + dr >= 8 || tempC + dc < 0 || tempC + dc >= 8) {
                            break;
                        }
                        failArrows.add(new int[]{tempR, tempC, tempR + dr, tempC + dc, 2});
                        tempR += dr;
                        tempC += dc;
                    }
                    tempR = r;
                    tempC = c;
                    while (tempR != termMinusR || tempC != termMinusC) {
                        if (tempR - dr < 0 || tempR - dr >= 8 || tempC - dc < 0 || tempC - dc >= 8) {
                            break;
                        }
                        failArrows.add(new int[]{tempR, tempC, tempR - dr, tempC - dc, 2});
                        tempR -= dr;
                        tempC -= dc;
                    }

                    // Add vector edge boundary wall red cross indicator safely declared
                    VizState failState;
                    if (!termPlusOnBoard) {
                        int boundaryR = scanPlus.isEmpty() ? r : scanPlus.get(scanPlus.size() - 1)[0];
                        int boundaryC = scanPlus.isEmpty() ? c : scanPlus.get(scanPlus.size() - 1)[1];
                        failState = new VizState(r, c, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                                String.format("Axis %s: Path failed (Wall boundary hit).", axisName));
                        failState.wallHits.add(new int[]{boundaryR, boundaryC, boundaryR, boundaryC, termPlusR, termPlusC});
                    } else if (!termMinusOnBoard) {
                        int boundaryR = scanMinus.isEmpty() ? r : scanMinus.get(scanMinus.size() - 1)[0];
                        int boundaryC = scanMinus.isEmpty() ? c : scanMinus.get(scanMinus.size() - 1)[1];
                        failState = new VizState(r, c, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                                String.format("Axis %s: Path failed (Wall boundary hit).", axisName));
                        failState.wallHits.add(new int[]{boundaryR, boundaryC, boundaryR, boundaryC, termMinusR, termMinusC});
                    } else {
                        failState = new VizState(r, c, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                                String.format("Axis %s: Path failed (Wall boundary hit).", axisName));
                    }

                    failState.arrows = failArrows;
                    failState.setTileTexts(failTexts);
                    failState.setMarkers(failMarkers);
                    vizHistory.add(failState);
                }
            }
        }

        // Final Frame: Shows all validated squares
        clearArray(curHighlights);
        String[][] finalMarkers = new String[8][8];
        String[][] finalTexts = new String[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (game.getPieceAt(r, c) == EMPTY) {
                    if (persistentValids[r][c]) {
                        curHighlights[r][c] = 3; // Green (Valid)
                        finalMarkers[r][c] = "✓";
                        finalTexts[r][c] = "Valid";
                    }
                }
            }
        }
        
        VizState finalState = new VizState(-1, -1, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                "Optimized Segment Scan complete! All legal moves are highlighted.");
        finalState.setMarkers(finalMarkers);
        finalState.setTileTexts(finalTexts);
        vizHistory.add(finalState);
    }

// Frame-by-Frame OO Scan Generator with Chronological Touch Paths & Multi-outcome checking (With Preserved Red Opponents)
    private void generateOOHistory() {
        vizHistory.clear();
        historyIndex = -1;

        int[][] curHighlights = new int[8][8];
        boolean[][] persistentValids = new boolean[8][8];
        boolean[][] persistentInvalids = new boolean[8][8];
        
        // Map out which search targets will be checked overall in OO neighbor evaluations
        boolean[][] willBeExplored = new boolean[8][8];
        int opponent = (currentPlayer == BLACK) ? WHITE : BLACK;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (game.getPieceAt(r, c) == opponent) {
                    for (int d = 0; d < 8; d++) {
                        int nr = r + DR[d];
                        int nc = c + DC[d];
                        if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                            if (game.getPieceAt(nr, nc) == EMPTY) {
                                willBeExplored[nr][nc] = true;
                            }
                        }
                    }
                }
            }
        }

        boolean[][] evaluated = new boolean[8][8];
        boolean[][] checked = new boolean[8][8]; 

        // SEQUENTIAL TRAVERSAL OUTER SWEEP
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String cellName = OthelloBitboard.indexToAlgebraic(r * 8 + c);
                int pieceAtCell = game.getPieceAt(r, c);

                // Create a brief sequential scan sweep frame
                clearArray(curHighlights);
                applyOOCandidateHighlights(curHighlights, new String[8][8], new String[8][8], evaluated, persistentValids, persistentInvalids, -1, -1, false);
                curHighlights[r][c] = 2; // Yellow active scan cursor
                
                String sweepExplanation;
                if (pieceAtCell == EMPTY) {
                    sweepExplanation = String.format("Scanning board sequentially: cell %s is empty. Skipping.", cellName);
                } else if (pieceAtCell == currentPlayer) {
                    sweepExplanation = String.format("Scanning board sequentially: cell %s contains own friendly piece. Skipping.", cellName);
                } else {
                    sweepExplanation = String.format("Scanning board sequentially: cell %s contains an OPPONENT piece! Halting sweep to check surrounding neighborhood.", cellName);
                }

                VizState sweepState = new VizState(-1, -1, -1, 0, persistentValids, persistentInvalids, curHighlights, sweepExplanation);
                String[][] sweepTexts = new String[8][8];
                String[][] sweepMarkers = new String[8][8];
                applyOOCandidateHighlights(curHighlights, sweepTexts, sweepMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                curHighlights[r][c] = 2; 
                sweepState.setTileTexts(sweepTexts);
                sweepState.setMarkers(sweepMarkers);
                vizHistory.add(sweepState);

                if (pieceAtCell != opponent) {
                    continue; 
                }

                // Check 8 neighbors of this opponent piece (Frontier search)
                for (int d = 0; d < 8; d++) {
                    int nr = r + DR[d];
                    int nc = c + DC[d];
                    String dirName = DIR_NAMES[d];

                    if (nr < 0 || nr >= 8 || nc < 0 || nc >= 8) continue;
                    
                    int neighborPiece = game.getPieceAt(nr, nc);
                    if (neighborPiece != EMPTY || checked[nr][nc]) continue;

                    checked[nr][nc] = true; // Prevents duplicate scans
                    String posName = OthelloBitboard.indexToAlgebraic(nr * 8 + nc);

                    // Track temporary empty spot failures across directional evaluations for this specific candidate square
                    boolean[][] currentSpaceInvalids = new boolean[8][8];
                    boolean[][] currentSpaceFailedOpponents = new boolean[8][8];
                    List<int[]> currentSquareArrows = new ArrayList<>();
                    String[][] currentSquareMarkers = new String[8][8];
                    String[][] currentSquareTexts = new String[8][8];

                    // Start examining this empty neighbor: Blue ("Evaluating") with "?" marker
                    clearArray(curHighlights);
                    applyOOCandidateHighlights(curHighlights, currentSquareTexts, currentSquareMarkers, evaluated, 
                                               persistentValids, persistentInvalids, nr, nc, true);

                    VizState neighborState = new VizState(nr, nc, -1, 0, persistentValids, persistentInvalids, curHighlights, "Evaluating candidate " + posName);
                    neighborState.setTileTexts(currentSquareTexts);
                    neighborState.setMarkers(currentSquareMarkers);
                    vizHistory.add(neighborState);

                    boolean cellIsIndeedValid = false;

                    for (int scanDir = 0; scanDir < 8; scanDir++) {
                        // Early Exit once confirmed Valid
                        if (cellIsIndeedValid) {
                            break; 
                        }

                        int sdr = DR[scanDir];
                        int sdc = DC[scanDir];
                        String scanDirName = DIR_NAMES[scanDir];

                        int currR = nr + sdr;
                        int currC = nc + sdc;
                        int step = 1;

                        // Check for wall boundaries or immediate adjacent empty spots before initiating the opponent search
                        if (currR < 0 || currR >= 8 || currC < 0 || currC >= 8) {
                            // Boundary wall hit immediately
                        } else {
                            int targetPiece = game.getPieceAt(currR, currC);
                            if (targetPiece == EMPTY) {
                                boolean isAlreadyValid = persistentValids[currR][currC];
                                
                                // Register transient failures first to ensure synchronous rendering
                                currentSpaceInvalids[currR][currC] = true;
                                currentSquareArrows.add(new int[]{nr, nc, currR, currC, 2}); // Red failure arrow

                                if (isAlreadyValid) {
                                    currentSquareMarkers[currR][currC] = "✓";
                                    currentSquareTexts[currR][currC] = "Valid";
                                } else {
                                    currentSquareMarkers[currR][currC] = "X";
                                    currentSquareTexts[currR][currC] = "Empty";
                                }
                                
                                clearArray(curHighlights);
                                applyOOCandidateHighlights(curHighlights, currentSquareTexts, currentSquareMarkers, evaluated, 
                                                           persistentValids, persistentInvalids, nr, nc, true);
                                
                                // Apply transient highlights, crosses, and labels (safely captures failed opponents as well)
                                applyTransientOverlay(curHighlights, currentSquareMarkers, currentSquareTexts, 
                                                      currentSpaceInvalids, currentSpaceFailedOpponents,
                                                      currentSquareMarkers, currentSquareTexts,
                                                      evaluated, persistentValids, persistentInvalids);

                                String explanationText = isAlreadyValid 
                                    ? String.format("[%s] Path failed (Adjacent cell %s is an empty valid position).", scanDirName, OthelloBitboard.indexToAlgebraic(currR * 8 + currC))
                                    : String.format("[%s] Path failed (Adjacent cell %s is empty).", scanDirName, OthelloBitboard.indexToAlgebraic(currR * 8 + currC));

                                VizState state = new VizState(nr, nc, scanDir, 1, persistentValids, persistentInvalids, curHighlights, explanationText);
                                
                                copyStringArray(currentSquareMarkers, state.markers);
                                copyStringArray(currentSquareTexts, state.tileTexts);
                                
                                // Apply crosses and text markers to our transient list for this frame
                                applyTransientOverlay(curHighlights, state.markers, state.tileTexts, 
                                                      currentSpaceInvalids, currentSpaceFailedOpponents,
                                                      currentSquareMarkers, currentSquareTexts,
                                                      evaluated, persistentValids, persistentInvalids);

                                state.tileTexts[nr][nc] = "Evaluating";
                                state.markers[nr][nc] = "?";
                                state.arrows.addAll(cloneArrows(currentSquareArrows));
                                vizHistory.add(state);
                                continue; // Path is dead, proceed immediately to next direction
                            }
                        }

                        List<int[]> scannedOpponents = new ArrayList<>();
                        List<int[]> currentDirArrows = new ArrayList<>(); // Temporary active direction arrows

                        while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && game.getPieceAt(currR, currC) == opponent) {
                            scannedOpponents.add(new int[]{currR, currC});
                            
                            clearArray(curHighlights);
                            applyOOCandidateHighlights(curHighlights, currentSquareTexts, currentSquareMarkers, evaluated, 
                                                       persistentValids, persistentInvalids, nr, nc, true);
                            
                            // Apply transient highlights, crosses, and labels
                            applyTransientOverlay(curHighlights, currentSquareMarkers, currentSquareTexts, 
                                                  currentSpaceInvalids, currentSpaceFailedOpponents,
                                                  currentSquareMarkers, currentSquareTexts,
                                                  evaluated, persistentValids, persistentInvalids);
                            
                            for (int[] p : scannedOpponents) {
                                curHighlights[p[0]][p[1]] = 2; // Yellow opponent
                            }
                            
                            VizState state = new VizState(nr, nc, scanDir, step, persistentValids, persistentInvalids, curHighlights, 
                                    String.format("[%s] Opponent piece detected at %s", 
                                            scanDirName, OthelloBitboard.indexToAlgebraic(currR * 8 + currC)));
                            
                            copyStringArray(currentSquareMarkers, state.markers);
                            copyStringArray(currentSquareTexts, state.tileTexts);
                            
                            // Restore transient invalid markings on frame safely without NullPointerExceptions
                            applyTransientOverlay(curHighlights, state.markers, state.tileTexts, 
                                                  currentSpaceInvalids, currentSpaceFailedOpponents,
                                                  currentSquareMarkers, currentSquareTexts,
                                                  evaluated, persistentValids, persistentInvalids);
                            
                            state.tileTexts[nr][nc] = "Evaluating"; 
                            state.markers[nr][nc] = "?";
                            
                            for (int[] p : scannedOpponents) {
                                state.markers[p[0]][p[1]] = String.valueOf(scannedOpponents.indexOf(p) + 1);
                            }
                            
                            int prevR = currR - sdr;
                            int prevC = currC - sdc;
                            currentDirArrows.add(new int[]{prevR, prevC, currR, currC, 1}); // Yellow arrow
                            
                            state.arrows.addAll(cloneArrows(currentSquareArrows));
                            state.arrows.addAll(cloneArrows(currentDirArrows));
                            
                            vizHistory.add(state);
                            
                            currR += sdr;
                            currC += sdc;
                            step++;
                        }

                        // Check completion
                        if (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && game.getPieceAt(currR, currC) == currentPlayer) {
                            if (step > 1) {
                                cellIsIndeedValid = true;
                                evaluated[nr][nc] = true;
                                persistentValids[nr][nc] = true; // Retain validation permanently

                                clearArray(curHighlights);
                                applyOOCandidateHighlights(curHighlights, currentSquareTexts, currentSquareMarkers, evaluated, 
                                                           persistentValids, persistentInvalids, nr, nc, false);

                                // Keep empty space failures and failed opponents highlighted red
                                applyTransientOverlay(curHighlights, currentSquareMarkers, currentSquareTexts, 
                                                      currentSpaceInvalids, currentSpaceFailedOpponents,
                                                      currentSquareMarkers, currentSquareTexts,
                                                      evaluated, persistentValids, persistentInvalids);

                                for (int[] p : scannedOpponents) {
                                    curHighlights[p[0]][p[1]] = 3; // Captured green
                                }
                                curHighlights[currR][currC] = 3; // Friendly anchor green
                                
                                VizState state = new VizState(nr, nc, scanDir, step, persistentValids, persistentInvalids, curHighlights, 
                                        String.format("[%s] Friendly anchor found at %s! Valid line confirmed.", 
                                                scanDirName, OthelloBitboard.indexToAlgebraic(currR * 8 + currC)));
                                state.markers[nr][nc] = "✓"; 
                                state.tileTexts[nr][nc] = "Valid";
                                
                                for (int[] arrow : currentDirArrows) {
                                    arrow[4] = 3; // Change to Green
                                }
                                currentSquareArrows.addAll(currentDirArrows);

                                int tempR = nr;
                                int tempC = nc;
                                while (tempR != currR || tempC != currC) {
                                    currentSquareArrows.add(new int[]{tempR, tempC, tempR + sdr, tempC + sdc, 3}); // Green
                                    tempR += sdr;
                                    tempC += sdc;
                                }
                                
                                currentSquareMarkers[nr][nc] = "✓";
                                currentSquareTexts[nr][nc] = "Valid";
                                
                                copyStringArray(currentSquareMarkers, state.markers);
                                copyStringArray(currentSquareTexts, state.tileTexts);
                                
                                // Restore crosses and labels
                                applyTransientOverlay(curHighlights, state.markers, state.tileTexts, 
                                                      currentSpaceInvalids, currentSpaceFailedOpponents,
                                                      currentSquareMarkers, currentSquareTexts,
                                                      evaluated, persistentValids, persistentInvalids);

                                state.arrows.addAll(cloneArrows(currentSquareArrows));
                                vizHistory.add(state);
                            } else {
                                // Step is 1, adjacent is own friendly piece ("Own Piece")
                                currentSpaceInvalids[currR][currC] = true;
                                currentSquareMarkers[currR][currC] = "X";
                                currentSquareTexts[currR][currC] = "Own Piece";
                                currentSquareArrows.add(new int[]{nr, nc, currR, currC, 2}); // Red arrow
                                
                                clearArray(curHighlights);
                                applyOOCandidateHighlights(curHighlights, currentSquareTexts, currentSquareMarkers, evaluated, 
                                                           persistentValids, persistentInvalids, nr, nc, true);
                                
                                // Apply transient highlights, crosses, and labels safely
                                applyTransientOverlay(curHighlights, currentSquareMarkers, currentSquareTexts, 
                                                      currentSpaceInvalids, currentSpaceFailedOpponents,
                                                      currentSquareMarkers, currentSquareTexts,
                                                      evaluated, persistentValids, persistentInvalids);
                                
                                VizState state = new VizState(nr, nc, scanDir, step, persistentValids, persistentInvalids, curHighlights, 
                                        String.format("[%s] Friendly piece at %s is directly adjacent. No opponent pieces to flip.", 
                                                scanDirName, OthelloBitboard.indexToAlgebraic(currR * 8 + currC)));
                                
                                copyStringArray(currentSquareMarkers, state.markers);
                                copyStringArray(currentSquareTexts, state.tileTexts);
                                
                                applyTransientOverlay(curHighlights, state.markers, state.tileTexts, 
                                                      currentSpaceInvalids, currentSpaceFailedOpponents,
                                                      currentSquareMarkers, currentSquareTexts,
                                                      evaluated, persistentValids, persistentInvalids);

                                state.tileTexts[nr][nc] = "Evaluating"; // Keeps "Evaluating" text active
                                state.markers[nr][nc] = "?"; // Keeps "?" active
                                state.arrows.addAll(cloneArrows(currentSquareArrows));
                                vizHistory.add(state);
                            }
                        } else {
                            if (!scannedOpponents.isEmpty()) {
                                boolean isAlreadyValid = (currR >= 0 && currR < 8 && currC >= 0 && currC < 8) && persistentValids[currR][currC];
                                
                                // Register transient failures first to ensure synchronous rendering
                                currentSpaceInvalids[currR][currC] = true;
                                if (isAlreadyValid) {
                                    currentSquareMarkers[currR][currC] = "✓";
                                    currentSquareTexts[currR][currC] = "Valid";
                                } else {
                                    currentSquareMarkers[currR][currC] = "X"; 
                                    currentSquareTexts[currR][currC] = "Empty";
                                }
                                
                                int tempR = nr;
                                int tempC = nc;
                                while (tempR != currR || tempC != currC) {
                                    currentSquareArrows.add(new int[]{tempR, tempC, tempR + sdr, tempC + sdc, 2}); // Red Arrow
                                    tempR += sdr;
                                    tempC += sdc;
                                }
                                
                                // Track failed path opponents so they remain highlighted
                                for (int[] p : scannedOpponents) {
                                    currentSpaceFailedOpponents[p[0]][p[1]] = true;
                                }

                                clearArray(curHighlights);
                                applyOOCandidateHighlights(curHighlights, currentSquareTexts, currentSquareMarkers, evaluated, 
                                                           persistentValids, persistentInvalids, nr, nc, true);
                                
                                // Apply transient highlights, crosses, and labels
                                applyTransientOverlay(curHighlights, currentSquareMarkers, currentSquareTexts, 
                                                      currentSpaceInvalids, currentSpaceFailedOpponents,
                                                      currentSquareMarkers, currentSquareTexts,
                                                      evaluated, persistentValids, persistentInvalids);

                                boolean hitWall = (currR < 0 || currR >= 8 || currC < 0 || currC >= 8);
                                int failedTargetR = currR;
                                int failedTargetC = currC;
                                
                                for (int[] arrow : currentDirArrows) {
                                    arrow[4] = 2; // Red
                                }
                                currentSquareArrows.addAll(currentDirArrows);

                                if (hitWall) {
                                    failedTargetR = currR - sdr;
                                    failedTargetC = currC - sdc;
                                    
                                    clearArray(curHighlights);
                                    applyOOCandidateHighlights(curHighlights, currentSquareTexts, currentSquareMarkers, evaluated, 
                                                               persistentValids, persistentInvalids, nr, nc, true);
                                    
                                    // Highlight transient failures
                                    applyTransientOverlay(curHighlights, currentSquareMarkers, currentSquareTexts, 
                                                          currentSpaceInvalids, currentSpaceFailedOpponents,
                                                          currentSquareMarkers, currentSquareTexts,
                                                          evaluated, persistentValids, persistentInvalids);

                                    VizState state = new VizState(nr, nc, scanDir, step, persistentValids, persistentInvalids, curHighlights, 
                                            String.format("[%s] Path failed (Boundary wall reached).", scanDirName));
                                    
                                    currentSquareMarkers[failedTargetR][failedTargetC] = "X"; 
                                    currentSquareTexts[failedTargetR][failedTargetC] = "End of board";
                                    
                                    int tr = nr;
                                    int tc = nc;
                                    while (tr != failedTargetR || tc != failedTargetC) {
                                        currentSquareArrows.add(new int[]{tr, tc, tr + sdr, tc + sdc, 2}); // Red
                                        tr += sdr;
                                        tc += sdc;
                                    }
                                    
                                    copyStringArray(currentSquareMarkers, state.markers);
                                    copyStringArray(currentSquareTexts, state.tileTexts);
                                    
                                    applyTransientOverlay(curHighlights, state.markers, state.tileTexts, 
                                                          currentSpaceInvalids, currentSpaceFailedOpponents,
                                                          currentSquareMarkers, currentSquareTexts,
                                                          evaluated, persistentValids, persistentInvalids);

                                    state.tileTexts[nr][nc] = "Evaluating"; 
                                    state.markers[nr][nc] = "?";
                                    state.arrows.addAll(cloneArrows(currentSquareArrows));
                                    vizHistory.add(state);
                                } else {
                                    String failMsg = isAlreadyValid 
                                        ? String.format("[%s] Path failed (Hit already valid empty square at %s).", scanDirName, OthelloBitboard.indexToAlgebraic(currR * 8 + currC))
                                        : String.format("[%s] Path failed (Empty square hit at %s).", scanDirName, OthelloBitboard.indexToAlgebraic(currR * 8 + currC));

                                    VizState state = new VizState(nr, nc, scanDir, step, persistentValids, persistentInvalids, curHighlights, failMsg);
                                    
                                    copyStringArray(currentSquareMarkers, state.markers);
                                    copyStringArray(currentSquareTexts, state.tileTexts);
                                    
                                    applyTransientOverlay(curHighlights, state.markers, state.tileTexts, 
                                                          currentSpaceInvalids, currentSpaceFailedOpponents,
                                                          currentSquareMarkers, currentSquareTexts,
                                                          evaluated, persistentValids, persistentInvalids);

                                    state.tileTexts[nr][nc] = "Evaluating"; 
                                    state.markers[nr][nc] = "?";
                                    state.arrows.addAll(cloneArrows(currentSquareArrows));
                                    vizHistory.add(state);
                                }
                            }
                        }
                    }

                    // Once all 8 directions are fully checked without validation, evaluate immediately as Invalid
                    if (!cellIsIndeedValid) {
                        evaluated[nr][nc] = true;
                        persistentInvalids[nr][nc] = true; // Mark as permanently invalid on this board state

                        clearArray(curHighlights);
                        String[][] cleanMarkers = new String[8][8];
                        String[][] cleanTexts = new String[8][8];
                        
                        // Generates only board-persistent values, wiping transient checking lines/crosses
                        applyOOCandidateHighlights(curHighlights, cleanTexts, cleanMarkers, evaluated, 
                                                   persistentValids, persistentInvalids, nr, nc, false);

                        VizState invalidState = new VizState(nr, nc, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                                String.format("Square %s evaluated: No valid moves possible.", posName));
                        
                        copyStringArray(cleanMarkers, invalidState.markers);
                        copyStringArray(cleanTexts, invalidState.tileTexts);
                        
                        // Active transient arrows are left clean
                        vizHistory.add(invalidState);
                    }
                }
            }
        }
        
        // Final Frame: Shows all validated squares, and marks evaluated failed neighbor squares as "Invalid"
        clearArray(curHighlights);
        String[][] finalMarkers = new String[8][8];
        String[][] finalTexts = new String[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (game.getPieceAt(r, c) == EMPTY) {
                    if (persistentValids[r][c]) {
                        curHighlights[r][c] = 3; // Green (Valid)
                        finalMarkers[r][c] = "✓";
                        finalTexts[r][c] = "Valid";
                    } else {
                        // Marks only the evaluated adjacent neighbors that actually failed as "Invalid"
                        if (checked[r][c]) {
                            curHighlights[r][c] = 4; // Red (Invalid)
                            finalTexts[r][c] = "Invalid";
                            finalMarkers[r][c] = "X";
                        }
                    }
                }
            }
        }
        
        VizState finalState = new VizState(-1, -1, -1, 0, persistentValids, persistentInvalids, curHighlights, 
                "OO scan complete! All adjacent empty neighbors evaluated.");
        finalState.setMarkers(finalMarkers);
        finalState.setTileTexts(finalTexts);
        vizHistory.add(finalState);
    }

private void generateBitboardHistory() {
        vizHistory.clear();
        historyIndex = -1;

        int[][] curHighlights = new int[8][8];
        boolean[][] persistentValids = new boolean[8][8]; 
        boolean[][] persistentInvalids = new boolean[8][8]; 
        boolean[][] checked = new boolean[8][8]; // Tracks checked empty squares

        int opponent = (currentPlayer == BLACK) ? WHITE : BLACK;

        for (int d = 0; d < 8; d++) {
            int dr = DR[d];
            int dc = DC[d];
            String dirName = DIR_NAMES[d];

            // Precompute immediate invalid empty spaces right next to friendly pieces in direction d
            boolean[][] dirInvalids = new boolean[8][8];
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (game.getPieceAt(r, c) == EMPTY) {
                        int prevR = r - dr;
                        int prevC = c - dc;
                        if (prevR >= 0 && prevR < 8 && prevC >= 0 && prevC < 8) {
                            if (game.getPieceAt(prevR, prevC) == currentPlayer) dirInvalids[r][c] = true;
                        }
                    }
                }
            }

            // --- Step 0: Identify active friendly pieces & tiles to be evaluated ---
            clearArray(curHighlights);
            String[][] step0Texts = new String[8][8];
            String[][] step0Markers = new String[8][8];
            
            // Highlight own friendly pieces as Purple
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (game.getPieceAt(r, c) == currentPlayer) {
                        curHighlights[r][c] = 6; // Purple (Own Piece / Source)
                    }
                }
            }

            // Highlight own pieces together with empty tiles that will be evaluated in direction d
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    boolean isCandidate = isPotentialBitboardTarget(r, c, currentPlayer, d) || dirInvalids[r][c];
                    if (isCandidate) {
                        curHighlights[r][c] = 1; // Turquoise (Evaluating)
                        if (persistentValids[r][c]) {
                            step0Texts[r][c] = "Valid"; // Retains "Valid" text on re-evaluation
                            step0Markers[r][c] = "✓";
                        } else {
                            step0Texts[r][c] = "Evaluating";
                            step0Markers[r][c] = "?"; // Active candidate displays "?"
                        }
                    } else {
                        // Render non-candidates with their pre-existing resolved status
                        if (persistentValids[r][c]) {
                            curHighlights[r][c] = 3; // Green (Valid)
                            step0Texts[r][c] = "Valid";
                            step0Markers[r][c] = "✓";
                        } else if (persistentInvalids[r][c]) {
                            curHighlights[r][c] = 4; // Red (Invalid)
                            step0Texts[r][c] = "Invalid";
                            step0Markers[r][c] = "X";
                        }
                    }
                }
            }
            
            VizState step0State = new VizState(-1, -1, d, 0, persistentValids, persistentInvalids, curHighlights, 
                    String.format("[Bitboard] Step 0: Identify own pieces and evaluation candidates in direction %s", dirName));
            step0State.setTileTexts(step0Texts);
            step0State.setMarkers(step0Markers);
            vizHistory.add(step0State);

            // --- Step 1: Shift friendly board by 1 & Mark immediate empty spaces as Invalid ---
            clearArray(curHighlights);
            String[][] step1Texts = new String[8][8];
            String[][] step1Markers = new String[8][8];
            List<int[]> step1Arrows = new ArrayList<>();
            List<int[]> step1WallHits = new ArrayList<>();

            // Preserve Purple Highlights for own friendly pieces
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (game.getPieceAt(r, c) == currentPlayer) {
                        curHighlights[r][c] = 6; 
                    }
                }
            }

            // Other candidates that are not immediate invalids remain "Evaluating" (Blue/Turquoise) or "Valid"
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    boolean isCandidate = isPotentialBitboardTarget(r, c, currentPlayer, d) || dirInvalids[r][c];
                    if (isCandidate && !dirInvalids[r][c]) {
                        if (persistentValids[r][c]) {
                            curHighlights[r][c] = 1; // Remain Turquoise
                            step1Texts[r][c] = "Valid"; // Retains "Valid" text on re-evaluation
                            step1Markers[r][c] = "✓"; // Keep its checkmark
                        } else {
                            curHighlights[r][c] = 1; 
                            step1Texts[r][c] = "Evaluating";
                            step1Markers[r][c] = "?";
                        }
                    } else if (!isCandidate) {
                        if (persistentValids[r][c]) {
                            curHighlights[r][c] = 3;
                            step1Texts[r][c] = "Valid";
                            step1Markers[r][c] = "✓";
                        } else if (persistentInvalids[r][c]) {
                            curHighlights[r][c] = 4;
                            step1Texts[r][c] = "Invalid";
                            step1Markers[r][c] = "X";
                        }
                    }
                }
            }

            // Shift board by 1 position
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    int prevR = r - dr;
                    int prevC = c - dc;
                    if (prevR >= 0 && prevR < 8 && prevC >= 0 && prevC < 8) {
                        if (game.getPieceAt(prevR, prevC) == currentPlayer) {
                            int destPiece = game.getPieceAt(r, c);
                            if (destPiece == EMPTY) {
                                // Landing immediately on empty space -> Red/Invalid (or Red if already Valid)
                                if (persistentValids[r][c]) {
                                    curHighlights[r][c] = 4; // Highlighted Red ONLY in Step 1
                                    step1Texts[r][c] = "Valid"; // Retains "Valid" text
                                    step1Markers[r][c] = "✓"; // Keep its checkmark
                                } else {
                                    curHighlights[r][c] = 4; // Red (Failed path)
                                    step1Texts[r][c] = "Invalid";
                                    step1Markers[r][c] = "X"; // Only draw X if not already valid
                                }
                                step1Arrows.add(new int[]{prevR, prevC, r, c, 2}); // Red arrow represents vector check failure
                                checked[r][c] = true;
                            } else if (destPiece == opponent) {
                                // Landing on opponent -> Capturable candidate!
                                curHighlights[r][c] = 5; // Orange (Shifted/Capturable)
                                step1Markers[r][c] = "✓";
                                step1Arrows.add(new int[]{prevR, prevC, r, c, 1}); // Yellow shifting arrow
                            } else if (destPiece == currentPlayer) {
                                // Landed on friendly own piece
                                curHighlights[r][c] = 4; 
                                step1Markers[r][c] = "X";
                                step1Texts[r][c] = "Own Piece"; // Add "Own Piece" text label on friendly alignment
                                step1Arrows.add(new int[]{prevR, prevC, r, c, 2}); // Red arrow
                            }
                        }
                    }
                    
                    if (game.getPieceAt(r, c) == currentPlayer) {
                        int nextR = r + dr;
                        int nextC = c + dc;
                        if (nextR < 0 || nextR >= 8 || nextC < 0 || nextC >= 8) {
                            curHighlights[r][c] = 4; 
                            step1WallHits.add(new int[]{r, c, r, c, nextR, nextC});
                            step1Texts[r][c] = "End of board";
                        }
                    }
                }
            }

            VizState step1State = new VizState(-1, -1, d, 1, persistentValids, persistentInvalids, curHighlights, String.format("[Bitboard] Step 1: Shift friendly board by 1 position in direction %s", dirName));
            step1State.setTileTexts(step1Texts);
            step1State.setMarkers(step1Markers);
            step1State.arrows = step1Arrows;
            step1State.wallHits = step1WallHits;
            vizHistory.add(step1State);

            // --- Step 2: Mask opponent pieces recursively in a chain ---
            clearArray(curHighlights);
            String[][] step2Texts = new String[8][8];
            String[][] step2Markers = new String[8][8];
            List<int[]> step2Arrows = new ArrayList<>();

            // Preserve Purple own pieces
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (game.getPieceAt(r, c) == currentPlayer) {
                        curHighlights[r][c] = 6; 
                    }
                }
            }

            // Keep Step 1's immediate invalid empty spaces marked Red (or revert back to Green if already valid)
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (dirInvalids[r][c]) {
                        if (persistentValids[r][c]) {
                            curHighlights[r][c] = 3; // Reverted back to Green (3) for Step 2
                            step2Texts[r][c] = "Valid"; // Retains "Valid" text
                            step2Markers[r][c] = "✓"; // Keep its checkmark
                        } else {
                            curHighlights[r][c] = 4; // Red
                            step2Texts[r][c] = "Invalid";
                            step2Markers[r][c] = "X";
                        }
                    } else {
                        boolean isCandidate = isPotentialBitboardTarget(r, c, currentPlayer, d);
                        if (isCandidate) {
                            if (persistentValids[r][c]) {
                                curHighlights[r][c] = 1; // Remain Turquoise
                                step2Texts[r][c] = "Valid"; // Retains "Valid" text
                                step2Markers[r][c] = "✓";
                            } else {
                                curHighlights[r][c] = 1; 
                                step2Texts[r][c] = "Evaluating";
                                step2Markers[r][c] = "?";
                            }
                        } else {
                            if (persistentValids[r][c]) {
                                curHighlights[r][c] = 3;
                                step2Texts[r][c] = "Valid";
                                step2Markers[r][c] = "✓";
                            } else if (persistentInvalids[r][c]) {
                                curHighlights[r][c] = 4;
                                step2Texts[r][c] = "Invalid";
                                step2Markers[r][c] = "X";
                            }
                        }
                    }
                }
            }

            // Scan and map out the recursive shifting chains of opponent pieces
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (game.getPieceAt(r, c) == currentPlayer) {
                        int currR = r + dr;
                        int currC = c + dc;
                        List<int[]> scannedOpponents = new ArrayList<>();
                        
                        while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && game.getPieceAt(currR, currC) == opponent) {
                            scannedOpponents.add(new int[]{currR, currC});
                            currR += dr;
                            currC += dc;
                        }
                        
                        if (!scannedOpponents.isEmpty()) {
                            int[] firstOpp = scannedOpponents.get(0);
                            int[] lastOpp = scannedOpponents.get(scannedOpponents.size() - 1);
                            
                            int stepIndex = 1;
                            for (int[] opp : scannedOpponents) {
                                curHighlights[opp[0]][opp[1]] = 2; // Yellow (Opponent)
                                step2Markers[opp[0]][opp[1]] = String.valueOf(stepIndex++); 
                            }
                            
                            step2Arrows.add(new int[]{firstOpp[0], firstOpp[1], lastOpp[0], lastOpp[1], 1}); // Yellow arrow detailing chain depth
                        }
                    }
                }
            }

            VizState step2State = new VizState(-1, -1, d, 2, persistentValids, persistentInvalids, curHighlights, String.format("[Bitboard] Step 2: Mask shifted friendly board with opponent pieces in direction %s", dirName));
            step2State.setTileTexts(step2Texts);
            step2State.setMarkers(step2Markers);
            step2State.arrows = step2Arrows;
            vizHistory.add(step2State);

            // --- Step 3: Find empty spaces that come after a chain of opponent pieces (Mark as Valid) ---
            clearArray(curHighlights);
            String[][] step3Texts = new String[8][8];
            String[][] step3Markers = new String[8][8];
            List<int[]> step3Arrows = new ArrayList<>();
            List<int[]> step3WallHits = new ArrayList<>();

            // Preserve own Purple pieces
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (game.getPieceAt(r, c) == currentPlayer) {
                        curHighlights[r][c] = 6; 
                    }
                }
            }

            // Keep Step 1's immediate invalid empty spaces marked Red (or revert back to Green if already valid)
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (dirInvalids[r][c]) {
                        if (persistentValids[r][c]) {
                            curHighlights[r][c] = 3; // Reverted back to Green (3) for Step 3
                            step3Texts[r][c] = "Valid"; // Retains "Valid" text
                            step3Markers[r][c] = "✓"; // Keep its checkmark
                        } else {
                            curHighlights[r][c] = 4; // Red
                            step3Texts[r][c] = "Invalid";
                            step3Markers[r][c] = "X";
                        }
                    } else {
                        boolean isCandidate = isPotentialBitboardTarget(r, c, currentPlayer, d);
                        if (isCandidate) {
                            if (persistentValids[r][c]) {
                                curHighlights[r][c] = 1; // Remain Turquoise
                                step3Texts[r][c] = "Valid"; // Retains "Valid" text
                                step3Markers[r][c] = "✓";
                            } else {
                                curHighlights[r][c] = 1; 
                                step3Texts[r][c] = "Evaluating";
                                step3Markers[r][c] = "?";
                            }
                        } else {
                            if (persistentValids[r][c]) {
                                curHighlights[r][c] = 3;
                                step3Texts[r][c] = "Valid";
                                step3Markers[r][c] = "✓";
                            } else if (persistentInvalids[r][c]) {
                                curHighlights[r][c] = 4;
                                step3Texts[r][c] = "Invalid";
                                step3Markers[r][c] = "X";
                            }
                        }
                    }
                }
            }

            // Mask shifts with empty spaces to evaluate the validity
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (game.getPieceAt(r, c) == currentPlayer) {
                        int currR = r + dr;
                        int currC = c + dc;
                        int count = 0;
                        while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && game.getPieceAt(currR, currC) == opponent) {
                            count++;
                            currR += dr;
                            currC += dc;
                        }
                        
                        if (count > 0) {
                            int nextR = currR;
                            int nextC = currC;
                            int lastOppR = currR - dr;
                            int lastOppC = currC - dc;
                            
                            if (nextR < 0 || nextR >= 8 || nextC < 0 || nextC >= 8) {
                                // Shift hits board boundary
                                curHighlights[lastOppR][lastOppC] = 4; 
                                step3WallHits.add(new int[]{r, c, lastOppR, lastOppC, nextR, nextC});
                                step3Texts[lastOppR][lastOppC] = "End of board";
                            } else {
                                int nextPiece = game.getPieceAt(nextR, nextC);
                                if (nextPiece == EMPTY) {
                                    // Empty space immediately following opponent chain -> VALID!
                                    curHighlights[nextR][nextC] = 3; // Green (Valid)
                                    persistentValids[nextR][nextC] = true;
                                    step3Markers[nextR][nextC] = "✓";
                                    step3Texts[nextR][nextC] = "Valid";
                                    step3Arrows.add(new int[]{r, c, nextR, nextC, 3}); // Green arrow
                                    checked[nextR][nextC] = true;
                                } else if (nextPiece == currentPlayer) {
                                    // Landed back on friendly own piece -> Invalid (or Red if already Valid)
                                    if (persistentValids[nextR][nextC]) {
                                        curHighlights[nextR][nextC] = 4; // Fails directional scan, so highlighted in Red
                                        step3Texts[nextR][nextC] = "Valid"; // Retains "Valid" text
                                        step3Markers[nextR][nextC] = "✓"; // Keep its checkmark
                                    } else {
                                        curHighlights[nextR][nextC] = 4; // Red (Failed check)
                                        step3Markers[nextR][nextC] = "X";
                                        step3Texts[nextR][nextC] = "Own Piece"; // Add "Own Piece" text label on friendly alignment
                                    }
                                    step3Arrows.add(new int[]{r, c, nextR, nextC, 2}); // Red arrow
                                }
                            }
                        }
                    }
                }
            }

            VizState step3State = new VizState(-1, -1, d, 3, persistentValids, persistentInvalids, curHighlights, String.format("[Bitboard] Step 3: Shift recursively and mask with empty spaces in direction %s", dirName));
            step3State.setTileTexts(step3Texts);
            step3State.setMarkers(step3Markers);
            step3State.arrows = step3Arrows;
            step3State.wallHits = step3WallHits;
            vizHistory.add(step3State);
        }

        // --- Final Frame: Shows all validated squares, and marks all failed empty adjacent squares as "Invalid" ---
        clearArray(curHighlights);
        String[][] finalMarkers = new String[8][8];
        String[][] finalTexts = new String[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (game.getPieceAt(r, c) == EMPTY) {
                    if (persistentValids[r][c]) {
                        curHighlights[r][c] = 3; // Green (Valid)
                        finalMarkers[r][c] = "✓";
                        finalTexts[r][c] = "Valid";
                    } else {
                        if (checked[r][c]) {
                            curHighlights[r][c] = 4; // Red (Invalid)
                            finalTexts[r][c] = "Invalid";
                            finalMarkers[r][c] = "X";
                        }
                    }
                }
            }
        }
        
        VizState finalState = new VizState(-1, -1, -1, 0, persistentValids, persistentInvalids, curHighlights, "Bitboard scan complete! All valid moves are highlighted.");
        finalState.setMarkers(finalMarkers);
        finalState.setTileTexts(finalTexts);
        vizHistory.add(finalState);
    }


    private class BoardSquare extends JPanel {
        private final int row;
        private final int col;

        public BoardSquare(int row, int col) {
            this.row = row;
            this.col = col;
            setBackground(new Color(34, 139, 34));
            setBorder(BorderFactory.createLineBorder(new Color(0, 50, 0), 1));
            
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    handleHumanMove(row, col);
                }
                @Override
                public void mouseEntered(MouseEvent e) {
                    handleSquareHover(row, col); 
                }
            });
        }

        // Standard vector checkmark generator (Fixed "Tofu" Boxes)
        private void drawCheckmark(Graphics2D g2, int cx, int cy, int size) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(4.0f));
            
            // Shadow
            g2.setColor(new Color(0, 0, 0, 180));
            g2.drawLine(cx - size/3 + 1, cy + 1, cx - size/10 + 1, cy + size/3 + 1);
            g2.drawLine(cx - size/10 + 1, cy + size/3 + 1, cx + size/3 + 1, cy - size/3 + 1);
            
            // Foreground
            g2.setColor(new Color(0, 255, 0));
            g2.drawLine(cx - size/3, cy, cx - size/10, cy + size/3);
            g2.drawLine(cx - size/10, cy + size/3, cx + size/3, cy - size/3);
        }

        // Standard vector cross generator
        private void drawCross(Graphics2D g2, int cx, int cy, int size) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(4.0f));
            
            // Shadow
            g2.setColor(new Color(0, 0, 0, 180));
            g2.drawLine(cx - size/3 + 1, cy - size/3 + 1, cx + size/3 + 1, cy + size/3 + 1);
            g2.drawLine(cx + size/3 + 1, cy - size/3 + 1, cx - size/3 + 1, cy + size/3 + 1);
            
            // Foreground
            g2.setColor(new Color(255, 50, 50));
            g2.drawLine(cx - size/3, cy - size/3, cx + size/3, cy + size/3);
            g2.drawLine(cx + size/3, cy - size/3, cx - size/3, cy + size/3);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int highlight = debugHighlights[row][col];
            
            // Retain found valid locations in a soft transparent green hue
            boolean isPersistentValid = false;
            boolean isPersistentInvalid = false;


            if (visualizerMode && historyIndex >= 0 && historyIndex < vizHistory.size()) {
                VizState state = vizHistory.get(historyIndex);
                isPersistentValid = state.persistentValids[row][col];
                isPersistentInvalid = state.persistentInvalids[row][col];
            }
            
            int piece = game.getPieceAt(row, col);

            // 1. Draw Translucent Color Highlights
            if (highlight != 0) {
                if (highlight == 1) g2.setColor(new Color(0, 191, 255, 120));      // Blue (Origins / Own Piece)
                else if (highlight == 2) g2.setColor(new Color(255, 215, 0, 120)); // Yellow (Candidates)
                else if (highlight == 3) g2.setColor(new Color(50, 205, 50, 120)); // Green (Valid)
                else if (highlight == 4) g2.setColor(new Color(220, 20, 60, 120)); // Red (Failed paths)
                else if (highlight == 5) g2.setColor(new Color(255, 140, 0, 120)); // Orange (Shifts)
                else if (highlight == 6) g2.setColor(new Color(160, 32, 240, 120)); // Vibrant Purple (Active Friendly Step 0)
                g.fillRect(0, 0, getWidth(), getHeight());
            } 
            else if (isPersistentValid) {
                g2.setColor(new Color(50, 205, 50, 60)); 
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            else if (isPersistentInvalid) {
                g2.setColor(new Color(220, 20, 60, 40)); 
                g.fillRect(0, 0, getWidth(), getHeight());
            }

            // 2. Draw active game discs
            boolean isValidMove = (!isProcessing && !isAITurn() && !visualizerMode && game.isValidMove(row, col, currentPlayer));

            int padding = 6;
            int size = Math.min(getWidth(), getHeight()) - 12;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            if (piece == BLACK) {
                drawDisc(g2, Color.BLACK, x, y, size);
            } else if (piece == WHITE) {
                drawDisc(g2, Color.WHITE, x, y, size);
            } else if (isValidMove) {
                g2.setColor(new Color(0, 0, 0, 40));
                int hintSize = size / 3;
                g2.fillOval(getWidth() / 2 - hintSize / 2, getHeight() / 2 - hintSize / 2, hintSize, hintSize);
            }
        }

        private void drawDisc(Graphics2D g2, Color color, int x, int y, int size) {
            g2.setColor(color);
            g2.fillOval(x, y, size, size);
            if (color == Color.BLACK) {
                g2.setColor(new Color(50, 50, 50));
            } else {
                g2.setColor(new Color(200, 200, 200));
            }
            g2.drawArc(x + 2, y + 2, size - 4, size - 4, 45, 180);
        }
    }

    // =========================================================================
    // MODIFIED: AUTOMATED BENCHMARK FRAMEWORK WITH COMPREHENSIVE PERFORMANCE SUMMARY
    // =========================================================================

    private void runAutomatedBenchmark() {
        if (isProcessing) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        // Lock UI controls for the duration of the benchmark run
        isProcessing = true;
        restartBtn.setEnabled(false);
        loadRecordBtn.setEnabled(false);
        
        benchmarkBtn.setEnabled(true);
        benchmarkBtn.setText("Stop Benchmark");
        benchmarkBtn.setForeground(Color.RED);

        depthSlider.setEnabled(false);
        bitboardRadio.setEnabled(false);
        ooRadio.setEnabled(false);
        nestedRadio.setEnabled(false);
        visualizerCheckbox.setEnabled(false);
        alphaBetaCheckbox.setEnabled(false);
        moveOrderingCheckbox.setEnabled(false);

        SwingUtilities.invokeLater(() -> row3.setVisible(false));

        activeBenchmarkThread = new Thread(() -> {
            StringBuilder summary = new StringBuilder();
            summary.append("<html><body style='font-family: sans-serif; font-size: 11px; padding: 10px; color: #333333;'>");
            summary.append("<h2 style='color: #2C3E50; border-bottom: 2px solid #2C3E50; padding-bottom: 5px; margin-bottom: 15px;'>Othello AI Automated Benchmark</h2>");
            System.out.println("=== OTHELLO AI AUTOMATED BENCHMARK ===\n");

            final String[] expectedMoves = new String[12]; 

            try {
                // TYPE 0: Visualizer Modes (Omitted from GUI summary modal, but run on-screen)
                // 1. Fresh Board (Traditional)
                runSingleBenchmarkStep(EngineType.FLAT_ARRAY, true, true, true, 7, "1D Flat Array - Fresh Board Visualizer", "#777777", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                // 2. Fresh Board (Bitboard)
                runSingleBenchmarkStep(EngineType.BITBOARD, true, true, true, 7, "Bitboard - Fresh Board Visualizer", "#777777", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                // 3. Takizawa Board (Traditional)
                runSingleBenchmarkStep(EngineType.FLAT_ARRAY, true, true, true, 7, "1D Flat Array - Takizawa Visualizer", "#777777", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                // 4. Takizawa Board (Bitboard)
                runSingleBenchmarkStep(EngineType.BITBOARD, true, true, true, 7, "Bitboard - Takizawa Visualizer", "#777777", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                // TYPE 1: No Alpha-Beta Pruning (Depth 6) - CRIMSON RED
                summary.append("<h3 style='color: #D32F2F; margin-top: 15px; border-bottom: 1px solid #D32F2F; padding-bottom: 3px;'>Type 1: No Alpha-Beta Pruning (Depth 6)</h3>");
                double t6_nested = runSingleBenchmarkStep(EngineType.NESTED_OBJECT, false, false, false, 6, "2D Cell Objects - No AB Pruning (Depth 6)", "#D32F2F", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t6_flat = runSingleBenchmarkStep(EngineType.FLAT_ARRAY, false, false, false, 6, "1D Flat Array - No AB Pruning (Depth 6)", "#D32F2F", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t6_bitboard = runSingleBenchmarkStep(EngineType.BITBOARD, false, false, false, 6, "Bitboard - No AB Pruning (Depth 6)", "#D32F2F", expectedMoves, summary, true);
                
                appendComparison(summary, t6_nested, t6_flat, t6_bitboard);
                appendSeparator(summary);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                // TYPE 2: Alpha-Beta Pruning, No Move Ordering (Depth 9) - DEEP BLUE
                summary.append("<h3 style='color: #1976D2; margin-top: 15px; border-bottom: 1px solid #1976D2; padding-bottom: 3px;'>Type 2: Alpha-Beta Pruning, No Move Ordering (Depth 9)</h3>");
                double t9_nested = runSingleBenchmarkStep(EngineType.NESTED_OBJECT, false, true, false, 9, "2D Cell Objects - AB, No Move Ordering (Depth 9)", "#1976D2", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t9_flat = runSingleBenchmarkStep(EngineType.FLAT_ARRAY, false, true, false, 9, "1D Flat Array - AB, No Move Ordering (Depth 9)", "#1976D2", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t9_bitboard = runSingleBenchmarkStep(EngineType.BITBOARD, false, true, false, 9, "Bitboard - AB, No Move Ordering (Depth 9)", "#1976D2", expectedMoves, summary, true);
                
                appendComparison(summary, t9_nested, t9_flat, t9_bitboard);
                appendSeparator(summary);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                // TYPE 3: Alpha-Beta Pruning with Move Ordering (Depth 10) - DEEP GREEN
                summary.append("<h3 style='color: #388E3C; margin-top: 15px; border-bottom: 1px solid #388E3C; padding-bottom: 3px;'>Type 3: Alpha-Beta Pruning with Move Ordering (Depth 10)</h3>");
                double t10_nested = runSingleBenchmarkStep(EngineType.NESTED_OBJECT, false, true, true, 10, "2D Cell Objects - AB with Move Ordering (Depth 10)", "#388E3C", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t10_flat = runSingleBenchmarkStep(EngineType.FLAT_ARRAY, false, true, true, 10, "1D Flat Array - AB with Move Ordering (Depth 10)", "#388E3C", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t10_bitboard = runSingleBenchmarkStep(EngineType.BITBOARD, false, true, true, 10, "Bitboard - AB with Move Ordering (Depth 10)", "#388E3C", expectedMoves, summary, true);
                
                appendComparison(summary, t10_nested, t10_flat, t10_bitboard);

                // Close the final HTML layout
                summary.append("</body></html>");

                // Present summary report visually
                SwingUtilities.invokeLater(() -> {
                    JEditorPane editorPane = new JEditorPane();
                    editorPane.setContentType("text/html");
                    editorPane.setText(summary.toString());
                    editorPane.setEditable(false);
                    editorPane.setCaretPosition(0);

                    JScrollPane scrollPane = new JScrollPane(editorPane);
                    scrollPane.setPreferredSize(new java.awt.Dimension(640, 500));
                    JOptionPane.showMessageDialog(OthelloGUI.this, scrollPane, "Benchmark Results Summary", JOptionPane.INFORMATION_MESSAGE);
                });

            } catch (InterruptedException ex) {
                System.out.println("Benchmark interrupted.");
            } finally {
                // Restore controls on Event Dispatch Thread (EDT)
                SwingUtilities.invokeLater(() -> {
                    isProcessing = false;
                    restartBtn.setEnabled(true);
                    loadRecordBtn.setEnabled(true);
                    
                    benchmarkBtn.setEnabled(true);
                    benchmarkBtn.setText("Run Benchmark");
                    benchmarkBtn.setForeground(Color.BLACK);

                    depthSlider.setEnabled(true);
                    bitboardRadio.setEnabled(true);
                    ooRadio.setEnabled(true);
                    nestedRadio.setEnabled(true);
                    visualizerCheckbox.setSelected(false);
                    visualizerMode = false;
                    visualizerCheckbox.setEnabled(true);
                    alphaBetaCheckbox.setEnabled(true);
                    moveOrderingCheckbox.setEnabled(alphaBetaCheckbox.isSelected());
                    
                    restartGame();
                });
            }
        });
        activeBenchmarkThread.start();
    }

    private void stopAutomatedBenchmark() {
        // Immediately turn off the processing flag to kill any pending EDT tasks
        isProcessing = false;

        // Stop the progress timer immediately to prevent any further EDT updates
        if (aiProgressTimer != null && aiProgressTimer.isRunning()) {
            aiProgressTimer.stop();
        }

        // Cancel the active background search worker immediately (sends interrupt signal)
        if (activeBenchmarkWorker != null && !activeBenchmarkWorker.isDone()) {
            activeBenchmarkWorker.cancel(true);
        }

        // Interrupt the main benchmark controller thread
        if (activeBenchmarkThread != null && activeBenchmarkThread.isAlive()) {
            activeBenchmarkThread.interrupt();
        }

        // Trigger a full GUI restart on the EDT to perform a clean game reset
        SwingUtilities.invokeLater(() -> {
            stopAutoPlay();
            clearHighlights();
            
            // Explicitly uncheck visualizer modes before calling restartGame()
            visualizerCheckbox.setSelected(false);
            visualizerMode = false;
            
            restartGame(); // Reinitializes board back to default 4 discs and clears labels
            aiInfoLabel.setText("Benchmark stopped and reset.");
        });
    }

    private double runSingleBenchmarkStep(EngineType engine, boolean vizMode, boolean abActive, boolean moActive, int depth, String testName, String colorHex, String[] expectedMoves, StringBuilder summary, boolean isTakizawa) {
        // Step 1: Update settings on GUI (via the EDT)
        try {
            SwingUtilities.invokeAndWait(() -> {
                selectedEngine = engine;
                bitboardRadio.setSelected(engine == EngineType.BITBOARD);
                ooRadio.setSelected(engine == EngineType.FLAT_ARRAY);
                nestedRadio.setSelected(engine == EngineType.NESTED_OBJECT);

                visualizerCheckbox.setSelected(vizMode);
                visualizerMode = vizMode;

                alphaBetaCheckbox.setSelected(abActive);
                if (abActive) {
                    moveOrderingCheckbox.setSelected(moActive);
                } else {
                    moveOrderingCheckbox.setSelected(false);
                }

                // Ensure both checkboxes are locked disabled during benchmark execution
                alphaBetaCheckbox.setEnabled(false);
                moveOrderingCheckbox.setEnabled(false);

                aiDepth = depth;
                depthSlider.setValue(depth);
                depthLabel.setText("Depth: " + depth);

                // Setup board state dynamically
                resetBoardForBenchmark(vizMode, isTakizawa); 
                
                boardPanel.repaint();
                updateStatus();
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // Give the UI a brief moment to render the newly updated layout state
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0.0;
        }

        // Step 2: Execute the evaluation
        String consoleResultStr = "";
        double finalTimeSec = 0.0;

        if (vizMode) {
            long startTime = System.nanoTime();
            if (engine == EngineType.BITBOARD) {
                generateBitboardHistory();
            } else if (engine == EngineType.FLAT_ARRAY) {
                generateFlatArrayOptimizedHistory(); // Added routing
            } else {
                generateOOHistory();
            }
            long endTime = System.nanoTime();
            double durationMs = (endTime - startTime) / 1_000_000.0;
            finalTimeSec = durationMs / 1000.0;
            int frames = vizHistory.size();

            consoleResultStr = String.format(
                "Test: %s\n" +
                " -> Visualizer History Generated\n" +
                " -> Total Frames: %d\n" +
                " -> Generation Time: %.2f ms\n\n",
                testName, frames, durationMs
            );
            
            // Kick off visualizer playback on the EDT
            SwingUtilities.invokeLater(() -> {
                row3.setVisible(true); // Made visible specifically for visualizer tests
                historyIndex = 0;
                applyHistoryFrame();
                startAutoPlay();
            });

            // Wait for the EDT to schedule and trigger startAutoPlay()
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0.0;
            }

            // Block the background benchmark thread here until the animation completes
            while (isAutoPlaying) {
                try {
                    Thread.sleep(100); // Poll status every 100ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } else {
            // Uncheck/pause any visualizer autoplay animations running on the GUI, and hide VCR panel
            SwingUtilities.invokeLater(() -> {
                stopAutoPlay();
                row3.setVisible(false); // Hidden outside of visualizer mode
                clearHighlights(); // Clear any visualizer overlay remnants before search starts
                boardPanel.repaint();
            });

            // Launch the search via an asynchronous SwingWorker so that the ticking progress timer runs on the EDT
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final int[] foundMoveIdx = new int[1];
            final long[] searchedStates = new long[1];
            final long[] searchEvals = new long[1];
            final double[] searchTimeSec = new double[1];

            SwingUtilities.invokeLater(() -> {
                if (!isProcessing) {
                    latch.countDown();
                    return; // Abort starting search worker if stopped
                }
                aiStartTime = System.nanoTime();
                aiProgressTimer = new Timer(50, ev -> {
                    long checked = game.getEvaluatedNodes();
                    long evals = game.getEvaluationCount();
                    long elapsedNanos = System.nanoTime() - aiStartTime;
                    double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
                    long nps = 0;
                    if (elapsedNanos > 1_000_000) { 
                        nps = Math.round(checked / elapsedSeconds);
                    }
                    aiInfoLabel.setText(String.format("[%s] %,d states, %,d evals (%,d/sec)", testName, checked, evals, nps));
                });
                aiProgressTimer.start();

                SwingWorker<Integer, Void> searchWorker = new SwingWorker<>() {
                    @Override
                    protected Integer doInBackground() {
                        long start = System.nanoTime();
                        int move = game.findBestMove(currentPlayer, depth);
                        long end = System.nanoTime();
                        searchTimeSec[0] = (end - start) / 1_000_000_000.0;
                        return move;
                    }

                    @Override
                    protected void done() {
                        aiProgressTimer.stop();
                        if (!isProcessing) {
                            latch.countDown();
                            return; // Abort any updates if benchmark was stopped
                        }
                        try {
                            foundMoveIdx[0] = get();
                            searchedStates[0] = game.getEvaluatedNodes();
                            searchEvals[0] = game.getEvaluationCount();
                        } catch (Exception e) {
                            foundMoveIdx[0] = -1;
                        }
                        latch.countDown(); // Wake up the benchmark thread
                    }
                };
                activeBenchmarkWorker = searchWorker; // Save reference so we can cancel it on Stop
                searchWorker.execute();
            });

            // Block benchmark thread until asynchronous worker finishes
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0.0;
            }

            long states = searchedStates[0];
            long evals = searchEvals[0];
            double durationSec = searchTimeSec[0];
            finalTimeSec = durationSec;
            int bestMoveIdx = foundMoveIdx[0];

            long nps = 0;
            if (durationSec > 0.0001) {
                nps = Math.round(states / durationSec);
            }

            String bestMoveAlg = OthelloBitboard.indexToAlgebraic(bestMoveIdx);
            
            // Establish/Verify baseline logic internally
            if (expectedMoves[depth] == null) {
                expectedMoves[depth] = bestMoveAlg; 
            }

            consoleResultStr = String.format(
                "Test: %s\n" +
                " -> States searched: %,d\n" +
                " -> Heuristic evals: %,d\n" +
                " -> Time taken: %.4f seconds\n" +
                " -> Throughput: %,d states/sec\n" +
                " -> Move found: %s\n\n",
                testName, states, evals, durationSec, nps, bestMoveAlg
            );

            // Format colored result blocks as HTML (excluding verifications)
            String htmlResult = String.format(
                "<div style='margin-bottom: 10px; font-family: monospace; font-size: 11px;'>" +
                " <span style='color: %s; font-weight: bold;'>&bull; %s</span><br>" +
                " &nbsp; &rarr; States searched: <b>%,d</b><br>" +
                " &nbsp; &rarr; Heuristic evals: <b>%,d</b><br>" +
                " &nbsp; &rarr; Time taken: <b>%.4f</b> seconds<br>" +
                " &nbsp; &rarr; Throughput: <b>%,d</b> states/sec<br>" +
                " &nbsp; &rarr; Move found: <b>%s</b>" +
                "</div>",
                colorHex, testName, states, evals, durationSec, nps, bestMoveAlg
            );
            
            summary.append(htmlResult);
            
            // Visually play the move on the GUI board
            SwingUtilities.invokeLater(() -> {
                if (!isProcessing) return;
                if (bestMoveIdx != -1) {
                    game.makeMove(bestMoveIdx / 8, bestMoveIdx % 8, currentPlayer);
                    boardPanel.repaint();
                }
            });
        }

        System.out.print(consoleResultStr);
        return finalTimeSec;
    }

    private void appendComparison(StringBuilder summary, double t_nested, double t_flat, double t_bitboard) {
        // Protect divisions against zero on extremely fast runs
        double ratio_flat_to_nested = t_nested / Math.max(t_flat, 0.000001);
        double ratio_bitboard_to_flat = t_flat / Math.max(t_bitboard, 0.000001);
        double ratio_bitboard_to_nested = t_nested / Math.max(t_bitboard, 0.000001);

        String comparisonHtml = String.format(
            "<div style='background-color: #F5F7FA; border-left: 4px solid #1976D2; padding: 10px; margin: 10px 0; font-family: monospace; font-size: 11px; color: #2C3E50;'>" +
            " <b>Type Performance Comparison:</b><br>" +
            " &bull; 1D Flat Array is <b>%.1fx</b> faster than 2D Cell Objects<br>" +
            " &bull; Bitboard is <b>%.1fx</b> faster than 1D Flat Array<br>" +
            " &bull; Bitboard is <b>%.1fx</b> faster than 2D Cell Objects" +
            "</div>",
            ratio_flat_to_nested, ratio_bitboard_to_flat, ratio_bitboard_to_nested
        );
        summary.append(comparisonHtml);
    }

    private void appendSeparator(StringBuilder summary) {
        summary.append("<hr style='border: 0; border-top: 1px dashed #CCCCCC; margin: 15px 0;'>");
    }

    private void resetBoardForBenchmark(boolean isVisualizer, boolean isTakizawa) {
        vizHistory.clear();
        historyIndex = -1;
        clearHighlights();

        if (selectedEngine == EngineType.BITBOARD) {
            game = new OthelloBitboard(); 
        } else if (selectedEngine == EngineType.FLAT_ARRAY) {
            game = new OthelloFlatArray(); 
        } else {
            game = new OthelloCellObjects(); 
        }
        
        game.setUseAlphaBeta(alphaBetaCheckbox.isSelected());
        game.setUseMoveOrdering(moveOrderingCheckbox.isSelected());

        if (isTakizawa) {
            // Play the 26 moves to load the Takizawa-33 record
            String[] moves = {
                "F5", "D6", "C4", "F3", "C5", "B4", "B3", "E6", "C6", "G5", "F6", "C7", "C3", "D2", "C2", "B2", "F4", "G4", "G3", "G7", "G6", "E7", "D3", "G2", "H3", "B6"
            };

            int activePlayer = BLACK;
            for (String move : moves) {
                int idx = OthelloBitboard.algebraicToIndex(move); 
                if (idx != -1) {
                    int r = idx / 8;
                    int c = idx % 8;
                    if (game.isValidMove(r, c, activePlayer)) {
                        game.makeMove(r, c, activePlayer);
                    }
                    activePlayer = (activePlayer == BLACK) ? WHITE : BLACK;
                }
            }
            
            if (isVisualizer) {
                currentPlayer = BLACK; // Keep Black's turn (Move 27) active for visualizer
            } else {
                // For search simulations, Black automatically plays "D1" immediately as Move 27
                int d1Index = OthelloBitboard.algebraicToIndex("D1"); 
                if (game.isValidMove(d1Index / 8, d1Index % 8, activePlayer)) {
                    game.makeMove(d1Index / 8, d1Index % 8, activePlayer);
                }
                currentPlayer = WHITE; // Pass turn to WHITE (Move 28) for evaluation
            }
        } else {
            // Fresh board starting state (4 center discs)
            currentPlayer = BLACK; // Black's turn to move
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new OthelloGUI().setVisible(true);
        });
    }
}