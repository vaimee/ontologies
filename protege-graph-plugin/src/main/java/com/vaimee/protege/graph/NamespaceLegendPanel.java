package com.vaimee.protege.graph;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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

        setNamespaces(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(), namespace -> {
        });
    }

    void setNamespaces(Map<String, Color> namespaces, Map<String, String> prefixes, Set<String> visibleNamespaces, Consumer<String> visibilityChanged) {
        namespaceList.removeAll();
        if (namespaces.isEmpty()) {
            namespaceList.add(new JLabel("No namespaces"));
        } else {
            for (Map.Entry<String, Color> entry : namespaces.entrySet()) {
                namespaceList.add(new NamespaceEntry(entry.getKey(), prefixes.get(entry.getKey()), entry.getValue(), visibleNamespaces.contains(entry.getKey()), visibilityChanged));
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
        NamespaceEntry(String namespace, String prefix, Color color, boolean visible, Consumer<String> visibilityChanged) {
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);
            setBorder(new EmptyBorder(8, 0, 0, 0));
            JCheckBox checkbox = new JCheckBox();
            checkbox.setSelected(visible);
            checkbox.setToolTipText("Show/hide namespace");
            checkbox.addActionListener(event -> visibilityChanged.accept(namespace));
            add(checkbox);
            add(new ColorSwatch(color));
            JLabel label = new JLabel(prefixLabel(prefix) + " " + namespace);
            label.setToolTipText(namespace);
            add(label);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        private static String prefixLabel(String prefix) {
            if (prefix == null) {
                return "";
            }
            if (prefix.isEmpty()) {
                return ":";
            }
            return prefix + ":";
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
