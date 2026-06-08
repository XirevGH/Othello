import java.util.ArrayList;
import java.util.List;

public class OthelloCellObjects implements OthelloBoard {
    public static final int BLACK = 1;
    public static final int WHITE = 2;
    public static final int EMPTY = 0;

    private static final int[][] STATIC_WEIGHTS = {
            {4, -3, 2, 2, 2, 2, -3, 4},
            {-3, -4, -1, -1, -1, -1, -4, -3},
            {2, -1, 1, 0, 0, 1, -1, 2},
            {2, -1, 0, 1, 1, 0, -1, 2},
            {2, -1, 0, 1, 1, 0, -1, 2},
            {2, -1, 1, 0, 0, 1, -1, 2},
            {-3, -4, -1, -1, -1, -1, -4, -3},
            {4, -3, 2, 2, 2, 2, -3, 4}
    };

    private static final int[] DR = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DC = {1, 1, 0, -1, -1, -1, 0, 1};

    private static class Cell {
        int row;
        int col;
        int piece;

        Cell(int row, int col, int piece) {
            this.row = row;
            this.col = col;
            this.piece = piece;
        }
    }

    private final Cell[][] board = new Cell[8][8];
    private boolean useAlphaBeta = true;
    private boolean useMoveOrdering = true;

    private static class NodeCounter {
        volatile long count = 0;
        volatile long evaluations = 0;
    }
    private final NodeCounter nodeCounter;

    private final UndoState[] undoStack = new UndoState[64];
    private final int[] moveStackR = new int[64 * 64];
    private final int[] moveStackC = new int[64 * 64];
    private final int[] moveCountStack = new int[64];

    public OthelloCellObjects() {
        this.nodeCounter = new NodeCounter();
        
        for (int i = 0; i < 64; i++) {
            undoStack[i] = new UndoState();
        }

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                board[r][c] = new Cell(r, c, EMPTY);
            }
        }

        board[3][4].piece = BLACK;
        board[4][3].piece = BLACK;
        board[3][3].piece = WHITE;
        board[4][4].piece = WHITE;
    }

    private OthelloCellObjects(NodeCounter counter) {
        this.nodeCounter = counter;
        for (int i = 0; i < 64; i++) {
            undoStack[i] = new UndoState();
        }
    }

    @Override
    public void setUseAlphaBeta(boolean useAlphaBeta) {
        this.useAlphaBeta = useAlphaBeta;
    }

    @Override
    public void setUseMoveOrdering(boolean useMoveOrdering) {
        this.useMoveOrdering = useMoveOrdering;
    }

    @Override
    public long getEvaluatedNodes() {
        return nodeCounter.count;
    }

    @Override
    public long getEvaluationCount() {
        return nodeCounter.evaluations;
    }

    @Override
    public int getPieceAt(int r, int c) {
        return board[r][c].piece;
    }

    @Override
    public void setPieceAt(int r, int c, int piece) {
        board[r][c].piece = piece;
    }

    @Override
    public int countDiscs(int player) {
        int count = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c].piece == player) count++;
            }
        }
        return count;
    }

    @Override
    public boolean isValidMove(int r, int c, int player) {
        if (board[r][c].piece != EMPTY) return false;
        int opponent = (player == BLACK) ? WHITE : BLACK;

        for (int d = 0; d < 8; d++) {
            int dr = DR[d];
            int dc = DC[d];

            int currRow = r + dr;
            int currCol = c + dc;
            int count = 0;

            while (currRow >= 0 && currRow < 8 && currCol >= 0 && currCol < 8 && board[currRow][currCol].piece == opponent) {
                count++;
                currRow += dr;
                currCol += dc;
            }

            if (count > 0 && currRow >= 0 && currRow < 8 && currCol >= 0 && currCol < 8 && board[currRow][currCol].piece == player) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<int[]> getValidMoves(int player) {
        List<int[]> moves = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c].piece == EMPTY) {
                    if (isValidMove(r, c, player)) {
                        moves.add(new int[]{r, c});
                    }
                }
            }
        }
        return moves;
    }

    private void generateMoves2D(int player, int ply) {
        int count = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (board[r][c].piece == EMPTY) {
                    if (isValidMove(r, c, player)) {
                        moveStackR[ply * 64 + count] = r;
                        moveStackC[ply * 64 + count] = c;
                        count++;
                    }
                }
            }
        }

        if (useAlphaBeta && useMoveOrdering && count > 1) {
            for (int i = 1; i < count; i++) {
                int mr = moveStackR[ply * 64 + i];
                int mc = moveStackC[ply * 64 + i];
                int weight = STATIC_WEIGHTS[mr][mc];
                int j = i - 1;
                while (j >= 0 && STATIC_WEIGHTS[moveStackR[ply * 64 + j]][moveStackC[ply * 64 + j]] < weight) {
                    moveStackR[ply * 64 + j + 1] = moveStackR[ply * 64 + j];
                    moveStackC[ply * 64 + j + 1] = moveStackC[ply * 64 + j];
                    j--;
                }
                moveStackR[ply * 64 + j + 1] = mr;
                moveStackC[ply * 64 + j + 1] = mc;
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
        int opponent = (player == BLACK) ? WHITE : BLACK;
        undo.placedRow = r;
        undo.placedCol = c;
        undo.flippedCount = 0;

        if (board[r][c].piece != EMPTY) return false;

        for (int d = 0; d < 8; d++) {
            int dr = DR[d];
            int dc = DC[d];

            int currRow = r + dr;
            int currCol = c + dc;
            int count = 0;

            while (currRow >= 0 && currRow < 8 && currCol >= 0 && currCol < 8 && board[currRow][currCol].piece == opponent) {
                count++;
                currRow += dr;
                currCol += dc;
            }

            if (currRow >= 0 && currRow < 8 && currCol >= 0 && currCol < 8 && board[currRow][currCol].piece == player) {
                int scanRow = r + dr;
                int scanCol = c + dc;
                for (int i = 0; i < count; i++) {
                    board[scanRow][scanCol].piece = player;
                    undo.flippedCoords[undo.flippedCount][0] = scanRow;
                    undo.flippedCoords[undo.flippedCount][1] = scanCol;
                    undo.flippedCount++;
                    scanRow += dr;
                    scanCol += dc;
                }
            }
        }

        if (undo.flippedCount > 0) {
            board[r][c].piece = player;
            return true;
        }
        return false;
    }

    private void undoMove(UndoState undo, int player) {
        int opponent = (player == BLACK) ? WHITE : BLACK;
        board[undo.placedRow][undo.placedCol].piece = EMPTY;
        for (int i = 0; i < undo.flippedCount; i++) {
            int fr = undo.flippedCoords[i][0];
            int fc = undo.flippedCoords[i][1];
            board[fr][fc].piece = opponent;
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
    public OthelloCellObjects copy() {
        OthelloCellObjects copyObj = new OthelloCellObjects(this.nodeCounter);
        copyObj.useAlphaBeta = this.useAlphaBeta;
        copyObj.useMoveOrdering = this.useMoveOrdering;
        
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                copyObj.board[r][c] = new Cell(r, c, this.board[r][c].piece);
            }
        }
        return copyObj;
    }

    private int evaluate(int originalPlayer) {
        nodeCounter.evaluations++;
        int opponent = (originalPlayer == BLACK) ? WHITE : BLACK;

        generateMoves2D(originalPlayer, 63);
        int validMovesMax = moveCountStack[63];

        generateMoves2D(opponent, 63);
        int validMovesMin = moveCountStack[63];

        int mobilityScore = validMovesMax - validMovesMin;
        int discDifference = countDiscs(originalPlayer) - countDiscs(opponent);

        int positionScore = 0;
        int edgeScore = 0;
        int cornerScore = 0;

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int piece = board[r][c].piece;
                if (piece == EMPTY) continue;

                int scoreFactor = (piece == originalPlayer) ? 1 : -1;

                positionScore += STATIC_WEIGHTS[r][c] * scoreFactor;

                if (r == 0 || r == 7 || c == 0 || c == 7) {
                    edgeScore += 25 * scoreFactor;
                }

                if ((r == 0 || r == 7) && (c == 0 || c == 7)) {
                    cornerScore += 15 * scoreFactor;
                }
            }
        }

        return mobilityScore + positionScore + cornerScore + edgeScore + discDifference;
    }

    private int minimax(int depth, int alpha, int beta, boolean isMaximizing, int player, int originalPlayer, int ply) {
        if (Thread.currentThread().isInterrupted()) {
            return 0;
        }
        nodeCounter.count++;

        generateMoves2D(player, ply);
        int moveCount = moveCountStack[ply];

        if (depth == 0) {
            return evaluate(originalPlayer);
        }

        if (moveCount == 0) {
            int currentOpponent = (player == BLACK) ? WHITE : BLACK;
            generateMoves2D(currentOpponent, ply);
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

                int mr = moveStackR[ply * 64 + i];
                int mc = moveStackC[ply * 64 + i];

                UndoState undo = undoStack[ply];
                makeMoveRecord(mr, mc, player, undo);

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

                int mr = moveStackR[ply * 64 + i];
                int mc = moveStackC[ply * 64 + i];

                UndoState undo = undoStack[ply];
                makeMoveRecord(mr, mc, player, undo);

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

        OthelloCellObjects searchBoard = copy();
        return searchBoard.findBestMoveInternal(player, depth);
    }

    private int findBestMoveInternal(int player, int depth) {
        generateMoves2D(player, 0);
        int moveCount = moveCountStack[0];
        if (moveCount == 0) {
            return -1;
        }

        int currentOpponent = (player == BLACK) ? WHITE : BLACK;
        int bestScore = Integer.MIN_VALUE;
        int bestMove = -1;

        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        int[][] rootMoves = new int[moveCount][2];
        for (int i = 0; i < moveCount; i++) {
            rootMoves[i][0] = moveStackR[0 * 64 + i];
            rootMoves[i][1] = moveStackC[0 * 64 + i];
        }

        for (int i = 0; i < moveCount; i++) {
            if (Thread.currentThread().isInterrupted()) {
                return -1;
            }

            int mr = rootMoves[i][0];
            int mc = rootMoves[i][1];

            UndoState undo = undoStack[0];
            makeMoveRecord(mr, mc, player, undo);

            int eval = minimax(depth - 1, 
                               useAlphaBeta ? alpha : Integer.MIN_VALUE, 
                               useAlphaBeta ? beta : Integer.MAX_VALUE, 
                               false, currentOpponent, player, 1);

            undoMove(undo, player);

            if (eval > bestScore) {
                bestScore = eval;
                bestMove = mr * 8 + mc; 
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
            OthelloCellObjects child = copy();
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
        int placedRow;
        int placedCol;
        int flippedCount;
        final int[][] flippedCoords = new int[64][2];
    }
}