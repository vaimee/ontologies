package com.vaimee.protege.graph;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.util.HashMap;
import java.util.Map;

final class OntologyGraphPanel extends JPanel {

    private static final Color CLASS_COLOR = new Color(15, 62, 101);
    private static final Color PROPERTY_COLOR = new Color(16, 177, 216);
    private static final Color SUBCLASS_EDGE_COLOR = new Color(78, 103, 126);
    private static final Color PROPERTY_EDGE_COLOR = new Color(16, 177, 216, 180);
    private static final int NODE_RADIUS = 34;

    private OntologyGraph graph = new OntologyGraph();

    OntologyGraphPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(900, 650));
    }

    void setGraph(OntologyGraph graph) {
        this.graph = graph;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Map<String, Point> positions = layoutNodes();
        paintEdges(g, positions);
        paintNodes(g, positions);
        g.dispose();
    }

    private Map<String, Point> layoutNodes() {
        Map<String, Point> positions = new HashMap<>();
        int count = graph.getNodes().size();
        if (count == 0) {
            return positions;
        }

        int centerX = Math.max(getWidth() / 2, 1);
        int centerY = Math.max(getHeight() / 2, 1);
        int radius = Math.max(Math.min(getWidth(), getHeight()) / 2 - 90, 120);
        int index = 0;

        for (OntologyGraph.Node node : graph.getNodes()) {
            double angle = (Math.PI * 2 * index) / count;
            int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            int y = centerY + (int) Math.round(Math.sin(angle) * radius);
            positions.put(node.id, new Point(x, y));
            index++;
        }

        return positions;
    }

    private void paintEdges(Graphics2D g, Map<String, Point> positions) {
        g.setStroke(new BasicStroke(1.6f));

        for (OntologyGraph.Edge edge : graph.getEdges()) {
            Point source = positions.get(edge.source);
            Point target = positions.get(edge.target);
            if (source == null || target == null) {
                continue;
            }

            g.setColor(edge.type == OntologyGraph.EdgeType.SUBCLASS ? SUBCLASS_EDGE_COLOR : PROPERTY_EDGE_COLOR);
            g.draw(new Line2D.Double(source.x, source.y, target.x, target.y));
        }
    }

    private void paintNodes(Graphics2D g, Map<String, Point> positions) {
        FontMetrics metrics = g.getFontMetrics();

        for (OntologyGraph.Node node : graph.getNodes()) {
            Point point = positions.get(node.id);
            if (point == null) {
                continue;
            }

            Color fill = node.type == OntologyGraph.NodeType.CLASS ? CLASS_COLOR : PROPERTY_COLOR;
            g.setColor(fill);
            g.fillOval(point.x - NODE_RADIUS, point.y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);
            g.setColor(Color.WHITE);
            g.drawOval(point.x - NODE_RADIUS, point.y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);

            String label = abbreviate(node.label, 18);
            int labelWidth = metrics.stringWidth(label);
            g.drawString(label, point.x - labelWidth / 2, point.y + 4);
        }
    }

    private static String abbreviate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "...";
    }

    private static final class Point {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
