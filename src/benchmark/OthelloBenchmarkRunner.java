package src.benchmark;

import javax.swing.*;
import java.awt.*;

import src.ui.OthelloGUI;
import src.engine.*;

public class OthelloBenchmarkRunner implements OthelloConstants {
    private final OthelloGUI gui;
    private Thread activeBenchmarkThread;
    private SwingWorker<Integer, Void> activeBenchmarkWorker;
    private Timer benchmarkProgressTimer;

    public OthelloBenchmarkRunner(OthelloGUI gui) {
        this.gui = gui;
    }

    public void runAutomatedBenchmark() {
        if (gui.isProcessing()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        gui.setBenchmarkRunningUI(true);

        activeBenchmarkThread = new Thread(() -> {
            StringBuilder summary = new StringBuilder();
            summary.append("<html><body style='font-family: sans-serif; font-size: 11px; padding: 10px; color: #333333;'>");
            summary.append("<h2 style='color: #2C3E50; border-bottom: 2px solid #2C3E50; padding-bottom: 5px; margin-bottom: 15px;'>Othello AI Automated Benchmark</h2>");
            System.out.println("=== OTHELLO AI AUTOMATED BENCHMARK ===\n");

            final String[] expectedMoves = new String[16]; 

            try {
                // PHASE 1: FRESH BOARD VISUALIZATIONS
                runSingleBenchmarkStep(EngineType.NESTED_OBJECT, true, true, true, 7, "2D Cell Objects - Fresh Board Visualizer", "#777777", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                runSingleBenchmarkStep(EngineType.PRIMITIVE_2D, true, true, true, 7, "2D Primitive Values - Fresh Board Visualizer", "#777777", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                runSingleBenchmarkStep(EngineType.FLAT_ARRAY, true, true, true, 7, "1D Flat Array - Fresh Board Visualizer", "#777777", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                runSingleBenchmarkStep(EngineType.BITBOARD, true, true, true, 7, "Bitboard - Fresh Board Visualizer", "#777777", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                // PHASE 2: TAKIZAWA BOARD VISUALIZATIONS
                runSingleBenchmarkStep(EngineType.NESTED_OBJECT, true, true, true, 7, "2D Cell Objects - Takizawa Visualizer", "#777777", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                runSingleBenchmarkStep(EngineType.PRIMITIVE_2D, true, true, true, 7, "2D Primitive Values - Takizawa Visualizer", "#777777", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                runSingleBenchmarkStep(EngineType.FLAT_ARRAY, true, true, true, 7, "1D Flat Array - Takizawa Visualizer", "#777777", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                runSingleBenchmarkStep(EngineType.BITBOARD, true, true, true, 7, "Bitboard - Takizawa Visualizer", "#777777", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                // PHASE 3: FRESH BOARD BENCHMARKS (Search)
                summary.append("<h2 style='color: #2E4053; margin-top: 25px; border-bottom: 2px solid #2E4053; padding-bottom: 5px;'>Fresh Board Search Benchmarks</h2>");

                // Fresh Board Type 1: No Alpha-Beta Pruning (Depth 9)
                summary.append("<h3 style='color: #D32F2F; margin-top: 15px; border-bottom: 1px solid #D32F2F; padding-bottom: 3px;'>Type 1: No Alpha-Beta Pruning (Depth 9)</h3>");
                double t6_fresh_nested = runSingleBenchmarkStep(EngineType.NESTED_OBJECT, false, false, false, 9, "2D Cell Objects - Fresh Board - No AB Pruning (Depth 9)", "#D32F2F", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t6_fresh_primitive = runSingleBenchmarkStep(EngineType.PRIMITIVE_2D, false, false, false, 9, "2D Primitive Values - Fresh Board - No AB Pruning (Depth 9)", "#D32F2F", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t6_fresh_flat = runSingleBenchmarkStep(EngineType.FLAT_ARRAY, false, false, false, 9, "1D Flat Array - Fresh Board - No AB Pruning (Depth 9)", "#D32F2F", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t6_fresh_bitboard = runSingleBenchmarkStep(EngineType.BITBOARD, false, false, false, 9, "Bitboard - Fresh Board - No AB Pruning (Depth 9)", "#D32F2F", expectedMoves, summary, false);
                appendComparison4(summary, t6_fresh_nested, t6_fresh_primitive, t6_fresh_flat, t6_fresh_bitboard);
                appendSeparator(summary);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                // Fresh Board Type 2: Alpha-Beta Pruning, No Move Ordering (Depth 15)
                summary.append("<h3 style='color: #1976D2; margin-top: 15px; border-bottom: 1px solid #1976D2; padding-bottom: 3px;'>Type 2: Alpha-Beta Pruning, No Move Ordering (Depth 15)</h3>");
                double t9_fresh_nested = runSingleBenchmarkStep(EngineType.NESTED_OBJECT, false, true, false, 15, "2D Cell Objects - Fresh Board - AB, No Move Ordering (Depth 15)", "#1976D2", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t9_fresh_primitive = runSingleBenchmarkStep(EngineType.PRIMITIVE_2D, false, true, false, 15, "2D Primitive Values - Fresh Board - AB, No Move Ordering (Depth 15)", "#1976D2", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t9_fresh_flat = runSingleBenchmarkStep(EngineType.FLAT_ARRAY, false, true, false, 15, "1D Flat Array - Fresh Board - AB, No Move Ordering (Depth 15)", "#1976D2", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t9_fresh_bitboard = runSingleBenchmarkStep(EngineType.BITBOARD, false, true, false, 15, "Bitboard - Fresh Board - AB, No Move Ordering (Depth 15)", "#1976D2", expectedMoves, summary, false);
                appendComparison4(summary, t9_fresh_nested, t9_fresh_primitive, t9_fresh_flat, t9_fresh_bitboard);
                appendSeparator(summary);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                // Fresh Board Type 3: Alpha-Beta Pruning with Move Ordering (Depth 15)
                summary.append("<h3 style='color: #388E3C; margin-top: 15px; border-bottom: 1px solid #388E3C; padding-bottom: 3px;'>Type 3: Alpha-Beta Pruning with Move Ordering (Depth 15)</h3>");
                double t10_fresh_nested = runSingleBenchmarkStep(EngineType.NESTED_OBJECT, false, true, true, 15, "2D Cell Objects - Fresh Board - AB with Move Ordering (Depth 15)", "#388E3C", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t10_fresh_primitive = runSingleBenchmarkStep(EngineType.PRIMITIVE_2D, false, true, true, 15, "2D Primitive Values - Fresh Board - AB with Move Ordering (Depth 15)", "#388E3C", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t10_fresh_flat = runSingleBenchmarkStep(EngineType.FLAT_ARRAY, false, true, true, 15, "1D Flat Array - Fresh Board - AB with Move Ordering (Depth 15)", "#388E3C", expectedMoves, summary, false);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t10_fresh_bitboard = runSingleBenchmarkStep(EngineType.BITBOARD, false, true, true, 15, "Bitboard - Fresh Board - AB with Move Ordering (Depth 15)", "#388E3C", expectedMoves, summary, false);
                appendComparison4(summary, t10_fresh_nested, t10_fresh_primitive, t10_fresh_flat, t10_fresh_bitboard);
                appendSeparator(summary);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                // PHASE 4: TAKIZAWA BOARD BENCHMARKS (Search)
                summary.append("<h2 style='color: #2E4053; margin-top: 25px; border-bottom: 2px solid #2E4053; padding-bottom: 5px;'>Takizawa Board Search Benchmarks</h2>");

                // Takizawa Type 1: No Alpha-Beta Pruning (Depth 6)
                summary.append("<h3 style='color: #D32F2F; margin-top: 15px; border-bottom: 1px solid #D32F2F; padding-bottom: 3px;'>Type 1: No Alpha-Beta Pruning (Depth 6)</h3>");
                double t6_tak_nested = runSingleBenchmarkStep(EngineType.NESTED_OBJECT, false, false, false, 6, "2D Cell Objects - Takizawa - No AB Pruning (Depth 6)", "#D32F2F", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t6_tak_primitive = runSingleBenchmarkStep(EngineType.PRIMITIVE_2D, false, false, false, 6, "2D Primitive Values - Takizawa - No AB Pruning (Depth 6)", "#D32F2F", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t6_tak_flat = runSingleBenchmarkStep(EngineType.FLAT_ARRAY, false, false, false, 6, "1D Flat Array - Takizawa - No AB Pruning (Depth 6)", "#D32F2F", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t6_tak_bitboard = runSingleBenchmarkStep(EngineType.BITBOARD, false, false, false, 6, "Bitboard - Takizawa - No AB Pruning (Depth 6)", "#D32F2F", expectedMoves, summary, true);
                appendComparison4(summary, t6_tak_nested, t6_tak_primitive, t6_tak_flat, t6_tak_bitboard);
                appendSeparator(summary);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                // Takizawa Type 2: Alpha-Beta Pruning, No Move Ordering (Depth 9)
                summary.append("<h3 style='color: #1976D2; margin-top: 15px; border-bottom: 1px solid #1976D2; padding-bottom: 3px;'>Type 2: Alpha-Beta Pruning, No Move Ordering (Depth 9)</h3>");
                double t9_tak_nested = runSingleBenchmarkStep(EngineType.NESTED_OBJECT, false, true, false, 9, "2D Cell Objects - Takizawa - AB, No Move Ordering (Depth 9)", "#1976D2", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t9_tak_primitive = runSingleBenchmarkStep(EngineType.PRIMITIVE_2D, false, true, false, 9, "2D Primitive Values - Takizawa - AB, No Move Ordering (Depth 9)", "#1976D2", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t9_tak_flat = runSingleBenchmarkStep(EngineType.FLAT_ARRAY, false, true, false, 9, "1D Flat Array - Takizawa - AB, No Move Ordering (Depth 9)", "#1976D2", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t9_tak_bitboard = runSingleBenchmarkStep(EngineType.BITBOARD, false, true, false, 9, "Bitboard - Takizawa - AB, No Move Ordering (Depth 9)", "#1976D2", expectedMoves, summary, true);
                appendComparison4(summary, t9_tak_nested, t9_tak_primitive, t9_tak_flat, t9_tak_bitboard);
                appendSeparator(summary);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                // Takizawa Type 3: Alpha-Beta Pruning with Move Ordering (Depth 10)
                summary.append("<h3 style='color: #388E3C; margin-top: 15px; border-bottom: 1px solid #388E3C; padding-bottom: 3px;'>Type 3: Alpha-Beta Pruning with Move Ordering (Depth 10)</h3>");
                double t10_tak_nested = runSingleBenchmarkStep(EngineType.NESTED_OBJECT, false, true, true, 10, "2D Cell Objects - Takizawa - AB with Move Ordering (Depth 10)", "#388E3C", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t10_tak_primitive = runSingleBenchmarkStep(EngineType.PRIMITIVE_2D, false, true, true, 10, "2D Primitive Values - Takizawa - AB with Move Ordering (Depth 10)", "#388E3C", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t10_tak_flat = runSingleBenchmarkStep(EngineType.FLAT_ARRAY, false, true, true, 10, "1D Flat Array - Takizawa - AB with Move Ordering (Depth 10)", "#388E3C", expectedMoves, summary, true);
                if (Thread.currentThread().isInterrupted()) return;
                Thread.sleep(1000);

                double t10_tak_bitboard = runSingleBenchmarkStep(EngineType.BITBOARD, false, true, true, 10, "Bitboard - Takizawa - AB with Move Ordering (Depth 10)", "#388E3C", expectedMoves, summary, true);
                appendComparison4(summary, t10_tak_nested, t10_tak_primitive, t10_tak_flat, t10_tak_bitboard);

                summary.append("</body></html>");

                SwingUtilities.invokeLater(() -> {
                    JEditorPane editorPane = new JEditorPane();
                    editorPane.setContentType("text/html");
                    editorPane.setText(summary.toString());
                    editorPane.setEditable(false);
                    editorPane.setCaretPosition(0);

                    JScrollPane scrollPane = new JScrollPane(editorPane);
                    scrollPane.setPreferredSize(new java.awt.Dimension(640, 500));
                    JOptionPane.showMessageDialog(gui, scrollPane, "Benchmark Results Summary", JOptionPane.INFORMATION_MESSAGE);
                });

            } catch (InterruptedException ex) {
                System.out.println("Benchmark interrupted.");
            } finally {
                SwingUtilities.invokeLater(() -> {
                    gui.setBenchmarkRunningUI(false);
                    gui.restartGame();
                });
            }
        });
        activeBenchmarkThread.start();
    }

    public void stopAutomatedBenchmark() {
        if (benchmarkProgressTimer != null && benchmarkProgressTimer.isRunning()) {
            benchmarkProgressTimer.stop();
        }

        if (activeBenchmarkWorker != null && !activeBenchmarkWorker.isDone()) {
            activeBenchmarkWorker.cancel(true);
        }

        if (activeBenchmarkThread != null && activeBenchmarkThread.isAlive()) {
            activeBenchmarkThread.interrupt();
        }

        gui.stopBenchmarkAndReset();
    }

    private double runSingleBenchmarkStep(EngineType engine, boolean vizMode, boolean abActive, boolean moActive, 
                                          int depth, String testName, String colorHex, String[] expectedMoves, 
                                          StringBuilder summary, boolean isTakizawa) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                gui.configureForBenchmark(engine, vizMode, abActive, moActive, depth, isTakizawa);
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0.0;
        }

        String consoleResultStr = "";
        double finalTimeSec = 0.0;

        if (vizMode) {
            long startTime = System.nanoTime();
            int frames = gui.generateVisualizerHistory();
            long endTime = System.nanoTime();
            double durationMs = (endTime - startTime) / 1_000_000.0;
            finalTimeSec = durationMs / 1000.0;

            consoleResultStr = String.format(
                "Test: %s\n" +
                " -> Visualizer History Generated\n" +
                " -> Total Frames: %d\n" +
                " -> Generation Time: %.2f ms\n\n",
                testName, frames, durationMs
            );
            
            SwingUtilities.invokeLater(gui::prepareAndStartVisualizerPlayback);

            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0.0;
            }

            while (gui.isAutoPlaying()) {
                try {
                    Thread.sleep(100); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } else {
            SwingUtilities.invokeLater(gui::prepareForSearchBenchmark);

            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final int[] foundMoveIdx = new int[1];
            final long[] searchedStates = new long[1];
            final double[] searchTimeSec = new double[1];

            SwingUtilities.invokeLater(() -> {
                if (!gui.isProcessing()) {
                    latch.countDown();
                    return; 
                }

                long searchStartTime = System.nanoTime();
                benchmarkProgressTimer = new Timer(50, ev -> {
                    long checked = gui.getGame().getEvaluatedNodes();
                    long elapsedNanos = System.nanoTime() - searchStartTime;
                    double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
                    long nps = 0;
                    if (elapsedNanos > 1_000_000) { 
                        nps = Math.round(checked / elapsedSeconds);
                    }
                    String settingsStr = String.format("[Alpha-Beta: %s, Move Ordering: %s]", abActive ? "ON" : "OFF", moActive ? "ON" : "OFF");
                    gui.setAiInfoText(String.format("[%s] %,d states in %.2fs (%,d/sec) %s", testName, checked, elapsedSeconds, nps, settingsStr));
                });
                benchmarkProgressTimer.start();

                SwingWorker<Integer, Void> searchWorker = new SwingWorker<>() {
                    @Override
                    protected Integer doInBackground() {
                        long start = System.nanoTime();
                        int move = gui.getGame().findBestMove(gui.getCurrentPlayer(), depth);
                        long end = System.nanoTime();
                        searchTimeSec[0] = (end - start) / 1_000_000_000.0;
                        return move;
                    }

                    @Override
                    protected void done() {
                        if (benchmarkProgressTimer != null) {
                            benchmarkProgressTimer.stop();
                        }
                        if (!gui.isProcessing()) {
                            latch.countDown();
                            return; 
                        }
                        try {
                            foundMoveIdx[0] = get();
                            searchedStates[0] = gui.getGame().getEvaluatedNodes();
                        } catch (Exception e) {
                            foundMoveIdx[0] = -1;
                        }
                        latch.countDown(); 
                    }
                };
                activeBenchmarkWorker = searchWorker; 
                searchWorker.execute();
            });

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0.0;
            }

            long states = searchedStates[0];
            double durationSec = searchTimeSec[0];
            finalTimeSec = durationSec;
            int bestMoveIdx = foundMoveIdx[0];

            long nps = 0;
            if (durationSec > 0.0001) {
                nps = Math.round(states / durationSec);
            }

            String bestMoveAlg = OthelloBitboard.indexToAlgebraic(bestMoveIdx);
            if (expectedMoves[depth] == null) {
                expectedMoves[depth] = bestMoveAlg; 
            }

            String settingsStr = String.format("[Alpha-Beta: %s, Move Ordering: %s]", abActive ? "ON" : "OFF", moActive ? "ON" : "OFF");

            consoleResultStr = String.format(
                "Test: %s\n" +
                " -> States searched: %,d\n" +
                " -> Settings: %s\n" +
                " -> Time taken: %.4f seconds\n" +
                " -> Throughput: %,d states/sec\n" +
                " -> Move found: %s\n\n",
                testName, states, settingsStr, durationSec, nps, bestMoveAlg
            );

            String htmlResult = String.format(
                "<div style='margin-bottom: 10px; font-family: monospace; font-size: 11px;'>" +
                " <span style='color: %s; font-weight: bold;'>&bull; %s</span><br>" +
                " &nbsp; &rarr; States searched: <b>%,d</b><br>" +
                " &nbsp; &rarr; Settings: <b>%s</b><br>" +
                " &nbsp; &rarr; Time taken: <b>%.4f</b> seconds<br>" +
                " &nbsp; &rarr; Throughput: <b>%,d</b> states/sec<br>" +
                " &nbsp; &rarr; Move found: <b>%s</b>" +
                "</div>",
                colorHex, testName, states, settingsStr, durationSec, nps, bestMoveAlg
            );
            
            summary.append(htmlResult);
            
            SwingUtilities.invokeLater(() -> {
                if (!gui.isProcessing()) return;
                gui.playBenchmarkMove(bestMoveIdx);
            });
        }

        System.out.print(consoleResultStr);
        return finalTimeSec;
    }

    private void appendComparison4(StringBuilder summary, double t_nested, double t_primitive, double t_flat, double t_bitboard) {
        double ratio_prim_to_nested = t_nested / Math.max(t_primitive, 0.000001);
        double ratio_flat_to_prim = t_primitive / Math.max(t_flat, 0.000001);
        double ratio_bitboard_to_flat = t_flat / Math.max(t_bitboard, 0.000001);
        double ratio_bitboard_to_nested = t_nested / Math.max(t_bitboard, 0.000001);

        String comparisonHtml = String.format(
            "<div style='background-color: #F5F7FA; border-left: 4px solid #1976D2; padding: 10px; margin: 10px 0; font-family: monospace; font-size: 11px; color: #2C3E50;'>" +
            " <b>Type Performance Comparison:</b><br>" +
            " &bull; 2D Primitive Values is <b>%.1fx</b> faster than 2D Cell Objects<br>" +
            " &bull; 1D Flat Array is <b>%.1fx</b> faster than 2D Primitive Values<br>" +
            " &bull; Bitboard is <b>%.1fx</b> faster than 1D Flat Array<br>" +
            " &bull; Bitboard is <b>%.1fx</b> faster than 2D Cell Objects" +
            "</div>",
            ratio_prim_to_nested, ratio_flat_to_prim, ratio_bitboard_to_flat, ratio_bitboard_to_nested
        );
        summary.append(comparisonHtml);
    }

    private void appendSeparator(StringBuilder summary) {
        summary.append("<hr style='border: 0; border-top: 1px dashed #CCCCCC; margin: 15px 0;'>");
    }
}