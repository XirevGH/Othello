package src.ui;

import javax.swing.*;
import java.awt.*;

public final class OthelloIcons {
    private OthelloIcons() {}

    public static class PlayIcon implements Icon {
        private final int width;
        private final int height;
        private final Color color;

        public PlayIcon(int width, int height, Color color) {
            this.width = width;
            this.height = height;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            int[] xPoints = { x + 2, x + 2, x + width - 2 };
            int[] yPoints = { y + 2, y + height - 2, y + height / 2 };
            g2.fillPolygon(xPoints, yPoints, 3);
            g2.dispose();
        }

        @Override public int getIconWidth() { return width; }
        @Override public int getIconHeight() { return height; }
    }

    public static class StopIcon implements Icon {
        private final int width;
        private final int height;
        private final Color color;

        public StopIcon(int width, int height, Color color) {
            this.width = width;
            this.height = height;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRect(x + 2, y + 2, width - 4, height - 4);
            g2.dispose();
        }

        @Override public int getIconWidth() { return width; }
        @Override public int getIconHeight() { return height; }
    }
}