package src.ui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import src.engine.*;
import src.visualizer.*;
import src.benchmark.*;

public class OthelloGUI extends JFrame implements OthelloConstants {
    private OthelloBoard game; 
    private final BoardSquare[][] squares = new BoardSquare[8][8];
    private JLabel statusLabel;
    private JLabel aiInfoLabel; 
    private JButton stopAIButton; 
    private JPanel boardPanel;
    private Timer aiProgressTimer; 
    
    private JRadioButton bitboardRadio;
    private JRadioButton ooRadio;
    private JRadioButton primitive2dRadio; 
    private JRadioButton nestedRadio;
    private JCheckBox visualizerCheckbox;
    private JCheckBox alphaBetaCheckbox;
    private JCheckBox moveOrderingCheckbox;

    private JSlider depthSlider;
    private JLabel depthLabel;

    private SwingWorker<Integer, Void> aiWorker; 
    private boolean aiSuspended = false; 

    private EngineType selectedEngine = EngineType.BITBOARD;
    private boolean lastMoveOrderingPreference = true;
    private long aiStartTime = 0; 

    private boolean visualizerMode = false;
    private final int[][] debugHighlights = new int[8][8]; 
    private int currentPlayer = BLACK; 
    private int aiDepth = 7; 
    private volatile boolean isProcessing = false; 

    private JButton restartBtn;
    private JButton loadRecordBtn;
    private JButton benchmarkBtn;

    private final OthelloBenchmarkRunner benchmarkRunner;

    private JPanel row3; 
    private JButton prevButton;
    private JButton playPauseButton;
    private JButton nextButton;
    private JSlider speedSlider;
    
    private volatile boolean isAutoPlaying = false; 
    private Timer autoPlayTimer;
    private int visualizerSpeedMs = 500; 

    private final List<VizState> vizHistory = new ArrayList<>();
    private int historyIndex = -1;

    public OthelloGUI() {
        game = new OthelloBitboard(); 
        benchmarkRunner = new OthelloBenchmarkRunner(this);
        
        setTitle("Othello AI - State Visualization");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        createHeaderPanel(); 
        createBoard();
        createStatusBar();

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
        setSize(1050, 740); 
        setLocationRelativeTo(null); 
        updateStatus();
    }

    // =========================================================
    // High-Level Public API for BenchmarkRunner & BoardSquare
    // =========================================================

    public OthelloBoard getGame() { return game; }
    public int getCurrentPlayer() { return currentPlayer; }
    public boolean isProcessing() { return isProcessing; }
    public boolean isAutoPlaying() { return isAutoPlaying; }
    public boolean isVisualizerMode() { return visualizerMode; }
    public void setAiInfoText(String text) { aiInfoLabel.setText(text); }
    public int getDebugHighlight(int row, int col) { return debugHighlights[row][col]; }

    public VizState getCurrentVizState() {
        if (visualizerMode && historyIndex >= 0 && historyIndex < vizHistory.size()) {
            return vizHistory.get(historyIndex);
        }
        return null;
    }

    public boolean isValidHumanMove(int row, int col) {
        return !isProcessing && !isAITurn() && !visualizerMode && game.isValidMove(row, col, currentPlayer);
    }

    public void setBenchmarkRunningUI(boolean running) {
        this.isProcessing = running;
        restartBtn.setEnabled(!running);
        loadRecordBtn.setEnabled(!running);
        benchmarkBtn.setEnabled(true);
        benchmarkBtn.setText(running ? "Stop Benchmark" : "Run Benchmark");
        benchmarkBtn.setForeground(running ? Color.RED : Color.BLACK);

        depthSlider.setEnabled(!running);
        bitboardRadio.setEnabled(!running);
        ooRadio.setEnabled(!running);
        primitive2dRadio.setEnabled(!running);
        nestedRadio.setEnabled(!running);
        visualizerCheckbox.setEnabled(!running);
        alphaBetaCheckbox.setEnabled(!running);
        moveOrderingCheckbox.setEnabled(!running);

        row3.setVisible(false);

        if (!running) {
            visualizerCheckbox.setSelected(false);
            visualizerMode = false;
        }
    }

    public void configureForBenchmark(EngineType engine, boolean vizMode, boolean abActive, 
                                      boolean moActive, int depth, boolean isTakizawa) {
        this.selectedEngine = engine;
        this.visualizerMode = vizMode;
        this.aiDepth = depth;

        bitboardRadio.setSelected(engine == EngineType.BITBOARD);
        ooRadio.setSelected(engine == EngineType.FLAT_ARRAY);
        primitive2dRadio.setSelected(engine == EngineType.PRIMITIVE_2D);
        nestedRadio.setSelected(engine == EngineType.NESTED_OBJECT);

        visualizerCheckbox.setSelected(vizMode);
        alphaBetaCheckbox.setSelected(abActive);
        alphaBetaCheckbox.setEnabled(false);
        moveOrderingCheckbox.setSelected(abActive && moActive);
        moveOrderingCheckbox.setEnabled(false);

        depthSlider.setValue(depth);
        depthLabel.setText("Depth: " + depth);

        resetBoardForBenchmark(vizMode, isTakizawa);
        boardPanel.repaint();
        updateStatus();
    }

    public int generateVisualizerHistory() {
        generateHistory();
        return vizHistory.size();
    }

    public void prepareAndStartVisualizerPlayback() {
        row3.setVisible(true);
        historyIndex = 0;
        applyHistoryFrame();
        startAutoPlay();
    }

    public void prepareForSearchBenchmark() {
        stopAutoPlay();
        row3.setVisible(false);
        clearHighlights();
        boardPanel.repaint();
    }

    public void playBenchmarkMove(int bestMoveIdx) {
        if (bestMoveIdx != -1) {
            game.makeMove(bestMoveIdx / 8, bestMoveIdx % 8, currentPlayer);
            boardPanel.repaint();
        }
    }

    public void stopBenchmarkAndReset() {
        isProcessing = false;
        if (aiProgressTimer != null && aiProgressTimer.isRunning()) {
            aiProgressTimer.stop();
        }
        SwingUtilities.invokeLater(() -> {
            stopAutoPlay();
            clearHighlights();
            setBenchmarkRunningUI(false);
            restartGame();
            setAiInfoText("Benchmark stopped and reset.");
        });
    }

    public void resetBoardForBenchmark(boolean isVisualizer, boolean isTakizawa) {
        vizHistory.clear();
        historyIndex = -1;
        clearHighlights();

        switch (selectedEngine) {
            case BITBOARD: game = new OthelloBitboard(); break;
            case FLAT_ARRAY: game = new OthelloFlatArray(); break;
            case PRIMITIVE_2D: game = new OthelloPrimitive(); break;
            case NESTED_OBJECT: game = new OthelloCellObjects(); break;
        }

        game.setUseAlphaBeta(alphaBetaCheckbox.isSelected());
        game.setUseMoveOrdering(moveOrderingCheckbox.isSelected());

        if (isTakizawa) {
            String[] moves = {
                "F5", "D6", "C4", "F3", "C5", "B4", "B3", "E6", "C6", "G5", "F6", "C7", "C3", 
                "D2", "C2", "B2", "F4", "G4", "G3", "G7", "G6", "E7", "D3", "G2", "H3", "B6"
            };
            int activePlayer = BLACK;
            for (String move : moves) {
                int idx = OthelloBitboard.algebraicToIndex(move);
                if (idx != -1 && game.isValidMove(idx / 8, idx % 8, activePlayer)) {
                    game.makeMove(idx / 8, idx % 8, activePlayer);
                    activePlayer = (activePlayer == BLACK) ? WHITE : BLACK;
                }
            }
            if (isVisualizer) {
                currentPlayer = BLACK;
            } else {
                int d1 = OthelloBitboard.algebraicToIndex("D1");
                if (game.isValidMove(d1 / 8, d1 % 8, activePlayer)) {
                    game.makeMove(d1 / 8, d1 % 8, activePlayer);
                }
                currentPlayer = WHITE;
            }
        } else {
            if (isVisualizer) {
                currentPlayer = BLACK;
            } else {
                int d3 = OthelloBitboard.algebraicToIndex("D3");
                if (d3 != -1 && game.isValidMove(d3 / 8, d3 % 8, BLACK)) {
                    game.makeMove(d3 / 8, d3 % 8, BLACK);
                }
                currentPlayer = WHITE;
            }
        }
    }

    public void restartGame() {
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
        } else if (selectedEngine == EngineType.PRIMITIVE_2D) {
            if (primitive2dRadio != null) primitive2dRadio.setSelected(true);
            game = new OthelloPrimitive(); 
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
            
            generateHistory();
            
            historyIndex = 0;
            applyHistoryFrame();
            stopAutoPlay();
            row3.setVisible(true);
        } else {
            aiSuspended = false; 
            aiInfoLabel.setText("");
            stopAIButton.setEnabled(false);
            stopAIButton.setVisible(false); 
            if (depthSlider != null) depthSlider.setEnabled(true);
            if (bitboardRadio != null) bitboardRadio.setEnabled(true);
            if (ooRadio != null) ooRadio.setEnabled(true);
            if (primitive2dRadio != null) primitive2dRadio.setEnabled(true);
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

    public void handleHumanMove(int row, int col) {
        if (isProcessing || isAITurn() || visualizerMode) return;

        if (game.isValidMove(row, col, currentPlayer)) {
            game.makeMove(row, col, currentPlayer);
            boardPanel.repaint();
            advanceTurn();
        } else {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    // =========================================================
    // Internal Private Helpers
    // =========================================================

    private void applyHistoryFrame() {
        if (historyIndex < 0 || historyIndex >= vizHistory.size()) return;

        VizState state = vizHistory.get(historyIndex);
        
        for (int r = 0; r < 8; r++) {
            System.arraycopy(state.highlights[r], 0, debugHighlights[r], 0, 8);
        }
        
        String arrowSym = "";
        if (state.dir >= 0 && state.dir < 8) {
            arrowSym = " " + DIR_ARROWS[state.dir];
        }
        
        aiInfoLabel.setText(String.format("Step %d/%d: %s%s", historyIndex + 1, vizHistory.size(), state.explanation, arrowSym));
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
        playPauseButton.setIcon(new OthelloIcons.StopIcon(14, 14, new Color(220, 20, 60))); 
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
        playPauseButton.setIcon(new OthelloIcons.PlayIcon(14, 14, new Color(34, 139, 34))); 
        
        if (autoPlayTimer != null) {
            autoPlayTimer.stop();
        }
        
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
            if (isProcessing && benchmarkBtn.getText().equals("Stop Benchmark")) {
                benchmarkRunner.stopAutomatedBenchmark();
            } else {
                benchmarkRunner.runAutomatedBenchmark();
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
                visualizerCheckbox.setSelected(visualizerMode); 
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
                
                generateHistory();

                row3.setVisible(true);
                historyIndex = 0;
                applyHistoryFrame();
                stopAutoPlay(); 
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
                alphaBetaCheckbox.setSelected(!alphaBetaCheckbox.isSelected()); 
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
                moveOrderingCheckbox.setSelected(!moveOrderingCheckbox.isSelected()); 
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

        primitive2dRadio = new JRadioButton("2D Primitive", false); 
        primitive2dRadio.setFont(new Font("Arial", Font.PLAIN, 12));
        primitive2dRadio.setOpaque(false);
        primitive2dRadio.setCursor(new Cursor(Cursor.HAND_CURSOR));
        primitive2dRadio.addActionListener(e -> {
            if (isProcessing) {
                primitive2dRadio.setSelected(selectedEngine == EngineType.PRIMITIVE_2D);
                Toolkit.getDefaultToolkit().beep();
                return;
            }
            if (selectedEngine != EngineType.PRIMITIVE_2D) {
                selectedEngine = EngineType.PRIMITIVE_2D;
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
        engineGroup.add(primitive2dRadio);
        engineGroup.add(nestedRadio);

        enginePanel.add(visualizerCheckbox);
        enginePanel.add(alphaBetaCheckbox);
        enginePanel.add(moveOrderingCheckbox);
        enginePanel.add(Box.createHorizontalStrut(10));
        enginePanel.add(engineLabel);
        enginePanel.add(bitboardRadio);
        enginePanel.add(ooRadio);
        enginePanel.add(primitive2dRadio);
        enginePanel.add(nestedRadio);

        headerPanel.add(actionPanel, BorderLayout.WEST);
        headerPanel.add(enginePanel, BorderLayout.EAST);

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
        btn.setFocusPainted(false); 
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBackground(Color.WHITE);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
    }

    private void generateHistory() {
        VisualizerHistoryGenerator generator = new VisualizerHistoryGenerator(game, currentPlayer);
        vizHistory.clear();
        if (selectedEngine == EngineType.BITBOARD) {
            vizHistory.addAll(generator.generateBitboardHistory());
        } else if (selectedEngine == EngineType.FLAT_ARRAY) {
            vizHistory.addAll(generator.generateFlatArrayOptimizedHistory());
        } else if (selectedEngine == EngineType.PRIMITIVE_2D) {
            vizHistory.addAll(generator.generateOOHistoryFrontier());
        } else {
            vizHistory.addAll(generator.generateOOHistoryPrimitive());
        }
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

        if (selectedEngine == EngineType.BITBOARD) {
            game = new OthelloBitboard(); 
        } else if (selectedEngine == EngineType.FLAT_ARRAY) {
            game = new OthelloFlatArray(); 
        } else if (selectedEngine == EngineType.PRIMITIVE_2D) {
            game = new OthelloPrimitive(); 
        } else {
            game = new OthelloCellObjects(); 
        }

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

        currentPlayer = activePlayer; 
        isProcessing = false;

        if (visualizerMode) {
            aiSuspended = true;
            stopAIButton.setEnabled(false);
            stopAIButton.setVisible(false); 
            if (depthSlider != null) depthSlider.setEnabled(false);
            
            generateHistory();
            
            historyIndex = 0;
            applyHistoryFrame();
            stopAutoPlay(); 
            row3.setVisible(true);
        } else {
            aiSuspended = false; 
            aiInfoLabel.setText("Takizawa Record-33 loaded. Black's turn.");
            stopAIButton.setEnabled(false);
            stopAIButton.setVisible(false); 
            if (depthSlider != null) depthSlider.setEnabled(true);
            if (bitboardRadio != null) bitboardRadio.setEnabled(true);
            if (ooRadio != null) ooRadio.setEnabled(true);
            if (primitive2dRadio != null) primitive2dRadio.setEnabled(true);
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
                super.paint(g); 
                if (visualizerMode && historyIndex >= 0 && historyIndex < vizHistory.size()) {
                    VisualizerRenderer.drawVisualizerOverlays((Graphics2D) g, vizHistory.get(historyIndex), getWidth(), getHeight());
                }
            }
        };
        boardPanel.setBackground(Color.BLACK);
        boardPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                squares[row][col] = new BoardSquare(row, col, this);
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
        stopAIButton.setVisible(false); 
        stopAIButton.setFocusPainted(false); 
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
        prevButton.setFocusPainted(false); 
        prevButton.addActionListener(e -> {
            if (isProcessing) return; 
            stepBackward();
        });

        playPauseButton = new JButton();
        playPauseButton.setFocusPainted(false); 
        playPauseButton.setIcon(new OthelloIcons.PlayIcon(14, 14, new Color(34, 139, 34))); 
        playPauseButton.setPreferredSize(new Dimension(50, 30));
        playPauseButton.addActionListener(e -> {
            if (isProcessing) return; 
            toggleAutoPlay();
        });

        nextButton = new JButton("Next >>");
        nextButton.setFont(new Font("Arial", Font.BOLD, 11));
        nextButton.setFocusPainted(false); 
        nextButton.addActionListener(e -> {
            if (isProcessing) return; 
            stepForward();
        });

        JLabel speedLabel = new JLabel("Speed:");
        speedLabel.setFont(new Font("Arial", Font.BOLD, 11));

        speedSlider = new JSlider(JSlider.HORIZONTAL, 1, 100, 15);
        speedSlider.setPreferredSize(new Dimension(100, 30));
        speedSlider.setOpaque(false);
        speedSlider.addChangeListener(e -> {
            int val = speedSlider.getValue();
            double minFps = 0.5;  
            double maxFps = 66.0; 
            double fps = minFps + (double)(val - 1) * (maxFps - minFps) / 99.0;
            visualizerSpeedMs = (int) Math.max(1, 1000.0 / fps); 
            
            if (autoPlayTimer != null) {
                autoPlayTimer.setDelay(visualizerSpeedMs);
                autoPlayTimer.setInitialDelay(visualizerSpeedMs);
                if (autoPlayTimer.isRunning()) {
                    autoPlayTimer.restart(); 
                }
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

    private void switchEngine() {
        if (aiWorker != null) {
            aiWorker.cancel(true);
            aiWorker = null;
        }
        if (aiProgressTimer != null && aiProgressTimer.isRunning()) {
            aiProgressTimer.stop();
        }
        stopAutoPlay();

        int[][] tempBoard = new int[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                tempBoard[r][c] = game.getPieceAt(r, c);
            }
        }

        if (selectedEngine == EngineType.BITBOARD) {
            if (bitboardRadio != null) bitboardRadio.setSelected(true);
            game = new OthelloBitboard(); 
        } else if (selectedEngine == EngineType.FLAT_ARRAY) {
            if (ooRadio != null) ooRadio.setSelected(true);
            game = new OthelloFlatArray(); 
        } else if (selectedEngine == EngineType.PRIMITIVE_2D) {
            if (primitive2dRadio != null) primitive2dRadio.setSelected(true);
            game = new OthelloPrimitive(); 
        } else {
            if (nestedRadio != null) nestedRadio.setSelected(true);
            game = new OthelloCellObjects(); 
        }
        game.setUseAlphaBeta(alphaBetaCheckbox.isSelected());
        game.setUseMoveOrdering(moveOrderingCheckbox.isSelected());

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                game.setPieceAt(r, c, tempBoard[r][c]);
            }
        }

        if (visualizerMode) {
            aiSuspended = true;
            stopAIButton.setEnabled(false);
            if (depthSlider != null) depthSlider.setEnabled(false);
            
            generateHistory();
            
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
            if (primitive2dRadio != null) primitive2dRadio.setEnabled(true);
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

        if (isAITurn()) {
            playAITurn();
        }
    }

    private boolean isAITurn() {
        if (aiSuspended) return false; 
        return currentPlayer == WHITE;
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
        stopAIButton.setVisible(true); 
        stopAIButton.setEnabled(true); 
        depthSlider.setEnabled(false); 
        bitboardRadio.setEnabled(false);
        ooRadio.setEnabled(false);
        primitive2dRadio.setEnabled(false);
        nestedRadio.setEnabled(false);
        visualizerCheckbox.setEnabled(false);
        alphaBetaCheckbox.setEnabled(false);
        moveOrderingCheckbox.setEnabled(false);
        restartBtn.setEnabled(false);
        loadRecordBtn.setEnabled(false);

        aiStartTime = System.nanoTime();

        aiProgressTimer = new Timer(50, e -> {
            long checked = game.getEvaluatedNodes();
            long elapsedNanos = System.nanoTime() - aiStartTime;
            double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
            
            long nps = 0;
            if (elapsedNanos > 1_000_000) { 
                nps = Math.round(checked / elapsedSeconds);
            }
            
            boolean ab = alphaBetaCheckbox.isSelected();
            boolean mo = moveOrderingCheckbox.isSelected();
            String settingsStr = String.format("[Alpha-Beta: %s, Move Ordering: %s]", ab ? "ON" : "OFF", mo ? "ON" : "OFF");

            aiInfoLabel.setText(String.format("Evaluated %,d states in %.2fs (%,d/sec) %s ", checked, elapsedSeconds, nps, settingsStr));
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
                stopAIButton.setVisible(false); 
                depthSlider.setEnabled(true); 
                bitboardRadio.setEnabled(true);
                ooRadio.setEnabled(true);
                primitive2dRadio.setEnabled(true);
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
                    long elapsedNanos = System.nanoTime() - aiStartTime;
                    double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
                    
                    long nps = 0;
                    if (elapsedNanos > 1_000_000) {
                        nps = Math.round(checked / elapsedSeconds);
                    }
                    
                    boolean ab = alphaBetaCheckbox.isSelected();
                    boolean mo = moveOrderingCheckbox.isSelected();
                    String settingsStr = String.format("[Alpha-Beta: %s, Move Ordering: %s]", ab ? "ON" : "OFF", mo ? "ON" : "OFF");

                    aiInfoLabel.setText(String.format("Evaluated %,d states at depth %d in %.3fs (%,d/sec) %s", 
                            checked, aiDepth, elapsedSeconds, nps, settingsStr));

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
        } else if (selectedEngine == EngineType.PRIMITIVE_2D) {
            engineName = "2D Primitive Values";
        } else {
            engineName = "2D Cell Objects";
        }

        statusLabel.setText(String.format("Black: %d  |  White: %d   ---   %s %s   [%s Engine]", 
                blackCount, whiteCount, turn, playerType, engineName));
    }

    private void clearHighlights() {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                debugHighlights[i][j] = 0;
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new OthelloGUI().setVisible(true);
        });
    }
}