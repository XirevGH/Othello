package src.engine;

public interface OthelloConstants {
    int EMPTY = 0;
    int BLACK = 1;
    int WHITE = 2;

    int[] DR = {0, 1, 1, 1, 0, -1, -1, -1};
    int[] DC = {1, 1, 0, -1, -1, -1, 0, 1};

    String[] DIR_NAMES = {
        "East", "South-East", "South", "South-West", "West", "North-West", "North", "North-East"
    };

    String[] DIR_ARROWS = {
        "→", "↘", "↓", "↙", "←", "↖", "↑", "↗"
    };
}