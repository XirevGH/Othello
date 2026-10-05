package src.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import src.engine.OthelloConstants;
import src.visualizer.VizState;

public class BoardSquare extends JPanel implements OthelloConstants {
    private final int row;
    private final int col;
    private final OthelloGUI gui;

    public BoardSquare(int row, int col, OthelloGUI gui) {
        this.row = row;
        this.col = col;
        this.gui = gui;
        setBackground(new Color(34, 139, 34));
        setBorder(BorderFactory.createLineBorder(new Color(0, 50, 0), 1));
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                gui.handleHumanMove(row, col);
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int highlight = gui.getDebugHighlight(row, col);
        
        boolean isPersistentValid = false;
        boolean isPersistentInvalid = false;

        VizState state = gui.getCurrentVizState();
        if (state != null) {
            isPersistentValid = state.persistentValids[row][col];
            isPersistentInvalid = state.persistentInvalids[row][col];
        }
        
        int piece = gui.getGame().getPieceAt(row, col);

        if (highlight != 0) {
            if (highlight == 1) g2.setColor(new Color(0, 191, 255, 120));      
            else if (highlight == 2) g2.setColor(new Color(255, 215, 0, 120)); 
            else if (highlight == 3) g2.setColor(new Color(50, 205, 50, 120)); 
            else if (highlight == 4) g2.setColor(new Color(220, 20, 60, 120)); 
            else if (highlight == 5) g2.setColor(new Color(255, 140, 0, 120)); 
            else if (highlight == 6) g2.setColor(new Color(160, 32, 240, 120)); 
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

        int size = Math.min(getWidth(), getHeight()) - 12;
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;

        if (piece == BLACK) {
            drawDisc(g2, Color.BLACK, x, y, size);
        } else if (piece == WHITE) {
            drawDisc(g2, Color.WHITE, x, y, size);
        } else if (gui.isValidHumanMove(row, col)) {
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