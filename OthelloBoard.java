import java.util.List;

public interface OthelloBoard {
    int getPieceAt(int r, int c);
    boolean isValidMove(int r, int c, int player);
    List<int[]> getValidMoves(int player);
    void makeMove(int r, int c, int player);
    int countDiscs(int player);
    boolean isGameOver();
    int getWinner();
    OthelloBoard copy();
    int findBestMove(int player, int depth);
    long getEvaluatedNodes();
    long getEvaluationCount();
    long estimateMaxNodes(int player, int depth);
    void setPieceAt(int r, int c, int piece);
    void setUseAlphaBeta(boolean useAlphaBeta);
    void setUseMoveOrdering(boolean useMoveOrdering); // NEW: Toggle signature
}