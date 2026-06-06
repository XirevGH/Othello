package Algoritmdesigntekniker;

import java.util.ArrayList;
import java.util.List;

public class OthelloBitboard implements OthelloBoard {
    public static final int BLACK = 1;
    public static final int WHITE = 2;

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

    private static final long COLUMN_A = ~0x0101010101010101L;
    private static final long COLUMN_H = ~0x8080808080808080L;

    private static final int RIGHT = 1;
    private static final int LEFT = -1;
    private static final int UP = -8;
    private static final int DOWN = 8;
    private static final int UP_RIGHT = -7;
    private static final int UP_LEFT = -9;
    private static final int DOWN_RIGHT = 9;
    private static final int DOWN_LEFT = 7;
    
    private static final int[] DIR_SHIFTS = { RIGHT, DOWN_RIGHT, DOWN, DOWN_LEFT, LEFT, UP_LEFT, UP, UP_RIGHT };

    private long blackDiscs;
    private long whiteDiscs;
    private boolean useAlphaBeta = true;
    private boolean useMoveOrdering = true;

    private final int[][] moveStack = new int[64][64];

    private long evaluatedNodes = 0;
    private long evaluationCount = 0;

    public OthelloBitboard() {
        blackDiscs = 0L;
        blackDiscs |= 1L << algebraicToIndex("E4");
        blackDiscs |= 1L << algebraicToIndex("D5");

        whiteDiscs = 0L;
        whiteDiscs |= 1L << algebraicToIndex("E5");
        whiteDiscs |= 1L << algebraicToIndex("D4");
    }

    @Override
    public void setUseMoveOrdering(boolean useMoveOrdering) {
        this.useMoveOrdering = useMoveOrdering;
    }

    @Override
    public void setUseAlphaBeta(boolean useAlphaBeta) {
        this.useAlphaBeta = useAlphaBeta;
    }

    @Override
    public long getEvaluatedNodes() {
        return evaluatedNodes;
    }

    @Override
    public long getEvaluationCount() {
        return evaluationCount;
    }

    @Override
    public int getPieceAt(int r, int c) {
        long bit = 1L << (r * 8 + c);
        if ((blackDiscs & bit) != 0) return BLACK;
        if ((whiteDiscs & bit) != 0) return WHITE;
        return 0; // EMPTY
    }

    public long getOccupied() {
        return blackDiscs | whiteDiscs;
    }

    public long getEmpty() {
        return ~(getOccupied());
    }

    public long getPlayerDiscs(int player) {
        return BLACK == player ? blackDiscs : whiteDiscs;
    }

    public long getOpponentDiscs(int player) {
        return BLACK == player ? whiteDiscs : blackDiscs;
    }

    @Override
    public int countDiscs(int player) {
        return Long.bitCount(getPlayerDiscs(player));
    }

    @Override
    public boolean isValidMove(int r, int c, int player) {
        long bit = 1L << (r * 8 + c);
        return (getValidMovesBitboard(player) & bit) != 0;
    }

    @Override
    public void setPieceAt(int r, int c, int piece) {
        long bit = 1L << (r * 8 + c);
        blackDiscs &= ~bit;
        whiteDiscs &= ~bit;
        if (piece == BLACK) {
            blackDiscs |= bit;
        } else if (piece == WHITE) {
            whiteDiscs |= bit;
        }
    }

    public boolean isValidMove(String pos, int player) {
        int index = algebraicToIndex(pos);
        if (index == -1)
            return false;
        return (getValidMovesBitboard(player) & (1L << index)) != 0;
    }

    @Override
    public List<int[]> getValidMoves(int player) {
        List<int[]> moves = new ArrayList<>();
        long valid = getValidMovesBitboard(player);
        while (valid != 0) {
            int index = Long.numberOfTrailingZeros(valid);
            valid &= ~(1L << index);
            moves.add(new int[]{index / 8, index % 8});
        }
        return moves;
    }

    public long getValidMovesBitboard(int player) {
        return getValidMoves(getPlayerDiscs(player), getOpponentDiscs(player));
    }

    private long getValidMoves(long playerBoard, long opponentBoard) {
        long validMoves = 0L;
        long empty = ~(playerBoard | opponentBoard);

        for (int dir : DIR_SHIFTS) {
            long temp = shift(playerBoard, dir) & opponentBoard;
            while (temp != 0) {
                long nextShift = shift(temp, dir);
                validMoves |= empty & nextShift;
                temp = nextShift & opponentBoard;
            }
        }
        return validMoves;
    }

    private static long shift(long board, int dir) {
        if (dir == RIGHT)
            return (board << 1) & COLUMN_A;
        if (dir == LEFT)
            return (board >>> 1) & COLUMN_H;
        if (dir == UP)
            return (board >>> 8);
        if (dir == DOWN)
            return (board << 8);
        if (dir == UP_RIGHT)
            return (board >>> 7) & COLUMN_A;
        if (dir == UP_LEFT)
            return (board >>> 9) & COLUMN_H;
        if (dir == DOWN_RIGHT)
            return (board << 9) & COLUMN_A;
        if (dir == DOWN_LEFT)
            return (board << 7) & COLUMN_H;
        return 0L;
    }

    private long getFlips(long playerBoard, long opponentBoard, int index) {
        long placedDisc = 1L << index;
        long totalFlips = 0L;

        for (int dir : DIR_SHIFTS) {
            long flipMask = shift(placedDisc, dir);
            long potentialFlips = 0L;

            while ((flipMask & opponentBoard) != 0) {
                potentialFlips |= flipMask;
                flipMask = shift(flipMask, dir);
            }
            if ((flipMask & playerBoard) != 0) {
                totalFlips |= potentialFlips;
            }
        }
        return totalFlips;
    }

    private void applyMove(int index, int player) {
        long playerBoard = getPlayerDiscs(player);
        long opponentBoard = getOpponentDiscs(player);

        long flips = getFlips(playerBoard, opponentBoard, index);
        long newPlayerBoard = playerBoard | flips | (1L << index);
        long newOpponentBoard = opponentBoard & ~flips;

        if (player == BLACK) {
            blackDiscs = newPlayerBoard;
            whiteDiscs = newOpponentBoard;
        } else {
            whiteDiscs = newPlayerBoard;
            blackDiscs = newOpponentBoard;
        }
    }

    @Override
    public void makeMove(int r, int c, int player) {
        applyMove(r * 8 + c, player);
    }

    public void makeMove(String pos, int player) {
        int index = algebraicToIndex(pos);
        if (index != -1 && isValidMove(pos, player)) {
            applyMove(index, player);
        }
    }

    @Override
    public boolean isGameOver() {
        return getValidMovesBitboard(BLACK) == 0 && getValidMovesBitboard(WHITE) == 0;
    }

    @Override
    public int getWinner() {
        if (!isGameOver())
            return -1;
        if (countDiscs(BLACK) == countDiscs(WHITE))
            return 0;
        return countDiscs(BLACK) > countDiscs(WHITE) ? 1 : 2;
    }

    private int evaluate(long maxDiscs, long minDiscs) {
        evaluationCount++;

        long validMovesMax = getValidMoves(maxDiscs, minDiscs);
        long validMovesMin = getValidMoves(minDiscs, maxDiscs);

        int mobilityScore = Long.bitCount(validMovesMax) - Long.bitCount(validMovesMin);
        int discDifference = Long.bitCount(maxDiscs) - Long.bitCount(minDiscs);

        int positionScore = 0;

        long tempMax = maxDiscs;
        while (tempMax != 0) {
            int pos = Long.numberOfTrailingZeros(tempMax);
            positionScore += STATIC_WEIGHTS[pos];
            tempMax &= ~(1L << pos);
        }

        long tempMin = minDiscs;
        while (tempMin != 0) {
            int pos = Long.numberOfTrailingZeros(tempMin);
            positionScore -= STATIC_WEIGHTS[pos];
            tempMin &= ~(1L << pos);
        }

        long edges = 0xFF818181818181FFL;
        int edgeScore = (Long.bitCount(maxDiscs & edges) - Long.bitCount(minDiscs & edges)) * 25;

        long corners = 0x8100000000000081L;
        int cornerScore = (Long.bitCount(maxDiscs & corners) - Long.bitCount(minDiscs & corners)) * 15;

        return mobilityScore + positionScore + cornerScore + edgeScore + discDifference;
    }

    private int minimax(long maxDiscs, long minDiscs, int depth, int alpha, int beta, boolean isMaximizing, int ply) {
        if (Thread.currentThread().isInterrupted()) {
            return 0;
        }

        evaluatedNodes++;

        long validMovesMax = getValidMoves(maxDiscs, minDiscs);
        long validMovesMin = getValidMoves(minDiscs, maxDiscs);

        if (depth == 0 || (validMovesMax == 0 && validMovesMin == 0)) {
            return evaluate(maxDiscs, minDiscs);
        }

        long validMoves = isMaximizing ? validMovesMax : validMovesMin;

        if (validMoves == 0) {
            return minimax(maxDiscs, minDiscs, depth, alpha, beta, !isMaximizing, ply + 1);
        }

        int count = 0;
        long tempMoves = validMoves;
        while (tempMoves != 0) {
            int nextMove = Long.numberOfTrailingZeros(tempMoves);
            tempMoves &= ~(1L << nextMove);
            moveStack[ply][count++] = nextMove;
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

        if (isMaximizing) {
            int maxEval = Integer.MIN_VALUE;

            for (int i = 0; i < count; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    return 0;
                }

                int nextMove = moveStack[ply][i];

                long flips = getFlips(maxDiscs, minDiscs, nextMove);
                long newMax = maxDiscs | flips | (1L << nextMove);
                long newMin = minDiscs & ~flips;

                int eval = minimax(newMax, newMin, depth - 1, 
                                   useAlphaBeta ? alpha : Integer.MIN_VALUE, 
                                   useAlphaBeta ? beta : Integer.MAX_VALUE, 
                                   false, ply + 1);
                
                maxEval = Math.max(maxEval, eval);
                if (useAlphaBeta) {
                    alpha = Math.max(alpha, eval);
                    if (beta <= alpha)
                        break;
                }
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;

            for (int i = 0; i < count; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    return 0;
                }

                int nextMove = moveStack[ply][i];

                long flips = getFlips(minDiscs, maxDiscs, nextMove);
                long newMin = minDiscs | flips | (1L << nextMove);
                long newMax = maxDiscs & ~flips;

                int eval = minimax(newMax, newMin, depth - 1, 
                                   useAlphaBeta ? alpha : Integer.MIN_VALUE, 
                                   useAlphaBeta ? beta : Integer.MAX_VALUE, 
                                   true, ply + 1);
                
                minEval = Math.min(minEval, eval);
                if (useAlphaBeta) {
                    beta = Math.min(beta, eval);
                    if (beta <= alpha)
                        break;
                }
            }
            return minEval;
        }
    }

    @Override
    public int findBestMove(int player, int depth) {
        evaluatedNodes = 0;
        evaluationCount = 0;

        long maxDiscs = getPlayerDiscs(player);
        long minDiscs = getOpponentDiscs(player);

        long validMoves = getValidMovesBitboard(player);
        if (validMoves == 0)
            return -1;

        int bestScore = Integer.MIN_VALUE;
        int bestMove = -1;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        int count = 0;
        long tempMoves = validMoves;
        while (tempMoves != 0) {
            int nextMove = Long.numberOfTrailingZeros(tempMoves);
            tempMoves &= ~(1L << nextMove);
            moveStack[0][count++] = nextMove;
        }

        if (useAlphaBeta && useMoveOrdering && count > 1) {
            for (int i = 1; i < count; i++) {
                int move = moveStack[0][i];
                int weight = STATIC_WEIGHTS[move];
                int j = i - 1;
                while (j >= 0 && STATIC_WEIGHTS[moveStack[0][j]] < weight) {
                    moveStack[0][j + 1] = moveStack[0][j];
                    j--;
                }
                moveStack[0][j + 1] = move;
            }
        }

        for (int i = 0; i < count; i++) {
            if (Thread.currentThread().isInterrupted()) {
                return -1;
            }

            int nextMove = moveStack[0][i];

            long flips = getFlips(maxDiscs, minDiscs, nextMove);
            long newMax = maxDiscs | flips | (1L << nextMove);
            long newMin = minDiscs & ~flips;

            int eval = minimax(newMax, newMin, depth - 1, 
                               useAlphaBeta ? alpha : Integer.MIN_VALUE, 
                               useAlphaBeta ? beta : Integer.MAX_VALUE, 
                               false, 1);

            if (eval > bestScore) {
                bestScore = eval;
                bestMove = nextMove;
            }
            if (useAlphaBeta) {
                alpha = Math.max(alpha, bestScore);
            }
        }
        return bestMove;
    }

    @Override
    public long estimateMaxNodes(int player, int depth) {
        long rootMoves = getValidMovesBitboard(player);
        int n0 = Long.bitCount(rootMoves);
        if (n0 == 0 || depth <= 0) return 0;
        if (depth == 1) return n0;

        int opponent = (player == BLACK) ? WHITE : BLACK;
        long totalChildMoves = 0;

        long tempMoves = rootMoves;
        while (tempMoves != 0) {
            long lowestBit = Long.lowestOneBit(tempMoves);
            int position = Long.numberOfTrailingZeros(lowestBit);
            tempMoves &= ~lowestBit;

            OthelloBitboard child = copy();
            child.makeMove(position / 8, position % 8, player);
            totalChildMoves += Long.bitCount(child.getValidMovesBitboard(opponent));
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

    @Override
    public OthelloBitboard copy() {
        OthelloBitboard boardCopy = new OthelloBitboard();
        boardCopy.blackDiscs = blackDiscs;
        boardCopy.whiteDiscs = whiteDiscs;
        boardCopy.evaluatedNodes = evaluatedNodes;
        boardCopy.evaluationCount = evaluationCount;
        boardCopy.useAlphaBeta = this.useAlphaBeta;
        boardCopy.useMoveOrdering = this.useMoveOrdering; 
        return boardCopy;
    }

    public static int algebraicToIndex(String algebraic) {
        if (algebraic == null || algebraic.length() != 2)
            return -1;
        char col = algebraic.toUpperCase().charAt(0);
        char row = algebraic.charAt(1);
        if (col < 'A' || col > 'H' || row < '1' || row > '8')
            return -1;
        return (row - '1') * 8 + (col - 'A');
    }

    public static String indexToAlgebraic(int index) {
        if (index < 0 || index > 63)
            return "";
        char row = (char) ((index / 8) + '1');
        char col = (char) ((index % 8) + 'A');
        return "" + col + row;
    }
}