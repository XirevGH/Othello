package src.visualizer;

import java.util.ArrayList;
import java.util.List;

import src.engine.*;

public class VisualizerHistoryGenerator implements OthelloConstants {
    private final OthelloBoard game;
    private final int currentPlayer;

    public VisualizerHistoryGenerator(OthelloBoard game, int currentPlayer) {
        this.game = game;
        this.currentPlayer = currentPlayer;
    }

    public List<VizState> generateBitboardHistory() {
        List<VizState> vizHistory = new ArrayList<>();
        int[][] curHighlights = new int[8][8];
        boolean[][] persistentValids = new boolean[8][8]; 
        boolean[][] persistentInvalids = new boolean[8][8]; 
        boolean[][] checked = new boolean[8][8]; 

        int opponent = (currentPlayer == BLACK) ? WHITE : BLACK;

        for (int d = 0; d < 8; d++) {
            int dr = DR[d];
            int dc = DC[d];
            String dirName = DIR_NAMES[d];

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

            // --- Step 0 ---
            clearArray(curHighlights);
            String[][] step0Texts = new String[8][8];
            String[][] step0Markers = new String[8][8];
            
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (game.getPieceAt(r, c) == currentPlayer) {
                        curHighlights[r][c] = 6; 
                    }
                }
            }

            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    boolean isCandidate = isPotentialBitboardTarget(r, c, currentPlayer, d) || dirInvalids[r][c];
                    if (isCandidate) {
                        curHighlights[r][c] = 1; 
                        if (persistentValids[r][c]) {
                            step0Texts[r][c] = "Valid"; 
                            step0Markers[r][c] = "✓";
                        } else {
                            step0Texts[r][c] = "Evaluating";
                            step0Markers[r][c] = "?"; 
                        }
                    } else {
                        if (persistentValids[r][c]) {
                            curHighlights[r][c] = 3; 
                            step0Texts[r][c] = "Valid";
                            step0Markers[r][c] = "✓";
                        } else if (persistentInvalids[r][c]) {
                            curHighlights[r][c] = 4; 
                            step0Texts[r][c] = "Invalid";
                            step0Markers[r][c] = "X";
                        }
                    }
                }
            }
            
            VizState step0State = new VizState(d, persistentValids, persistentInvalids, curHighlights, 
                    String.format("[Bitboard] Step 0: Identify own pieces and evaluation candidates in direction %s", dirName));
            step0State.setTileTexts(step0Texts);
            step0State.setMarkers(step0Markers);
            vizHistory.add(step0State);

            // --- Step 1 ---
            clearArray(curHighlights);
            String[][] step1Texts = new String[8][8];
            String[][] step1Markers = new String[8][8];
            List<int[]> step1Arrows = new ArrayList<>();
            List<int[]> step1WallHits = new ArrayList<>();

            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (game.getPieceAt(r, c) == currentPlayer) {
                        curHighlights[r][c] = 6; 
                    }
                }
            }

            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    boolean isCandidate = isPotentialBitboardTarget(r, c, currentPlayer, d) || dirInvalids[r][c];
                    if (isCandidate && !dirInvalids[r][c]) {
                        if (persistentValids[r][c]) {
                            curHighlights[r][c] = 1; 
                            step1Texts[r][c] = "Valid"; 
                            step1Markers[r][c] = "✓"; 
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

            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    int prevR = r - dr;
                    int prevC = c - dc;
                    if (prevR >= 0 && prevR < 8 && prevC >= 0 && prevC < 8) {
                        if (game.getPieceAt(prevR, prevC) == currentPlayer) {
                            int destPiece = game.getPieceAt(r, c);
                            if (destPiece == EMPTY) {
                                if (persistentValids[r][c]) {
                                    curHighlights[r][c] = 4; 
                                    step1Texts[r][c] = "Valid"; 
                                    step1Markers[r][c] = "✓"; 
                                } else {
                                    curHighlights[r][c] = 4; 
                                    step1Texts[r][c] = "Invalid";
                                    step1Markers[r][c] = "X"; 
                                }
                                step1Arrows.add(new int[]{prevR, prevC, r, c, 2}); 
                                checked[r][c] = true;
                            } else if (destPiece == opponent) {
                                curHighlights[r][c] = 5; 
                                step1Markers[r][c] = "✓";
                                step1Arrows.add(new int[]{prevR, prevC, r, c, 1}); 
                            } else if (destPiece == currentPlayer) {
                                curHighlights[r][c] = 4; 
                                step1Markers[r][c] = "X";
                                step1Texts[r][c] = "Own Piece"; 
                                step1Arrows.add(new int[]{prevR, prevC, r, c, 2}); 
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

            VizState step1State = new VizState(d, persistentValids, persistentInvalids, curHighlights, String.format("[Bitboard] Step 1: Shift friendly board by 1 position in direction %s", dirName));
            step1State.setTileTexts(step1Texts);
            step1State.setMarkers(step1Markers);
            step1State.arrows = step1Arrows;
            step1State.wallHits = step1WallHits;
            vizHistory.add(step1State);

            // --- Step 2 ---
            clearArray(curHighlights);
            String[][] step2Texts = new String[8][8];
            String[][] step2Markers = new String[8][8];
            List<int[]> step2Arrows = new ArrayList<>();

            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (game.getPieceAt(r, c) == currentPlayer) {
                        curHighlights[r][c] = 6; 
                    }
                }
            }

            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (dirInvalids[r][c]) {
                        if (persistentValids[r][c]) {
                            curHighlights[r][c] = 3; 
                            step2Texts[r][c] = "Valid"; 
                            step2Markers[r][c] = "✓"; 
                        } else {
                            curHighlights[r][c] = 4; 
                            step2Texts[r][c] = "Invalid";
                            step2Markers[r][c] = "X";
                        }
                    } else {
                        boolean isCandidate = isPotentialBitboardTarget(r, c, currentPlayer, d);
                        if (isCandidate) {
                            if (persistentValids[r][c]) {
                                curHighlights[r][c] = 1; 
                                step2Texts[r][c] = "Valid"; 
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
                                curHighlights[opp[0]][opp[1]] = 2; 
                                step2Markers[opp[0]][opp[1]] = String.valueOf(stepIndex++); 
                            }
                            
                            step2Arrows.add(new int[]{firstOpp[0], firstOpp[1], lastOpp[0], lastOpp[1], 1}); 
                        }
                    }
                }
            }

            VizState step2State = new VizState( d, persistentValids, persistentInvalids, curHighlights, String.format("[Bitboard] Step 2: Mask shifted friendly board with opponent pieces in direction %s", dirName));
            step2State.setTileTexts(step2Texts);
            step2State.setMarkers(step2Markers);
            step2State.arrows = step2Arrows;
            vizHistory.add(step2State);

            // --- Step 3 ---
            clearArray(curHighlights);
            String[][] step3Texts = new String[8][8];
            String[][] step3Markers = new String[8][8];
            List<int[]> step3Arrows = new ArrayList<>();
            List<int[]> step3WallHits = new ArrayList<>();

            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (game.getPieceAt(r, c) == currentPlayer) {
                        curHighlights[r][c] = 6; 
                    }
                }
            }

            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (dirInvalids[r][c]) {
                        if (persistentValids[r][c]) {
                            curHighlights[r][c] = 3; 
                            step3Texts[r][c] = "Valid"; 
                            step3Markers[r][c] = "✓"; 
                        } else {
                            curHighlights[r][c] = 4; 
                            step3Texts[r][c] = "Invalid";
                            step3Markers[r][c] = "X";
                        }
                    } else {
                        boolean isCandidate = isPotentialBitboardTarget(r, c, currentPlayer, d);
                        if (isCandidate) {
                            if (persistentValids[r][c]) {
                                curHighlights[r][c] = 1; 
                                step3Texts[r][c] = "Valid"; 
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
                                curHighlights[lastOppR][lastOppC] = 4; 
                                step3WallHits.add(new int[]{r, c, lastOppR, lastOppC, nextR, nextC});
                                step3Texts[lastOppR][lastOppC] = "End of board";
                            } else {
                                int nextPiece = game.getPieceAt(nextR, nextC);
                                if (nextPiece == EMPTY) {
                                    curHighlights[nextR][nextC] = 3; 
                                    persistentValids[nextR][nextC] = true;
                                    step3Markers[nextR][nextC] = "✓";
                                    step3Texts[nextR][nextC] = "Valid";
                                    step3Arrows.add(new int[]{r, c, nextR, nextC, 3}); 
                                    checked[nextR][nextC] = true;
                                } else if (nextPiece == currentPlayer) {
                                    if (persistentValids[nextR][nextC]) {
                                        curHighlights[nextR][nextC] = 4; 
                                        step3Texts[nextR][nextC] = "Valid"; 
                                        step3Markers[nextR][nextC] = "✓"; 
                                    } else {
                                        curHighlights[nextR][nextC] = 4; 
                                        step3Markers[nextR][nextC] = "X";
                                        step3Texts[nextR][nextC] = "Own Piece"; 
                                    }
                                    step3Arrows.add(new int[]{r, c, nextR, nextC, 2}); 
                                }
                            }
                        }
                    }
                }
            }

            VizState step3State = new VizState(d, persistentValids, persistentInvalids, curHighlights, String.format("[Bitboard] Step 3: Shift recursively and mask with empty spaces in direction %s", dirName));
            step3State.setTileTexts(step3Texts);
            step3State.setMarkers(step3Markers);
            step3State.arrows = step3Arrows;
            step3State.wallHits = step3WallHits;
            vizHistory.add(step3State);
        }

        clearArray(curHighlights);
        String[][] finalMarkers = new String[8][8];
        String[][] finalTexts = new String[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (game.getPieceAt(r, c) == EMPTY) {
                    if (persistentValids[r][c]) {
                        curHighlights[r][c] = 3; 
                        finalMarkers[r][c] = "✓";
                        finalTexts[r][c] = "Valid";
                    } else {
                        if (checked[r][c]) {
                            curHighlights[r][c] = 4; 
                            finalTexts[r][c] = "Invalid";
                            finalMarkers[r][c] = "X";
                        }
                    }
                }
            }
        }
        
        VizState finalState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, "Bitboard scan complete! All valid moves are highlighted.");
        finalState.setMarkers(finalMarkers);
        finalState.setTileTexts(finalTexts);
        vizHistory.add(finalState);
        return vizHistory;
    }

    public List<VizState> generateFlatArrayOptimizedHistory() {
        List<VizState> vizHistory = new ArrayList<>();
        int[][] curHighlights = new int[8][8];
        boolean[][] persistentValids = new boolean[8][8];
        boolean[][] persistentInvalids = new boolean[8][8];
        
        boolean[][] processed = new boolean[64][4];
        
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

            clearArray(curHighlights);
            applyOOCandidateHighlights(curHighlights, new String[8][8], new String[8][8], evaluated, persistentValids, persistentInvalids, -1, -1, false);
            curHighlights[r][c] = 2; 
            
            String sweepExplanation;
            if (pieceAtCell == EMPTY) {
                sweepExplanation = String.format("Scanning board... %s is empty. Skipping.", cellName);
            } else if (pieceAtCell == currentPlayer) {
                sweepExplanation = String.format("Scanning board... %s contains Own Piece. Skipping.", cellName);
            } else {
                sweepExplanation = String.format("Scanning board... Found opponent piece at %s! Halting sweep to evaluate axes.", cellName);
            }

            VizState sweepState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, sweepExplanation);
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
                    clearArray(curHighlights);
                    applyOOCandidateHighlights(curHighlights, new String[8][8], new String[8][8], evaluated, persistentValids, persistentInvalids, -1, -1, false);
                    curHighlights[r][c] = 6; 
                    
                    VizState skipState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, 
                            String.format("Axis %s on %s has already been processed. Skipping.", axisName, cellName));
                    String[][] skipTexts = new String[8][8];
                    String[][] skipMarkers = new String[8][8];
                    applyOOCandidateHighlights(curHighlights, skipTexts, skipMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                    
                    curHighlights[r][c] = 6;
                    skipTexts[r][c] = "Skip";
                    skipMarkers[r][c] = AXIS_ARROWS[axis]; 
                    
                    skipState.setTileTexts(skipTexts);
                    skipState.setMarkers(skipMarkers);
                    vizHistory.add(skipState);
                    continue;
                }

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
                    
                    curHighlights[r][c] = 5; 
                    step1Markers[r][c] = "1"; 

                    applyEndpointHighlights(curHighlights, 
                                            termPlusOnBoard, termPlusR, termPlusC, 
                                            termMinusOnBoard, termMinusR, termMinusC, 
                                            persistentValids);

                    int labelIndex = 2;
                    for (int[] p : activePlus) {
                        curHighlights[p[0]][p[1]] = 2; 
                        step1Markers[p[0]][p[1]] = String.valueOf(labelIndex++);
                    }

                    List<int[]> step1Arrows = new ArrayList<>();
                    int pr = r;
                    int pc = c;
                    for (int[] p : activePlus) {
                        step1Arrows.add(new int[]{pr, pc, p[0], p[1], 1});
                        pr = p[0];
                        pc = p[1];
                    }

                    VizState step1State = new VizState(-1, persistentValids, persistentInvalids, curHighlights, 
                            String.format("Axis %s: Tracing in [+] direction (towards %s). Opponent piece %d found.", 
                                    axisName, PLUS_DIR_NAMES[axis], plusPieceCount));
                    step1State.arrows = step1Arrows;
                    step1State.setTileTexts(step1Texts);
                    step1State.setMarkers(step1Markers);
                    
                    applyEndpointTextsAndMarkers(step1State.tileTexts, step1State.markers,
                                                 termPlusOnBoard, termPlusR, termPlusC, false,
                                                 termMinusOnBoard, termMinusR, termMinusC, false,
                                                 persistentValids);
                    vizHistory.add(step1State);

                    currR += dr;
                    currC += dc;
                    plusPieceCount++;
                }
                
                clearArray(curHighlights);
                String[][] termPlusTexts = new String[8][8];
                String[][] termPlusMarkers = new String[8][8];
                applyOOCandidateHighlights(curHighlights, termPlusTexts, termPlusMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                
                curHighlights[r][c] = 5; 
                termPlusMarkers[r][c] = "1";
                
                applyEndpointHighlights(curHighlights, 
                                        termPlusOnBoard, termPlusR, termPlusC, 
                                        termMinusOnBoard, termMinusR, termMinusC, 
                                        persistentValids);

                int labelIdx = 2;
                for (int[] p : scanPlus) {
                    curHighlights[p[0]][p[1]] = 2; 
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
                VizState termPlusState; 
                
                if (termPlusOnBoard) {
                    termPlusArrows.add(new int[]{pr, pc, termPlusR, termPlusC, 1}); 
                    
                    termPlusState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, 
                            String.format("Axis %s: Terminal endpoint in [+] direction is located at %s.", 
                                    axisName, OthelloBitboard.indexToAlgebraic(termPlusR * 8 + termPlusC)));
                } else {
                    termPlusState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, 
                            String.format("Axis %s: Scan hits boundary edge in [+] direction.", axisName));
                            
                    int boundaryR = scanPlus.isEmpty() ? r : scanPlus.get(scanPlus.size() - 1)[0];
                    int boundaryC = scanPlus.isEmpty() ? c : scanPlus.get(scanPlus.size() - 1)[1];
                    termPlusWallHits.add(new int[]{boundaryR, boundaryC, boundaryR, boundaryC, termPlusR, termPlusC});
                    termPlusTexts[boundaryR][boundaryC] = "End of board";
                }
                
                termPlusState.arrows = termPlusArrows;
                termPlusState.wallHits = termPlusWallHits;
                termPlusState.setTileTexts(termPlusTexts);
                termPlusState.setMarkers(termPlusMarkers);
                
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
                    
                    curHighlights[r][c] = 5; 
                    step2Markers[r][c] = "1";

                    applyEndpointHighlights(curHighlights, 
                                            termPlusOnBoard, termPlusR, termPlusC, 
                                            termMinusOnBoard, termMinusR, termMinusC, 
                                            persistentValids);

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
                    ar = r;
                    ac = c;
                    for (int[] p : activeMinus) {
                        step2Arrows.add(new int[]{ar, ac, p[0], p[1], 1});
                        ar = p[0];
                        ac = p[1];
                    }

                    VizState step2State = new VizState(-1, persistentValids, persistentInvalids, curHighlights, 
                            String.format("Axis %s: Tracing in [-] direction (towards %s). Opponent piece %d found.", 
                                    axisName, MINUS_DIR_NAMES[axis], minusPieceCount));
                    step2State.arrows = step2Arrows;
                    step2State.setTileTexts(step2Texts);
                    step2State.setMarkers(step2Markers);
                    
                    applyEndpointTextsAndMarkers(step2State.tileTexts, step2State.markers,
                                                 termPlusOnBoard, termPlusR, termPlusC, true,
                                                 termMinusOnBoard, termMinusR, termMinusC, false,
                                                 persistentValids);
                    vizHistory.add(step2State);

                    currR -= dr;
                    currC -= dc;
                    minusPieceCount++;
                }
                
                termMinusR = currR;
                termMinusC = currC;
                termMinusOnBoard = (termMinusR >= 0 && termMinusR < 8 && termMinusC >= 0 && termMinusC < 8);
                
                clearArray(curHighlights);
                String[][] termMinusTexts = new String[8][8];
                String[][] termMinusMarkers = new String[8][8];
                applyOOCandidateHighlights(curHighlights, termMinusTexts, termMinusMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                
                curHighlights[r][c] = 5; 
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
                ar = r;
                ac = c;
                for (int[] p : scanMinus) {
                    termMinusArrows.add(new int[]{ar, ac, p[0], p[1], 1});
                    ar = p[0];
                    ac = p[1];
                }
                
                List<int[]> termMinusWallHits = new ArrayList<>();
                VizState termMinusState; 
                
                if (termMinusOnBoard) {
                    termMinusArrows.add(new int[]{ar, ac, termMinusR, termMinusC, 1});
                    
                    termMinusState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, 
                            String.format("Axis %s: Other terminal endpoint in [-] direction is located at %s.", 
                                    axisName, OthelloBitboard.indexToAlgebraic(termMinusR * 8 + termMinusC)));
                } else {
                    termMinusState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, 
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

                    boolean match1 = (game.getPieceAt(termPlusR, termPlusC) == EMPTY && game.getPieceAt(termMinusR, termMinusC) == currentPlayer);
                    boolean match2 = (game.getPieceAt(termMinusR, termMinusC) == EMPTY && game.getPieceAt(termPlusR, termPlusC) == currentPlayer);

                    if (match1 || match2) {
                        clearArray(curHighlights);
                        String[][] successTexts = new String[8][8];
                        String[][] successMarkers = new String[8][8];
                        applyOOCandidateHighlights(curHighlights, successTexts, successMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                        
                        List<int[]> successArrows = new ArrayList<>();

                        if (match1) {
                            evaluated[termPlusR][termPlusC] = true;
                            persistentValids[termPlusR][termPlusC] = true;
                            
                            curHighlights[termPlusR][termPlusC] = 3; 
                            curHighlights[termMinusR][termMinusC] = 3; 
                            for (int[] p : scanPlus) curHighlights[p[0]][p[1]] = 3;
                            for (int[] p : scanMinus) curHighlights[p[0]][p[1]] = 3;
                            curHighlights[r][c] = 3;

                            int tempR = termPlusR;
                            int tempC = termPlusC;
                            while (tempR != termMinusR || tempC != termMinusC) {
                                successArrows.add(new int[]{tempR, tempC, tempR - dr, tempC - dc, 3}); 
                                tempR -= dr;
                                tempC -= dc;
                            }

                            successTexts[termPlusR][termPlusC] = "Valid";
                            successMarkers[termPlusR][termPlusC] = "✓";
                            
                            successTexts[termMinusR][termMinusC] = "Own Piece";
                            successMarkers[termMinusR][termMinusC] = null;
                        } else {
                            evaluated[termMinusR][termMinusC] = true;
                            persistentValids[termMinusR][termMinusC] = true;

                            curHighlights[termMinusR][termMinusC] = 3; 
                            curHighlights[termPlusR][termPlusC] = 3; 
                            for (int[] p : scanPlus) curHighlights[p[0]][p[1]] = 3;
                            for (int[] p : scanMinus) curHighlights[p[0]][p[1]] = 3;
                            curHighlights[r][c] = 3;

                            int tempR = termMinusR;
                            int tempC_real = termMinusC;
                            while (tempR != termPlusR || tempC_real != termPlusC) {
                                successArrows.add(new int[]{tempR, tempC_real, tempR + dr, tempC_real + dc, 3}); 
                                tempR += dr;
                                tempC_real += dc;
                            }

                            successTexts[termMinusR][termMinusC] = "Valid";
                            successMarkers[termMinusR][termMinusC] = "✓";
                            
                            successTexts[termPlusR][termPlusC] = "Own Piece";
                            successMarkers[termPlusR][termPlusC] = null;
                        }

                        VizState successState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, 
                                String.format("Axis %s: Sandwiched segment found! Valid move registered.", axisName));
                        successState.arrows = successArrows;
                        successState.setTileTexts(successTexts);
                        successState.setMarkers(successMarkers);
                        vizHistory.add(successState);
                    } else {
                        clearArray(curHighlights);
                        String[][] failTexts = new String[8][8];
                        String[][] failMarkers = new String[8][8];
                        applyOOCandidateHighlights(curHighlights, failTexts, failMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                        
                        List<int[]> failArrows = new ArrayList<>();

                        curHighlights[termPlusR][termPlusC] = 4; 
                        curHighlights[termMinusR][termMinusC] = 4; 
                        for (int[] p : scanPlus) curHighlights[p[0]][p[1]] = 4;
                        for (int[] p : scanMinus) curHighlights[p[0]][p[1]] = 4;
                        curHighlights[r][c] = 4;

                        int tempR = r;
                        int tempC = c;
                        while (tempR != termPlusR || tempC != termPlusC) {
                            failArrows.add(new int[]{tempR, tempC, tempR + dr, tempC + dc, 2}); 
                            tempR += dr;
                            tempC += dc;
                        }
                        tempR = r;
                        tempC = c;
                        while (tempR != termMinusR || tempC != termMinusC) {
                            failArrows.add(new int[]{tempR, tempC, tempR - dr, tempC - dc, 2}); 
                            tempR -= dr;
                            tempC -= dc;
                        }

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

                        VizState failState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, 
                                String.format("Axis %s: Terminals fail to form a valid sandwich.", axisName));
                        failState.arrows = failArrows;
                        failState.setTileTexts(failTexts);
                        failState.setMarkers(failMarkers);
                        vizHistory.add(failState);
                    }
                } else {
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
                            failTexts[termPlusR][termPlusC] = "Own Piece"; 
                        }
                    } else {
                        int boundaryR = scanPlus.isEmpty() ? r : scanPlus.get(scanPlus.size() - 1)[0];
                        int boundaryC = scanPlus.isEmpty() ? c : scanPlus.get(scanPlus.size() - 1)[1];
                        failTexts[boundaryR][boundaryC] = "End of board"; 
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
                            failTexts[termMinusR][termMinusC] = "Own Piece"; 
                        }
                    } else {
                        int boundaryR = scanMinus.isEmpty() ? r : scanMinus.get(scanMinus.size() - 1)[0];
                        int boundaryC = scanMinus.isEmpty() ? c : scanMinus.get(scanMinus.size() - 1)[1];
                        failTexts[boundaryR][boundaryC] = "End of board"; 
                    }

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

                    VizState failState;
                    if (!termPlusOnBoard) {
                        int boundaryR = scanPlus.isEmpty() ? r : scanPlus.get(scanPlus.size() - 1)[0];
                        int boundaryC = scanPlus.isEmpty() ? c : scanPlus.get(scanPlus.size() - 1)[1];
                        failState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, 
                                String.format("Axis %s: Path failed (Wall boundary hit).", axisName));
                        failState.wallHits.add(new int[]{boundaryR, boundaryC, boundaryR, boundaryC, termPlusR, termPlusC});
                    } else if (!termMinusOnBoard) {
                        int boundaryR = scanMinus.isEmpty() ? r : scanMinus.get(scanMinus.size() - 1)[0];
                        int boundaryC = scanMinus.isEmpty() ? c : scanMinus.get(scanMinus.size() - 1)[1];
                        failState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, 
                                String.format("Axis %s: Path failed (Wall boundary hit).", axisName));
                        failState.wallHits.add(new int[]{boundaryR, boundaryC, boundaryR, boundaryC, termMinusR, termMinusC});
                    } else {
                        failState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, 
                                String.format("Axis %s: Path failed (Wall boundary hit).", axisName));
                    }

                    failState.arrows = failArrows;
                    failState.setTileTexts(failTexts);
                    failState.setMarkers(failMarkers);
                    vizHistory.add(failState);
                }
            }
        }

        clearArray(curHighlights);
        String[][] finalMarkers = new String[8][8];
        String[][] finalTexts = new String[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (game.getPieceAt(r, c) == EMPTY) {
                    if (persistentValids[r][c]) {
                        curHighlights[r][c] = 3; 
                        finalMarkers[r][c] = "✓";
                        finalTexts[r][c] = "Valid";
                    }
                }
            }
        }
        
        VizState finalState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, 
                "Optimized Segment Scan complete! All legal moves are highlighted.");
        finalState.setMarkers(finalMarkers);
        finalState.setTileTexts(finalTexts);
        vizHistory.add(finalState);
        return vizHistory;
    }

    public List<VizState> generateOOHistoryFrontier() {
        List<VizState> vizHistory = new ArrayList<>();
        int[][] curHighlights = new int[8][8];
        boolean[][] persistentValids = new boolean[8][8];
        boolean[][] persistentInvalids = new boolean[8][8];
        boolean[][] evaluated = new boolean[8][8];
        boolean[][] checked = new boolean[8][8];

        int opponent = (currentPlayer == BLACK) ? WHITE : BLACK;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String cellName = OthelloBitboard.indexToAlgebraic(r * 8 + c);
                int pieceAtCell = game.getPieceAt(r, c);

                clearArray(curHighlights);
                applyOOCandidateHighlights(curHighlights, new String[8][8], new String[8][8], evaluated, persistentValids, persistentInvalids, -1, -1, false);
                curHighlights[r][c] = 2;

                String sweepExplanation;
                if (pieceAtCell == EMPTY) {
                    sweepExplanation = String.format("Scanning board sequentially: cell %s is empty. Skipping.", cellName);
                } else if (pieceAtCell == currentPlayer) {
                    sweepExplanation = String.format("Scanning board sequentially: cell %s contains own friendly piece. Skipping.", cellName);
                } else {
                    sweepExplanation = String.format("Scanning board sequentially: cell %s contains an OPPONENT piece! Halting sweep to check surrounding neighborhood.", cellName);
                }

                VizState sweepState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, sweepExplanation);
                String[][] sweepTexts = new String[8][8];
                String[][] sweepMarkers = new String[8][8];
                applyOOCandidateHighlights(curHighlights, sweepTexts, sweepMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                curHighlights[r][c] = 2;
                sweepState.setTileTexts(sweepTexts);
                sweepState.setMarkers(sweepMarkers);
                vizHistory.add(sweepState);

                if (pieceAtCell != opponent) continue;

                for (int d = 0; d < 8; d++) {
                    int nr = r + DR[d];
                    int nc = c + DC[d];
                    String dirName = DIR_NAMES[d];

                    if (nr < 0 || nr >= 8 || nc < 0 || nc >= 8) {
                        clearArray(curHighlights);
                        String[][] oobTexts = new String[8][8];
                        String[][] oobMarkers = new String[8][8];
                        applyOOCandidateHighlights(curHighlights, oobTexts, oobMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                        curHighlights[r][c] = 2;

                        VizState oobState = new VizState(d, persistentValids, persistentInvalids, curHighlights,
                                String.format("Probing neighbor %s: Out of bounds. Skipping.", dirName));
                        oobState.wallHits.add(new int[]{r, c, r, c, nr, nc});
                        oobState.setTileTexts(oobTexts);
                        oobState.setMarkers(oobMarkers);
                        vizHistory.add(oobState);
                        continue;
                    }

                    int neighborPiece = game.getPieceAt(nr, nc);
                    String neighborCellName = OthelloBitboard.indexToAlgebraic(nr * 8 + nc);

                    if (neighborPiece != EMPTY) {
                        clearArray(curHighlights);
                        String[][] occupiedTexts = new String[8][8];
                        String[][] occupiedMarkers = new String[8][8];
                        applyOOCandidateHighlights(curHighlights, occupiedTexts, occupiedMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                        curHighlights[r][c] = 2;
                        curHighlights[nr][nc] = 4;
                        occupiedTexts[nr][nc] = (neighborPiece == currentPlayer) ? "Own Piece" : "Opponent";
                        occupiedMarkers[nr][nc] = "X";

                        String pieceType = (neighborPiece == currentPlayer) ? "friendly" : "opponent";
                        VizState occupiedState = new VizState(d, persistentValids, persistentInvalids, curHighlights,
                                String.format("Probing neighbor %s (%s): Occupied by %s piece. Skipping.", dirName, neighborCellName, pieceType));
                        occupiedState.arrows.add(new int[]{r, c, nr, nc, 2});
                        occupiedState.setTileTexts(occupiedTexts);
                        occupiedState.setMarkers(occupiedMarkers);
                        vizHistory.add(occupiedState);
                        continue;
                    }

                    if (checked[nr][nc]) {
                        clearArray(curHighlights);
                        String[][] dupeTexts = new String[8][8];
                        String[][] dupeMarkers = new String[8][8];
                        applyOOCandidateHighlights(curHighlights, dupeTexts, dupeMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                        curHighlights[r][c] = 2;
                        curHighlights[nr][nc] = 6;
                        dupeTexts[nr][nc] = "Skip";
                        dupeMarkers[nr][nc] = null;

                        VizState dupeState = new VizState(d, persistentValids, persistentInvalids, curHighlights,
                                String.format("Probing neighbor %s (%s): Empty, but already processed. Skipping.", dirName, neighborCellName));
                        dupeState.arrows.add(new int[]{r, c, nr, nc, 2});
                        dupeState.setTileTexts(dupeTexts);
                        dupeState.setMarkers(dupeMarkers);
                        vizHistory.add(dupeState);
                        continue;
                    }

                    checked[nr][nc] = true;
                    String posName = OthelloBitboard.indexToAlgebraic(nr * 8 + nc);

                    clearArray(curHighlights);
                    String[][] foundTexts = new String[8][8];
                    String[][] foundMarkers = new String[8][8];
                    applyOOCandidateHighlights(curHighlights, foundTexts, foundMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                    curHighlights[r][c] = 2;
                    curHighlights[nr][nc] = 1;
                    foundTexts[nr][nc] = "Evaluating";
                    foundMarkers[nr][nc] = "?";

                    VizState foundCandidateState = new VizState(d, persistentValids, persistentInvalids, curHighlights,
                            String.format("Probing neighbor %s (%s): Empty space found! Initiating directional validations.", dirName, posName));
                    foundCandidateState.arrows.add(new int[]{r, c, nr, nc, 1});
                    foundCandidateState.setTileTexts(foundTexts);
                    foundCandidateState.setMarkers(foundMarkers);
                    vizHistory.add(foundCandidateState);

                    evaluateRayCandidate(nr, nc, posName, persistentValids, persistentInvalids, evaluated, opponent, vizHistory);
                }
            }
        }

        finalizeOOHistory(persistentValids, persistentInvalids, "OO scan complete! All adjacent empty neighbors evaluated.", vizHistory);
        return vizHistory;
    }

    public List<VizState> generateOOHistoryPrimitive() {
        List<VizState> vizHistory = new ArrayList<>();
        int[][] curHighlights = new int[8][8];
        boolean[][] persistentValids = new boolean[8][8];
        boolean[][] persistentInvalids = new boolean[8][8];
        boolean[][] evaluated = new boolean[8][8];

        int opponent = (currentPlayer == BLACK) ? WHITE : BLACK;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String cellName = OthelloBitboard.indexToAlgebraic(r * 8 + c);
                int pieceAtCell = game.getPieceAt(r, c);

                clearArray(curHighlights);
                applyOOCandidateHighlights(curHighlights, new String[8][8], new String[8][8], evaluated, persistentValids, persistentInvalids, -1, -1, false);
                curHighlights[r][c] = 2; // Yellow active sweep cursor

                String sweepExplanation;
                if (pieceAtCell == EMPTY) {
                    sweepExplanation = String.format("Scanning board... %s is empty. Evaluating in 8 directions.", cellName);
                } else if (pieceAtCell == currentPlayer) {
                    sweepExplanation = String.format("Scanning board... %s contains Own Piece. Skipping.", cellName);
                } else {
                    sweepExplanation = String.format("Scanning board... %s contains Opponent Piece. Skipping.", cellName);
                }

                VizState sweepState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, sweepExplanation);
                String[][] sweepTexts = new String[8][8];
                String[][] sweepMarkers = new String[8][8];
                applyOOCandidateHighlights(curHighlights, sweepTexts, sweepMarkers, evaluated, persistentValids, persistentInvalids, -1, -1, false);
                curHighlights[r][c] = 2;
                sweepState.setTileTexts(sweepTexts);
                sweepState.setMarkers(sweepMarkers);
                vizHistory.add(sweepState);

                if (pieceAtCell != EMPTY) continue;

                clearArray(curHighlights);
                String[][] currentSquareTexts = new String[8][8];
                String[][] currentSquareMarkers = new String[8][8];
                applyOOCandidateHighlights(curHighlights, currentSquareTexts, currentSquareMarkers, evaluated, 
                                           persistentValids, persistentInvalids, r, c, true);

                VizState neighborState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, "Evaluating candidate " + cellName);
                neighborState.setTileTexts(currentSquareTexts);
                neighborState.setMarkers(currentSquareMarkers);
                vizHistory.add(neighborState);

                evaluateRayCandidate(r, c, cellName, persistentValids, persistentInvalids, evaluated, opponent, vizHistory);
            }
        }

        finalizeOOHistory(persistentValids, persistentInvalids, "Primitive Sweep complete! All legal moves are highlighted.", vizHistory);
        return vizHistory;
    }

    private boolean evaluateRayCandidate(int targetR, int targetC, String candidateName, 
                                         boolean[][] persistentValids, boolean[][] persistentInvalids, 
                                         boolean[][] evaluated, int opponent, List<VizState> vizHistory) {
        int[][] curHighlights = new int[8][8];
        boolean cellIsIndeedValid = false;

        boolean[][] currentSpaceInvalids = new boolean[8][8];
        boolean[][] currentSpaceFailedOpponents = new boolean[8][8];
        List<int[]> currentSquareArrows = new ArrayList<>();
        String[][] currentSquareMarkers = new String[8][8];
        String[][] currentSquareTexts = new String[8][8];

        for (int scanDir = 0; scanDir < 8; scanDir++) {
            if (cellIsIndeedValid) break;

            int sdr = DR[scanDir];
            int sdc = DC[scanDir];
            String scanDirName = DIR_NAMES[scanDir];

            int currR = targetR + sdr;
            int currC = targetC + sdc;
            int step = 1;

            if (currR >= 0 && currR < 8 && currC >= 0 && currC < 8) {
                int targetPiece = game.getPieceAt(currR, currC);
                if (targetPiece == EMPTY) {
                    boolean isAlreadyValid = persistentValids[currR][currC];
                    currentSpaceInvalids[currR][currC] = true;
                    currentSquareArrows.add(new int[]{targetR, targetC, currR, currC, 2});

                    currentSquareMarkers[currR][currC] = isAlreadyValid ? "✓" : "X";
                    currentSquareTexts[currR][currC] = isAlreadyValid ? "Valid" : "Invalid";

                    clearArray(curHighlights);
                    applyOOCandidateHighlights(curHighlights, currentSquareTexts, currentSquareMarkers, evaluated, 
                                               persistentValids, persistentInvalids, targetR, targetC, true);
                    applyTransientOverlay(curHighlights, currentSquareMarkers, currentSquareTexts, 
                                          currentSpaceInvalids, currentSpaceFailedOpponents,
                                          currentSquareMarkers, currentSquareTexts,
                                          evaluated, persistentValids, persistentInvalids);

                    String failMsg = isAlreadyValid 
                        ? String.format("[%s] Path failed (Adjacent cell %s is an empty valid position).", scanDirName, OthelloBitboard.indexToAlgebraic(currR * 8 + currC))
                        : String.format("[%s] Path failed (Adjacent cell %s is empty).", scanDirName, OthelloBitboard.indexToAlgebraic(currR * 8 + currC));

                    VizState state = new VizState(scanDir, persistentValids, persistentInvalids, curHighlights, failMsg);
                    copyStringArray(currentSquareMarkers, state.markers);
                    copyStringArray(currentSquareTexts, state.tileTexts);
                    applyTransientOverlay(curHighlights, state.markers, state.tileTexts, 
                                          currentSpaceInvalids, currentSpaceFailedOpponents,
                                          currentSquareMarkers, currentSquareTexts,
                                          evaluated, persistentValids, persistentInvalids);

                    state.tileTexts[targetR][targetC] = "Evaluating";
                    state.markers[targetR][targetC] = "?";
                    state.arrows.addAll(cloneArrows(currentSquareArrows));
                    vizHistory.add(state);
                    continue;
                } else if (targetPiece == currentPlayer) {
                    currentSpaceInvalids[currR][currC] = true;
                    currentSquareMarkers[currR][currC] = "X";
                    currentSquareTexts[currR][currC] = "Own Piece";
                    currentSquareArrows.add(new int[]{targetR, targetC, currR, currC, 2});

                    clearArray(curHighlights);
                    applyOOCandidateHighlights(curHighlights, currentSquareTexts, currentSquareMarkers, evaluated, 
                                               persistentValids, persistentInvalids, targetR, targetC, true);
                    applyTransientOverlay(curHighlights, currentSquareMarkers, currentSquareTexts, 
                                          currentSpaceInvalids, currentSpaceFailedOpponents,
                                          currentSquareMarkers, currentSquareTexts,
                                          evaluated, persistentValids, persistentInvalids);

                    VizState state = new VizState(scanDir, persistentValids, persistentInvalids, curHighlights, 
                            String.format("[%s] Friendly piece at %s is directly adjacent. No opponent pieces to flip.", 
                                    scanDirName, OthelloBitboard.indexToAlgebraic(currR * 8 + currC)));
                    copyStringArray(currentSquareMarkers, state.markers);
                    copyStringArray(currentSquareTexts, state.tileTexts);
                    applyTransientOverlay(curHighlights, state.markers, state.tileTexts, 
                                          currentSpaceInvalids, currentSpaceFailedOpponents,
                                          currentSquareMarkers, currentSquareTexts,
                                          evaluated, persistentValids, persistentInvalids);

                    state.tileTexts[targetR][targetC] = "Evaluating";
                    state.markers[targetR][targetC] = "?";
                    state.arrows.addAll(cloneArrows(currentSquareArrows));
                    vizHistory.add(state);
                    continue;
                }
            }

            List<int[]> scannedOpponents = new ArrayList<>();
            List<int[]> currentDirArrows = new ArrayList<>();

            while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && game.getPieceAt(currR, currC) == opponent) {
                scannedOpponents.add(new int[]{currR, currC});

                clearArray(curHighlights);
                applyOOCandidateHighlights(curHighlights, currentSquareTexts, currentSquareMarkers, evaluated, 
                                           persistentValids, persistentInvalids, targetR, targetC, true);
                applyTransientOverlay(curHighlights, currentSquareMarkers, currentSquareTexts, 
                                      currentSpaceInvalids, currentSpaceFailedOpponents,
                                      currentSquareMarkers, currentSquareTexts,
                                      evaluated, persistentValids, persistentInvalids);

                for (int[] p : scannedOpponents) curHighlights[p[0]][p[1]] = 2;

                VizState state = new VizState(scanDir, persistentValids, persistentInvalids, curHighlights, 
                        String.format("[%s] Opponent piece detected at %s", scanDirName, OthelloBitboard.indexToAlgebraic(currR * 8 + currC)));
                copyStringArray(currentSquareMarkers, state.markers);
                copyStringArray(currentSquareTexts, state.tileTexts);
                applyTransientOverlay(curHighlights, state.markers, state.tileTexts, 
                                      currentSpaceInvalids, currentSpaceFailedOpponents,
                                      currentSquareMarkers, currentSquareTexts,
                                      evaluated, persistentValids, persistentInvalids);

                state.tileTexts[targetR][targetC] = "Evaluating";
                state.markers[targetR][targetC] = "?";

                for (int k = 0; k < scannedOpponents.size(); k++) {
                    int[] p = scannedOpponents.get(k);
                    state.markers[p[0]][p[1]] = String.valueOf(k + 1);
                }

                int prevR = currR - sdr;
                int prevC = currC - sdc;
                currentDirArrows.add(new int[]{prevR, prevC, currR, currC, 1});

                state.arrows.addAll(cloneArrows(currentSquareArrows));
                state.arrows.addAll(cloneArrows(currentDirArrows));
                vizHistory.add(state);

                currR += sdr;
                currC += sdc;
                step++;
            }

            if (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && game.getPieceAt(currR, currC) == currentPlayer) {
                if (step > 1) {
                    cellIsIndeedValid = true;
                    evaluated[targetR][targetC] = true;
                    persistentValids[targetR][targetC] = true;

                    clearArray(curHighlights);
                    applyOOCandidateHighlights(curHighlights, currentSquareTexts, currentSquareMarkers, evaluated, 
                                               persistentValids, persistentInvalids, targetR, targetC, false);
                    applyTransientOverlay(curHighlights, currentSquareMarkers, currentSquareTexts, 
                                          currentSpaceInvalids, currentSpaceFailedOpponents,
                                          currentSquareMarkers, currentSquareTexts,
                                          evaluated, persistentValids, persistentInvalids);

                    for (int[] p : scannedOpponents) curHighlights[p[0]][p[1]] = 3;
                    curHighlights[currR][currC] = 3;

                    VizState state = new VizState(scanDir, persistentValids, persistentInvalids, curHighlights, 
                            String.format("[%s] Friendly anchor found at %s! Valid line confirmed.", 
                                    scanDirName, OthelloBitboard.indexToAlgebraic(currR * 8 + currC)));
                    state.markers[targetR][targetC] = "✓";
                    state.tileTexts[targetR][targetC] = "Valid";

                    for (int[] arrow : currentDirArrows) arrow[4] = 3;
                    currentSquareArrows.addAll(currentDirArrows);

                    int tempR = targetR;
                    int tempC = targetC;
                    while (tempR != currR || tempC != currC) {
                        currentSquareArrows.add(new int[]{tempR, tempC, tempR + sdr, tempC + sdc, 3});
                        tempR += sdr;
                        tempC += sdc;
                    }

                    currentSquareMarkers[targetR][targetC] = "✓";
                    currentSquareTexts[targetR][targetC] = "Valid";

                    copyStringArray(currentSquareMarkers, state.markers);
                    copyStringArray(currentSquareTexts, state.tileTexts);
                    applyTransientOverlay(curHighlights, state.markers, state.tileTexts, 
                                          currentSpaceInvalids, currentSpaceFailedOpponents,
                                          currentSquareMarkers, currentSquareTexts,
                                          evaluated, persistentValids, persistentInvalids);

                    state.arrows.addAll(cloneArrows(currentSquareArrows));
                    vizHistory.add(state);
                }
            } else if (!scannedOpponents.isEmpty()) {
                boolean isAlreadyValid = (currR >= 0 && currR < 8 && currC >= 0 && currC < 8) && persistentValids[currR][currC];
                currentSpaceInvalids[currR][currC] = true;
                currentSquareMarkers[currR][currC] = isAlreadyValid ? "✓" : "X";
                currentSquareTexts[currR][currC] = isAlreadyValid ? "Valid" : "Invalid";

                int tempR = targetR;
                int tempC = targetC;
                while (tempR != currR || tempC != currC) {
                    currentSquareArrows.add(new int[]{tempR, tempC, tempR + sdr, tempC + sdc, 2});
                    tempR += sdr;
                    tempC += sdc;
                }

                for (int[] p : scannedOpponents) currentSpaceFailedOpponents[p[0]][p[1]] = true;

                clearArray(curHighlights);
                applyOOCandidateHighlights(curHighlights, currentSquareTexts, currentSquareMarkers, evaluated, 
                                           persistentValids, persistentInvalids, targetR, targetC, true);
                applyTransientOverlay(curHighlights, currentSquareMarkers, currentSquareTexts, 
                                      currentSpaceInvalids, currentSpaceFailedOpponents,
                                      currentSquareMarkers, currentSquareTexts,
                                      evaluated, persistentValids, persistentInvalids);

                boolean hitWall = (currR < 0 || currR >= 8 || currC < 0 || currC >= 8);
                for (int[] arrow : currentDirArrows) arrow[4] = 2;
                currentSquareArrows.addAll(currentDirArrows);

                if (hitWall) {
                    int failedTargetR = currR - sdr;
                    int failedTargetC = currC - sdc;

                    VizState state = new VizState(scanDir, persistentValids, persistentInvalids, curHighlights, 
                            String.format("[%s] Path failed (Boundary wall reached).", scanDirName));
                    currentSquareMarkers[failedTargetR][failedTargetC] = "X";
                    currentSquareTexts[failedTargetR][failedTargetC] = "End of board";

                    int tr = targetR;
                    int tc = targetC;
                    while (tr != failedTargetR || tc != failedTargetC) {
                        currentSquareArrows.add(new int[]{tr, tc, tr + sdr, tc + sdc, 2});
                        tr += sdr;
                        tc += sdc;
                    }

                    copyStringArray(currentSquareMarkers, state.markers);
                    copyStringArray(currentSquareTexts, state.tileTexts);
                    applyTransientOverlay(curHighlights, state.markers, state.tileTexts, 
                                          currentSpaceInvalids, currentSpaceFailedOpponents,
                                          currentSquareMarkers, currentSquareTexts,
                                          evaluated, persistentValids, persistentInvalids);

                    state.tileTexts[targetR][targetC] = "Evaluating";
                    state.markers[targetR][targetC] = "?";
                    state.arrows.addAll(cloneArrows(currentSquareArrows));
                    vizHistory.add(state);
                } else {
                    String failMsg = isAlreadyValid 
                        ? String.format("[%s] Path failed (Hit already valid empty square at %s).", scanDirName, OthelloBitboard.indexToAlgebraic(currR * 8 + currC))
                        : String.format("[%s] Path failed (Empty square hit at %s).", scanDirName, OthelloBitboard.indexToAlgebraic(currR * 8 + currC));

                    VizState state = new VizState(scanDir, persistentValids, persistentInvalids, curHighlights, failMsg);
                    copyStringArray(currentSquareMarkers, state.markers);
                    copyStringArray(currentSquareTexts, state.tileTexts);
                    applyTransientOverlay(curHighlights, state.markers, state.tileTexts, 
                                          currentSpaceInvalids, currentSpaceFailedOpponents,
                                          currentSquareMarkers, currentSquareTexts,
                                          evaluated, persistentValids, persistentInvalids);

                    state.tileTexts[targetR][targetC] = "Evaluating";
                    state.markers[targetR][targetC] = "?";
                    state.arrows.addAll(cloneArrows(currentSquareArrows));
                    vizHistory.add(state);
                }
            }
        }

        if (!cellIsIndeedValid) {
            evaluated[targetR][targetC] = true;
            persistentInvalids[targetR][targetC] = true;

            clearArray(curHighlights);
            String[][] cleanMarkers = new String[8][8];
            String[][] cleanTexts = new String[8][8];
            applyOOCandidateHighlights(curHighlights, cleanTexts, cleanMarkers, evaluated, 
                                       persistentValids, persistentInvalids, targetR, targetC, false);

            VizState invalidState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, 
                    String.format("Square %s evaluated: No valid moves possible.", candidateName));
            copyStringArray(cleanMarkers, invalidState.markers);
            copyStringArray(cleanTexts, invalidState.tileTexts);
            vizHistory.add(invalidState);
        }

        return cellIsIndeedValid;
    }

    private void applyTransientOverlay(int[][] curHighlights, String[][] markers, String[][] tileTexts, 
                                        boolean[][] currentSpaceInvalids, boolean[][] currentSpaceFailedOpponents,
                                        String[][] currentSquareMarkers, String[][] currentSquareTexts,
                                        boolean[][] evaluated, boolean[][] persistentValids, boolean[][] persistentInvalids) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (currentSpaceInvalids[i][j] || currentSpaceFailedOpponents[i][j]) {
                    curHighlights[i][j] = 4; 
                }
            }
        }

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (currentSpaceInvalids[i][j]) {
                    if (evaluated[i][j]) {
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

    private void applyEndpointHighlights(int[][] curHighlights, 
                                         boolean termPlusOnBoard, int termPlusR, int termPlusC,
                                         boolean termMinusOnBoard, int termMinusR, int termMinusC,
                                         boolean[][] persistentValids) {
        if (termPlusOnBoard) {
            int type = game.getPieceAt(termPlusR, termPlusC);
            if ((type == EMPTY || type == currentPlayer) && !persistentValids[termPlusR][termPlusC]) {
                curHighlights[termPlusR][termPlusC] = 1; 
            }
        }
        if (termMinusOnBoard) {
            int type = game.getPieceAt(termMinusR, termMinusC);
            if ((type == EMPTY || type == currentPlayer) && !persistentValids[termMinusR][termMinusC]) {
                curHighlights[termMinusR][termMinusC] = 1; 
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

    private void applyOOCandidateHighlights(int[][] curHighlights, String[][] tileTexts, String[][] markers,
                                            boolean[][] evaluated,
                                            boolean[][] persistentValids, boolean[][] persistentInvalids,
                                            int nr, int nc, boolean isCurrentlyEvaluating) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (r == nr && c == nc && isCurrentlyEvaluating) {
                    curHighlights[r][c] = 1; 
                    tileTexts[r][c] = "Evaluating";
                    markers[r][c] = "?"; 
                } else if (evaluated[r][c]) {
                    if (persistentValids[r][c]) {
                        curHighlights[r][c] = 3; 
                        tileTexts[r][c] = "Valid";
                        markers[r][c] = "✓";
                    } else if (persistentInvalids[r][c]) {
                        curHighlights[r][c] = 4; 
                        tileTexts[r][c] = "Invalid";
                        markers[r][c] = "X";
                    }
                } else {
                    curHighlights[r][c] = 0; 
                    if (tileTexts[r][c] == null || !isPreservedTileText(tileTexts[r][c])) {
                        tileTexts[r][c] = "";
                    }
                    if (markers[r][c] != null && (markers[r][c].equals("X") || markers[r][c].equals("✓") || markers[r][c].equals("?"))) {
                    } else {
                        markers[r][c] = null;
                    }
                }
            }
        }
    }

    private void finalizeOOHistory(boolean[][] persistentValids, boolean[][] persistentInvalids, String completionMessage, List<VizState> vizHistory) {
        int[][] curHighlights = new int[8][8];
        String[][] finalMarkers = new String[8][8];
        String[][] finalTexts = new String[8][8];

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (game.getPieceAt(r, c) == EMPTY) {
                    if (persistentValids[r][c]) {
                        curHighlights[r][c] = 3; 
                        finalMarkers[r][c] = "✓";
                        finalTexts[r][c] = "Valid";
                    } else if (persistentInvalids[r][c]) {
                        curHighlights[r][c] = 4; 
                        finalTexts[r][c] = "Invalid";
                        finalMarkers[r][c] = "X";
                    }
                }
            }
        }

        VizState finalState = new VizState(-1, persistentValids, persistentInvalids, curHighlights, completionMessage);
        finalState.setMarkers(finalMarkers);
        finalState.setTileTexts(finalTexts);
        vizHistory.add(finalState);
    }

    private boolean isPreservedTileText(String text) {
        return text.equals("Own Piece") || text.equals("Empty") || 
            text.equals("Valid") || text.equals("Invalid") || 
            text.equals("End of board");
    }

    private boolean isPotentialBitboardTarget(int r, int c, int player, int dirIndex) {
        if (game.getPieceAt(r, c) != EMPTY) return false; 
        
        int dr = DR[dirIndex];
        int dc = DC[dirIndex];
        int currR = r - dr;
        int currC = c - dc;
        
        while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8) {
            int piece = game.getPieceAt(currR, currC);
            if (piece == EMPTY) {
                return false; 
            }
            if (piece == player) {
                return true;
            }
            currR -= dr;
            currC -= dc;
        }
        return false;
    }

    private void copyStringArray(String[][] source, String[][] dest) {
        for (int i = 0; i < 8; i++) {
            System.arraycopy(source[i], 0, dest[i], 0, 8);
        }
    }

    private List<int[]> cloneArrows(List<int[]> source) {
        List<int[]> clone = new ArrayList<>();
        for (int[] a : source) {
            clone.add(new int[]{a[0], a[1], a[2], a[3], a[4]});
        }
        return clone;
    }

    private void clearArray(int[][] arr) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                arr[i][j] = 0;
            }
        }
    }
}