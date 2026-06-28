package com.vaimee.protege.graph;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.Collections;
import java.util.Map;

final class NamespaceLegendPanel extends JPanel {

    private final JPanel namespaceList = new JPanel();

    NamespaceLegendPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(12, 12, 12, 12));
        setPreferredSize(new Dimension(320, 760));

        JLabel title = new JLabel("Namespaces");
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);

        namespaceList.setLayout(new BoxLayout(namespaceList, BoxLayout.Y_AXIS));
        namespaceList.setAlignmentX(LEFT_ALIGNMENT);
        add(namespaceList);

        setNamespaces(Collections.emptyMap());
    }

    void setNamespaces(Map<String, Color> namespaces) {
        namespaceList.removeAll();
        if (namespaces.isEmpty()) {
            namespaceList.add(new JLabel("No namespaces"));
        } else {
            for (Map.Entry<String, Color> entry : namespaces.entrySet()) {
                namespaceList.add(new NamespaceEntry(entry.getKey(), entry.getValue()));
            }
        }
        setPreferredSize(new Dimension(preferredWidth(namespaces), getPreferredSize().height));
        revalidate();
        repaint();
    }

    private int preferredWidth(Map<String, Color> namespaces) {
        FontMetrics metrics = getFontMetrics(getFont());
        int width = 320;
        for (String namespace : namespaces.keySet()) {
            width = Math.max(width, metrics.stringWidth(namespace) + 64);
        }
        return Math.min(width, 620);
    }

    private static final class NamespaceEntry extends JPanel {
        NamespaceEntry(String namespace, Color color) {
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
            setBorder(new EmptyBorder(8, 0, 0, 0));
            add(new ColorSwatch(color));
            JLabel label = new JLabel(namespace);
            label.setToolTipText(namespace);
            add(label);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    private static final class ColorSwatch extends JPanel {
        private final Color color;

        ColorSwatch(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(18, 18));
            setMaximumSize(new Dimension(18, 18));
            setBorder(new EmptyBorder(0, 0, 0, 8));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            graphics.setColor(color);
            graphics.fillRoundRect(0, 3, 12, 12, 5, 5);
        }
    }
}
