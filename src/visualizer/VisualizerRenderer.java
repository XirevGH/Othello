package src.visualizer;

import java.awt.*;

public final class VisualizerRenderer {
    private VisualizerRenderer() {}

    public static void drawVisualizerOverlays(Graphics2D g2, VizState state, int boardWidth, int boardHeight) {
        if (state == null) return;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = boardWidth / 8;
        int h = boardHeight / 8;

        // LAYER 1: Draw check arrows and bitboard shift arrows
        g2.setStroke(new BasicStroke(4.0f)); 
        for (int[] arrow : state.arrows) {
            int startR = arrow[0];
            int startC = arrow[1];
            int endR = arrow[2];
            int endC = arrow[3];
            int colorType = arrow.length > 4 ? arrow[4] : 1; 

            int x1 = startC * w + w / 2;
            int y1 = startR * h + h / 2;
            int x2 = endC * w + w / 2;
            int y2 = endR * h + h / 2;

            if (colorType == 2) {
                g2.setColor(new Color(255, 50, 50));  // Red
            } else if (colorType == 3) {
                g2.setColor(new Color(0, 255, 0));    // Green
            } else {
                g2.setColor(new Color(255, 235, 0));   // Yellow
            }

            drawArrowLine(g2, x1, y1, x2, y2, 18, 9); 
        }

        // Draw offscreen wall hit vectors
        for (int[] wallHit : state.wallHits) {
            int startR = wallHit[0];
            int startC = wallHit[1];
            int edgeR = wallHit[2];
            int edgeC = wallHit[3];
            int offR = wallHit[4];
            int offC = wallHit[5];

            int xs = startC * w + w / 2;
            int ys = startR * h + h / 2;
            int x1 = edgeC * w + w / 2; 
            int y1 = edgeR * h + h / 2; 
            int x2 = offC * w + w / 2;  
            int y2 = offR * h + h / 2;  

            int edgeX = x1 + (x2 - x1) / 2;
            int edgeY = y1 + (y2 - y1) / 2;

            g2.setColor(new Color(255, 50, 50)); // Red
            drawArrowLine(g2, xs, ys, edgeX, edgeY, 18, 9);
            drawCross(g2, edgeX, edgeY, 16); 
        }

        // LAYER 2: Draw all vector outcome markers
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String marker = state.markers[r][c];
                if (marker != null) {
                    int cx = c * w + w / 2;
                    int cy = r * h + h / 2;
                    
                    if (marker.equals("✓")) {
                        int checkY = cy + 13;
                        drawCheckmark(g2, cx, checkY, 24); 
                    } else if (marker.equals("X")) {
                        int crossY = cy + 13;
                        drawCross(g2, cx, crossY, 20); 
                    } else if (marker.equals("?")) {
                        int qY = cy + 13;
                        g2.setFont(new Font("Arial", Font.BOLD, 22));
                        g2.setColor(Color.BLACK); 
                        g2.drawString("?", cx - 6 + 1, qY + 1); // Shadow
                        g2.setColor(Color.WHITE); 
                        g2.drawString("?", cx - 6, qY); // Foreground
                    } else if (marker.startsWith("Skip")) {
                        int axis = Integer.parseInt(marker.substring(4));
                        drawSkipDoubleArrow(g2, cx, cy, axis); 
                    } else {
                        g2.setFont(new Font("Arial", Font.BOLD, 18));
                        g2.setColor(Color.BLACK); 
                        g2.drawString(marker, cx - 5 + 1, cy + 7 + 1); // Shadow
                        g2.setColor(new Color(255, 235, 0)); 
                        g2.drawString(marker, cx - 5, cy + 7); // Foreground
                    }
                }
            }
        }

        // LAYER 3: Draw visualizer text labels
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                String text = state.tileTexts[r][c];
                boolean isPersistentValid = state.persistentValids[r][c];
                boolean isPersistentInvalid = state.persistentInvalids[r][c];

                if (text == null) text = "";

                if (!text.isEmpty()) {
                    drawCenteredMultiLineString(g2, text, w, h, r, c, state.highlights[r][c]);
                } else if (isPersistentValid) {
                    drawCenteredMultiLineString(g2, "Valid", w, h, r, c, 3);
                } else if (isPersistentInvalid) {
                    drawCenteredMultiLineString(g2, "Invalid", w, h, r, c, 4);
                }
            }
        }
    }

    public static void drawCenteredMultiLineString(Graphics2D g2, String text, int cellWidth, int cellHeight, int row, int col, int highlight) {
        if (text.isEmpty()) return;

        int fontSize;
        switch (text) {
            case "Valid":
            case "Invalid":
            case "Evaluating":
            case "End of board":
            case "Empty":
            case "Own Piece":
            case "Skip":
            case "Opponent":
            case "Already checked":
                fontSize = 14;
                break;
            default:
                fontSize = 9;
                break;
        }

        g2.setFont(new Font("Arial", Font.BOLD, fontSize));
        FontMetrics fm = g2.getFontMetrics();
        String[] lines = text.split("\n");
        int lineHeight = fm.getHeight() - 2; 
        int totalHeight = lineHeight * lines.length;
        
        int cellX = col * cellWidth;
        int cellY = row * cellHeight;
        
        int startY = cellY + ((cellHeight - totalHeight) / 2) + fm.getAscent() - 13;
        
        if (text.equals("Skip") || text.equals("Already checked")) {
            startY += 3; 
        }

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int startX = cellX + (cellWidth - fm.stringWidth(line)) / 2;
            
            if (text.equals("Skip") || text.equals("Already checked")) {
                startX += 3; 
            }
            
            int currentY = startY + i * lineHeight;
            
            g2.setColor(new Color(0, 0, 0, 220));
            g2.drawString(line, startX + 1, currentY + 1);
            
            if (text.equals("Valid")) {
                g2.setColor(new Color(0, 255, 0)); 
            } else if (text.equals("Own Piece") && highlight == 3) {
                g2.setColor(new Color(0, 255, 0)); 
            } else if (text.equals("Own Piece") && highlight == 1) {
                g2.setColor(new Color(255, 215, 0)); 
            } else if (text.equals("Own Piece") && highlight == 2) {
                g2.setColor(new Color(255, 215, 0)); 
            } else if (text.equals("Empty") && highlight == 2) {
                g2.setColor(new Color(255, 215, 0)); 
            } else if (text.equals("Empty") && highlight == 1) {
                g2.setColor(new Color(255, 215, 0)); 
            } else if (text.equals("Skip") || text.equals("Already checked")) {
                g2.setColor(new Color(160, 32, 240)); 
            } else if (text.equals("Invalid") || text.equals("End of board") || text.equals("Empty") || text.equals("Own Piece") || text.equals("Opponent")) {
                g2.setColor(new Color(255, 50, 50)); 
            } else if (text.equals("Evaluating")) {
                g2.setColor(Color.WHITE); 
            } else {
                g2.setColor(Color.WHITE);
            }
            g2.drawString(line, startX, currentY);
        }
    }

    public static void drawSkipDoubleArrow(Graphics2D g2, int cx, int cy, int axis) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int arrowCx = cx;
        int arrowCy = cy + 10;

        if (axis == 0) { 
            arrowCx += 2;
            arrowCy -= 3;
        } else if (axis == 1) { 
            arrowCx += 3;
        } else { 
            arrowCx += 3; 
            arrowCy -= 3; 
        }

        int len = 12; 
        g2.setStroke(new BasicStroke(3.0f));
        drawSingleAxisDoubleArrow(g2, arrowCx, arrowCy, len, axis, new Color(160, 32, 240)); 
    }

    public static void drawSingleAxisDoubleArrow(Graphics2D g2, int cx, int cy, int len, int axis, Color color) {
        g2.setColor(color);
        int d = 8; 
        int h = 5; 

        if (axis == 0) { 
            int x1 = cx - len;
            int x2 = cx + len;
            g2.drawLine(x1 + d - 1, cy, x2 - d + 1, cy); 
            
            int[] xp1 = {x1, x1 + d, x1 + d};
            int[] yp1 = {cy, cy - h, cy + h};
            g2.fillPolygon(xp1, yp1, 3);
            
            int[] xp2 = {x2, x2 - d, x2 - d};
            int[] yp2 = {cy, cy - h, cy + h};
            g2.fillPolygon(xp2, yp2, 3);
            
        } else if (axis == 1) { 
            int y1 = cy - len;
            int y2 = cy + len;
            g2.drawLine(cx, y1 + d - 1, cx, y2 - d + 1);
            
            int[] xp1 = {cx, cx - h, cx + h};
            int[] yp1 = {y1, y1 + d, y1 + d};
            g2.fillPolygon(xp1, yp1, 3);
            
            int[] xp2 = {cx, cx - h, cx + h};
            int[] yp2 = {y2, y2 - d, y2 - d};
            g2.fillPolygon(xp2, yp2, 3);
            
        } else if (axis == 2) { 
            int offset = (int)(len * 0.707);
            int x1 = cx - offset;
            int y1 = cy - offset;
            int x2 = cx + offset;
            int y2 = cy + offset;
            
            int od = (int)(d * 0.707);
            int oh = (int)(h * 0.707);
            
            g2.drawLine(x1 + od - 1, y1 + od - 1, x2 - od + 1, y2 - od + 1);
            
            int[] xp1 = {x1, x1 + od - oh, x1 + od + oh};
            int[] yp1 = {y1, y1 + od + oh, y1 + od - oh};
            g2.fillPolygon(xp1, yp1, 3);
            
            int[] xp2 = {x2, x2 - od - oh, x2 - od + oh};
            int[] yp2 = {y2, y2 - od + oh, y2 - od - oh};
            g2.fillPolygon(xp2, yp2, 3);
            
        } else if (axis == 3) { 
            int offset = (int)(len * 0.707);
            int x1 = cx + offset;
            int y1 = cy - offset;
            int x2 = cx - offset;
            int y2 = cy + offset;
            
            int od = (int)(d * 0.707);
            int oh = (int)(h * 0.707);
            
            g2.drawLine(x1 - od + 1, y1 + od - 1, x2 + od - 1, y2 - od + 1);
            
            int[] xp1 = {x1, x1 - od - oh, x1 - od + oh};
            int[] yp1 = {y1, y1 + od - oh, y1 + od + oh};
            g2.fillPolygon(xp1, yp1, 3);
            
            int[] xp2 = {x2, x2 + od - oh, x2 + od + oh};
            int[] yp2 = {y2, y2 - od - oh, y2 - od + oh};
            g2.fillPolygon(xp2, yp2, 3);
        }
    }

    public static void drawArrowLine(Graphics2D g, int x1, int y1, int x2, int y2, int d, int h) {
        int dx = x2 - x1, dy = y2 - y1;
        double D = Math.sqrt(dx * dx + dy * dy);
        if (D < 1) return;
        
        int pad = 5;
        double x1_adj = x1 + (dx / D) * pad;
        double y1_adj = y1 + (dy / D) * pad;
        double x2_adj = x2 - (dx / D) * pad;
        double y2_adj = y2 - (dy / D) * pad;
        
        int x1_i = (int) x1_adj;
        int x2_i = (int) x2_adj;
        int y1_i = (int) y1_adj;
        int y2_i = (int) y2_adj;

        int dx_adj = x2_i - x1_i;
        int dy_adj = y2_i - y1_i;
        double D_adj = Math.sqrt(dx_adj * dx_adj + dy_adj * dy_adj);

        double xm = D_adj - d; 
        double sin = dy_adj / D_adj, cos = dx_adj / D_adj;

        int x_base = (int) (xm * cos + x1_i);
        int y_base = (int) (xm * sin + y1_i);

        g.drawLine(x1_i, y1_i, x_base, y_base);

        double ym = h, yn = -h;
        double x_m_rot = xm * cos - ym * sin + x1_i;
        double y_m_rot = xm * sin + ym * cos + y1_i;
        double x_n_rot = xm * cos - yn * sin + x1_i;
        double y_n_rot = xm * sin + yn * cos + y1_i;

        int[] xpoints = {x2_i, (int) x_m_rot, (int) x_n_rot};
        int[] ypoints = {y2_i, (int) y_m_rot, (int) y_n_rot};

        g.fillPolygon(xpoints, ypoints, 3);
    }

    public static void drawCheckmark(Graphics2D g2, int cx, int cy, int size) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(4.0f));
        
        // Shadow
        g2.setColor(new Color(0, 0, 0, 180));
        g2.drawLine(cx - size/3 + 1, cy + 1, cx - size/10 + 1, cy + size/3 + 1);
        g2.drawLine(cx - size/10 + 1, cy + size/3 + 1, cx + size/3 + 1, cy - size/3 + 1);
        
        // Foreground
        g2.setColor(new Color(0, 255, 0));
        g2.drawLine(cx - size/3, cy, cx - size/10, cy + size/3);
        g2.drawLine(cx - size/10, cy + size/3, cx + size/3, cy - size/3);
    }

    public static void drawCross(Graphics2D g2, int cx, int cy, int size) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(4.0f));
        
        // Shadow
        g2.setColor(new Color(0, 0, 0, 180));
        g2.drawLine(cx - size/3 + 1, cy - size/3 + 1, cx + size/3 + 1, cy + size/3 + 1);
        g2.drawLine(cx + size/3 + 1, cy - size/3 + 1, cx - size/3 + 1, cy + size/3 + 1);
        
        // Foreground
        g2.setColor(new Color(255, 50, 50));
        g2.drawLine(cx - size/3, cy - size/3, cx + size/3, cy + size/3);
        g2.drawLine(cx + size/3, cy - size/3, cx - size/3, cy + size/3);
    }
}