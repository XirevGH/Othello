package Algoritmdesigntekniker;

import java.util.ArrayList;
import java.util.List;

public class OthelloFlatArray implements OthelloBoard {
    public static final int BLACK = 1;
    public static final int WHITE = 2;
    public static final int EMPTY = 0;

    private static final int[] STATIC_WEIGHTS = {
            4, -3, 2, 2, 2, 2, -3, 4,
            -3, -4, -1, -1, -1, -1, -4, -3,
            2, -1, 1, 0, 0, 1, -1, 2,
            2, -1, 0, 1, 1, 0, -1, 2,
            2, -1, 0, 1, 1, 0, -1, 2,
            2, -1, 1, 0, 0, 1, -1, 2,
            -3, -4, -1, -1, -1, -1, -4, -3,
            4, -3, 2, 2, 2, 2, -3, 4
    };

    private final int[] board = new int[64];
    private boolean useAlphaBeta = true;
    private boolean useMoveOrdering = true; 

    private static class NodeCounter {
        long count = 0;
        long evaluations = 0;
    }
    private final NodeCounter nodeCounter;

    private final UndoState[] undoStack = new UndoState[64];
    private final int[][] moveStack = new int[64][64]; 
    private final int[] moveCountStack = new int[64];
    
    // Stack-allocated arrays to track processed axes and duplicate moves (Optimized without bitwise math)
    private final boolean[][][] processedStack = new boolean[64][64][4];
    private final boolean[][] addedStack = new boolean[64][64];

    public OthelloFlatArray() {
        this.nodeCounter = new NodeCounter();
        
        for (int i = 0; i < 64; i++) {
            undoStack[i] = new UndoState();
        }
        
        board[3 * 8 + 4] = BLACK; 
        board[4 * 8 + 3] = BLACK; 
        board[3 * 8 + 3] = WHITE; 
        board[4 * 8 + 4] = WHITE; 
    }

    private OthelloFlatArray(NodeCounter counter) {
        this.nodeCounter = counter;
        for (int i = 0; i < 64; i++) {
            undoStack[i] = new UndoState();
        }
    }

    @Override
    public void setUseMoveOrdering(boolean useMoveOrdering) {
        this.useMoveOrdering = useMoveOrdering;
    }
    
    @Override
    public long getEvaluationCount() {
        return nodeCounter.evaluations;
    }

    @Override
    public void setUseAlphaBeta(boolean useAlphaBeta) {
        this.useAlphaBeta = useAlphaBeta;
    }

    @Override
    public long getEvaluatedNodes() {
        return nodeCounter.count;
    }

    @Override
    public int getPieceAt(int r, int c) {
        return board[r * 8 + c];
    }

    @Override
    public void setPieceAt(int r, int c, int piece) {
        board[r * 8 + c] = piece;
    }

    @Override
    public int countDiscs(int player) {
        int count = 0;
        for (int i = 0; i < 64; i++) {
            if (board[i] == player) count++;
        }
        return count;
    }

    @Override
    public boolean isValidMove(int r, int c, int player) {
        // Fallback for isolated legal checks
        int index = r * 8 + c;
        if (board[index] != EMPTY) return false;
        
        int opponent = (player == BLACK) ? WHITE : BLACK;
        int[] drAxes = {0, 1, 1, 1};
        int[] dcAxes = {1, 0, 1, -1};
        
        for (int axis = 0; axis < 4; axis++) {
            int dr = drAxes[axis];
            int dc = dcAxes[axis];
            
            // Check + Direction
            int stepPlus = 0;
            int currR = r + dr;
            int currC = c + dc;
            while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && board[currR * 8 + currC] == opponent) {
                stepPlus++;
                currR += dr;
                currC += dc;
            }
            boolean foundPlayerPlus = (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && board[currR * 8 + currC] == player);
            if (stepPlus > 0 && foundPlayerPlus) return true;

            // Check - Direction
            int stepMinus = 0;
            currR = r - dr;
            currC = c - dc;
            while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && board[currR * 8 + currC] == opponent) {
                stepMinus++;
                currR -= dr;
                currC -= dc;
            }
            boolean foundPlayerMinus = (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && board[currR * 8 + currC] == player);
            if (stepMinus > 0 && foundPlayerMinus) return true;
        }
        return false;
    }

    @Override
    public List<int[]> getValidMoves(int player) {
        List<int[]> moves = new ArrayList<>();
        int opponent = (player == BLACK) ? WHITE : BLACK;
        
        boolean[][] processed = new boolean[64][4];
        boolean[] added = new boolean[64];

        int[] drAxes = {0, 1, 1, 1};
        int[] dcAxes = {1, 0, 1, -1};

        for (int i = 0; i < 64; i++) {
            if (board[i] == opponent) {
                int r = i / 8;
                int c = i % 8;

                for (int axis = 0; axis < 4; axis++) {
                    if (processed[i][axis]) continue;

                    int dr = drAxes[axis];
                    int dc = dcAxes[axis];

                    // Scan + Direction
                    int currR = r + dr;
                    int currC = c + dc;
                    while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && board[currR * 8 + currC] == opponent) {
                        processed[currR * 8 + currC][axis] = true;
                        currR += dr;
                        currC += dc;
                    }
                    int termPlusR = currR;
                    int termPlusC = currC;

                    // Scan - Direction
                    currR = r - dr;
                    currC = c - dc;
                    while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && board[currR * 8 + currC] == opponent) {
                        processed[currR * 8 + currC][axis] = true;
                        currR -= dr;
                        currC -= dc;
                    }
                    int termMinusR = currR;
                    int termMinusC = currC;

                    processed[i][axis] = true;

                    boolean termPlusOnBoard = (termPlusR >= 0 && termPlusR < 8 && termPlusC >= 0 && termPlusC < 8);
                    boolean termMinusOnBoard = (termMinusR >= 0 && termMinusR < 8 && termMinusC >= 0 && termMinusC < 8);

                    if (termPlusOnBoard && termMinusOnBoard) {
                        int plusIdx = termPlusR * 8 + termPlusC;
                        int minusIdx = termMinusR * 8 + termMinusC;

                        if (board[plusIdx] == EMPTY && board[minusIdx] == player) {
                            if (!added[plusIdx]) {
                                added[plusIdx] = true;
                                moves.add(new int[]{termPlusR, termPlusC});
                            }
                        }
                        if (board[minusIdx] == EMPTY && board[plusIdx] == player) {
                            if (!added[minusIdx]) {
                                added[minusIdx] = true;
                                moves.add(new int[]{termMinusR, termMinusC});
                            }
                        }
                    }
                }
            }
        }
        return moves;
    }

    private void generateMoves1D(int player, int ply) {
        int count = 0;
        int opponent = (player == BLACK) ? WHITE : BLACK;

        for (int i = 0; i < 64; i++) {
            processedStack[ply][i][0] = false;
            processedStack[ply][i][1] = false;
            processedStack[ply][i][2] = false;
            processedStack[ply][i][3] = false;
            addedStack[ply][i] = false;
        }

        int[] drAxes = {0, 1, 1, 1};
        int[] dcAxes = {1, 0, 1, -1};

        for (int i = 0; i < 64; i++) {
            if (board[i] == opponent) {
                int r = i / 8;
                int c = i % 8;

                for (int axis = 0; axis < 4; axis++) {
                    if (processedStack[ply][i][axis]) continue;

                    int dr = drAxes[axis];
                    int dc = dcAxes[axis];

                    // Scan + Direction
                    int currR = r + dr;
                    int currC = c + dc;
                    while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && board[currR * 8 + currC] == opponent) {
                        processedStack[ply][currR * 8 + currC][axis] = true;
                        currR += dr;
                        currC += dc;
                    }
                    int termPlusR = currR;
                    int termPlusC = currC;

                    // Scan - Direction
                    currR = r - dr;
                    currC = c - dc;
                    while (currR >= 0 && currR < 8 && currC >= 0 && currC < 8 && board[currR * 8 + currC] == opponent) {
                        processedStack[ply][currR * 8 + currC][axis] = true;
                        currR -= dr;
                        currC -= dc;
                    }
                    int termMinusR = currR;
                    int termMinusC = currC;

                    processedStack[ply][i][axis] = true;

                    boolean termPlusOnBoard = (termPlusR >= 0 && termPlusR < 8 && termPlusC >= 0 && termPlusC < 8);
                    boolean termMinusOnBoard = (termMinusR >= 0 && termMinusR < 8 && termMinusC >= 0 && termMinusC < 8);

                    if (termPlusOnBoard && termMinusOnBoard) {
                        int plusIdx = termPlusR * 8 + termPlusC;
                        int minusIdx = termMinusR * 8 + termMinusC;

                        if (board[plusIdx] == EMPTY && board[minusIdx] == player) {
                            if (!addedStack[ply][plusIdx]) {
                                addedStack[ply][plusIdx] = true;
                                moveStack[ply][count++] = plusIdx;
                            }
                        }
                        if (board[minusIdx] == EMPTY && board[plusIdx] == player) {
                            if (!addedStack[ply][minusIdx]) {
                                addedStack[ply][minusIdx] = true;
                                moveStack[ply][count++] = minusIdx;
                            }
                        }
                    }
                }
            }
        }

        if (useAlphaBeta && useMoveOrdering && count > 1) {
            for (int i = 1; i < count; i++) {
                int move = moveStack[ply][i];
                int weight = STATIC_WEIGHTS[move];
                int j = i - 1;
                while (j >= 0 && STATIC_WEIGHTS[moveStack[ply][j]] < weight) {
                    moveStack[ply][j + 1] = moveStack[ply][j];
                    j--;
                }
                moveStack[ply][j + 1] = move;
            }
        }
        moveCountStack[ply] = count;
    }

    @Override
    public void makeMove(int r, int c, int player) {
        UndoState tempUndo = new UndoState();
        makeMoveRecord(r, c, player, tempUndo);
    }

    private boolean makeMoveRecord(int r, int c, int player, UndoState undo) {
        int index = r * 8 + c;
        int opponent = (player == BLACK) ? WHITE : BLACK;
        undo.placedIndex = index;
        undo.flippedCount = 0;

        if (board[index] != EMPTY) return false;

        int[] drAxes = {0, 1, 1, 1, 0, -1, -1, -1};
        int[] dcAxes = {1, 1, 0, -1, -1, -1, 0, 1};

        for (int d = 0; d < 8; d++) {
            int dr = drAxes[d];
            int dc = dcAxes[d];

            int currRow = r + dr;
            int currCol = c + dc;
            int count = 0;

            while (currRow >= 0 && currRow < 8 && currCol >= 0 && currCol < 8 && board[currRow * 8 + currCol] == opponent) {
                count++;
                currRow += dr;
                currCol += dc;
            }

            if (currRow >= 0 && currRow < 8 && currCol >= 0 && currCol < 8 && board[currRow * 8 + currCol] == player) {
                int scanRow = r + dr;
                int scanCol = c + dc;
                for (int i = 0; i < count; i++) {
                    int fIndex = scanRow * 8 + scanCol;
                    board[fIndex] = player;
                    undo.flippedIndices[undo.flippedCount++] = fIndex;
                    scanRow += dr;
                    scanCol += dc;
                }
            }
        }

        if (undo.flippedCount > 0) {
            board[index] = player;
            return true;
        }
        return false;
    }

    private void undoMove(UndoState undo, int player) {
        int opponent = (player == BLACK) ? WHITE : BLACK;
        board[undo.placedIndex] = EMPTY;
        for (int i = 0; i < undo.flippedCount; i++) {
            board[undo.flippedIndices[i]] = opponent;
        }
    }

    private boolean hasValidMoves(int player) {
        return !getValidMoves(player).isEmpty();
    }

    @Override
    public boolean isGameOver() {
        return !hasValidMoves(BLACK) && !hasValidMoves(WHITE);
    }

    @Override
    public int getWinner() {
        if (!isGameOver()) return -1;
        int blackCount = countDiscs(BLACK);
        int whiteCount = countDiscs(WHITE);
        if (blackCount == whiteCount) return 0;
        return blackCount > whiteCount ? 1 : 2;
    }

    @Override
    public OthelloFlatArray copy() {
        OthelloFlatArray copyObj = new OthelloFlatArray(this.nodeCounter);
        System.arraycopy(this.board, 0, copyObj.board, 0, 64);
        copyObj.useAlphaBeta = this.useAlphaBeta;
        copyObj.useMoveOrdering = this.useMoveOrdering;
        return copyObj;
    }

    private int evaluate(int originalPlayer) {
        nodeCounter.evaluations++;
        int opponent = (originalPlayer == BLACK) ? WHITE : BLACK;
        
        generateMoves1D(originalPlayer, 63);
        int validMovesMax = moveCountStack[63];
        
        generateMoves1D(opponent, 63);
        int validMovesMin = moveCountStack[63];

        int mobilityScore = validMovesMax - validMovesMin;
        int discDifference = countDiscs(originalPlayer) - countDiscs(opponent);

        int positionScore = 0;
        int edgeScore = 0;
        int cornerScore = 0;

        for (int index = 0; index < 64; index++) {
            int piece = board[index];
            if (piece == EMPTY) continue;

            int scoreFactor = (piece == originalPlayer) ? 1 : -1;

            positionScore += STATIC_WEIGHTS[index] * scoreFactor;

            int r = index / 8;
            int c = index % 8;

            if (r == 0 || r == 7 || c == 0 || c == 7) {
                edgeScore += 25 * scoreFactor;
            }

            if ((r == 0 || r == 7) && (c == 0 || c == 7)) {
                cornerScore += 15 * scoreFactor;
            }
        }

        return mobilityScore + positionScore + cornerScore + edgeScore + discDifference;
    }

    private int minimax(int depth, int alpha, int beta, boolean isMaximizing, int player, int originalPlayer, int ply) {
        if (Thread.currentThread().isInterrupted()) {
            return 0;
        }
        nodeCounter.count++;

        generateMoves1D(player, ply);
        int moveCount = moveCountStack[ply];

        if (depth == 0) {
            return evaluate(originalPlayer);
        }

        if (moveCount == 0) {
            int currentOpponent = (player == BLACK) ? WHITE : BLACK;
            
            generateMoves1D(currentOpponent, ply);
            int opponentCount = moveCountStack[ply];
            
            if (opponentCount == 0) {
                return evaluate(originalPlayer);
            }
            
            return minimax(depth, alpha, beta, !isMaximizing, currentOpponent, originalPlayer, ply + 1);
        }

        int currentOpponent = (player == BLACK) ? WHITE : BLACK;
        if (isMaximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (int i = 0; i < moveCount; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    return 0;
                }

                int moveIndex = moveStack[ply][i];
                int r = moveIndex / 8;
                int c = moveIndex % 8;

                UndoState undo = undoStack[ply];
                makeMoveRecord(r, c, player, undo);

                int eval = minimax(depth - 1, 
                                   useAlphaBeta ? alpha : Integer.MIN_VALUE, 
                                   useAlphaBeta ? beta : Integer.MAX_VALUE, 
                                   false, currentOpponent, originalPlayer, ply + 1);
                
                undoMove(undo, player); 

                maxEval = Integer.max(maxEval, eval);
                
                if (useAlphaBeta) {
                    alpha = Integer.max(alpha, eval);
                    if (beta <= alpha) {
                        break;
                    }
                }
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (int i = 0; i < moveCount; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    return 0;
                }

                int moveIndex = moveStack[ply][i];
                int r = moveIndex / 8;
                int c = moveIndex % 8;

                UndoState undo = undoStack[ply];
                makeMoveRecord(r, c, player, undo);

                int eval = minimax(depth - 1, 
                                   useAlphaBeta ? alpha : Integer.MIN_VALUE, 
                                   useAlphaBeta ? beta : Integer.MAX_VALUE, 
                                   true, currentOpponent, originalPlayer, ply + 1);
                
                undoMove(undo, player); 

                minEval = Integer.min(minEval, eval);
                
                if (useAlphaBeta) {
                    beta = Integer.min(beta, eval);
                    if (beta <= alpha) {
                        break;
                    }
                }
            }
            return minEval;
        }
    }

    @Override
    public int findBestMove(int player, int depth) {
        nodeCounter.count = 0; 
        nodeCounter.evaluations = 0;
        
        OthelloFlatArray searchBoard = copy();
        return searchBoard.findBestMoveInternal(player, depth);
    }

    private int findBestMoveInternal(int player, int depth) {
        generateMoves1D(player, 0); 
        int moveCount = moveCountStack[0];
        if (moveCount == 0) {
            return -1;
        }

        int currentOpponent = (player == BLACK) ? WHITE : BLACK;
        int bestScore = Integer.MIN_VALUE;
        int bestMove = -1;

        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        int[] rootMoves = new int[moveCount];
        System.arraycopy(moveStack[0], 0, rootMoves, 0, moveCount);

        for (int i = 0; i < moveCount; i++) {
            if (Thread.currentThread().isInterrupted()) {
                return -1;
            }

            int moveIndex = rootMoves[i];
            int r = moveIndex / 8;
            int c = moveIndex % 8;

            UndoState undo = undoStack[0];
            makeMoveRecord(r, c, player, undo);

            int eval = minimax(depth - 1, 
                               useAlphaBeta ? alpha : Integer.MIN_VALUE, 
                               useAlphaBeta ? beta : Integer.MAX_VALUE, 
                               false, currentOpponent, player, 1);

            undoMove(undo, player);

            if (eval > bestScore) {
                bestScore = eval;
                bestMove = moveIndex;
            }
            
            if (useAlphaBeta) {
                alpha = Math.max(alpha, bestScore);
            }
        }
        return bestMove;
    }

    @Override
    public long estimateMaxNodes(int player, int depth) {
        List<int[]> rootMoves = getValidMoves(player);
        int n0 = rootMoves.size();
        if (n0 == 0 || depth <= 0) return 0;
        if (depth == 1) return n0;

        int opponent = (player == BLACK) ? WHITE : BLACK;
        long totalChildMoves = 0;

        for (int[] move : rootMoves) {
            OthelloFlatArray child = copy();
            child.makeMove(move[0], move[1], player);
            totalChildMoves += child.getValidMoves(opponent).size();
        }

        double priorWeight = 4.0; 
        double priorB = 8.0; 
        double b1 = (totalChildMoves + (priorWeight * priorB)) / (n0 + priorWeight);

        double total;
        if (Math.abs(b1 - 1.0) < 0.0001) {
            total = (double) n0 * depth;
        } else if (b1 == 0.0) {
            total = n0;
        } else {
            total = n0 * (Math.pow(b1, depth) - 1) / (b1 - 1);
        }

        return Math.round(total);
    }

    private static class UndoState {
        int placedIndex;
        int flippedCount;
        final int[] flippedIndices = new int[64];
    }
}