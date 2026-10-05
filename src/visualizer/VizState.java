package src.visualizer;

import java.util.ArrayList;
import java.util.List;


public class VizState {
    public int dir;
    public boolean[][] persistentValids;
    public boolean[][] persistentInvalids;
    public int[][] highlights;
    public String explanation;

    public List<int[]> arrows = new ArrayList<>();   
    public List<int[]> wallHits = new ArrayList<>(); 
    public String[][] markers = new String[8][8];    
    public String[][] tileTexts = new String[8][8];  

    public VizState(int dir, boolean[][] valids, boolean[][] invalids, int[][] highlights, String explanation) {
        this.dir = dir;
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