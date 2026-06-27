package com.vaimee.protege.graph;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OntologyGraphPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(OntologyGraphPanel.class);

    private static final Color CLASS_COLOR = new Color(15, 62, 101);
    private static final Color MESSAGE_COLOR = new Color(78, 103, 126);
    private static final Color SUBCLASS_EDGE_COLOR = new Color(78, 103, 126);
    private static final Color PROPERTY_EDGE_COLOR = new Color(16, 177, 216, 180);
    private static final int NODE_WIDTH = 140;
    private static final int NODE_HEIGHT = 44;
    private static final int MARGIN = 70;

    private OntologyGraph graph = new OntologyGraph();
    private Map<String, GephiGraphLayout.Position> positions = Collections.emptyMap();
    private final Map<String, Point> manualPositions = new HashMap<>();
    private Map<String, Point> screenPositions = Collections.emptyMap();
    private String draggedNodeId;
    private int dragOffsetX;
    private int dragOffsetY;
    private String message;

    OntologyGraphPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(1000, 760));
        MouseAdapter dragHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                for (OntologyGraph.Node node : graph.getNodes()) {
                    Point point = screenPositions.get(node.id);
                    if (point != null && containsNode(point, event.getX(), event.getY())) {
                        draggedNodeId = node.id;
                        dragOffsetX = event.getX() - point.x;
                        dragOffsetY = event.getY() - point.y;
                        return;
                    }
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (draggedNodeId != null) {
                    manualPositions.put(draggedNodeId, new Point(event.getX() - dragOffsetX, event.getY() - dragOffsetY));
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                draggedNodeId = null;
            }
        };
        addMouseListener(dragHandler);
        addMouseMotionListener(dragHandler);
    }

    void setGraph(OntologyGraph graph) {
        this.graph = graph;
        this.manualPositions.clear();
        try {
            this.positions = GephiGraphLayout.layout(graph);
        } catch (RuntimeException | LinkageError e) {
            logger.error("Unable to compute Gephi layout for VAIMEE graph", e);
            this.positions = Collections.emptyMap();
        }
        if (!graph.getNodes().isEmpty() && positions.isEmpty()) {
            message = "Gephi layout produced no positions. See Protégé log for details.";
            logger.warn("Graph has {} nodes and {} edges, but no positions were produced", graph.getNodes().size(), graph.getEdges().size());
        } else {
            message = null;
            logger.info("Graph panel received {} nodes, {} edges, {} positions", graph.getNodes().size(), graph.getEdges().size(), positions.size());
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        screenPositions = scalePositions();
        if (message != null) {
            paintMessage(g, message);
            g.dispose();
            return;
        }
        paintEdges(g, screenPositions);
        paintNodes(g, screenPositions);
        logger.debug("Painted VAIMEE graph: {} screen positions, panel size {}x{}", screenPositions.size(), getWidth(), getHeight());
        g.dispose();
    }

    private Map<String, Point> scalePositions() {
        Map<String, Point> scaled = new HashMap<>();
        if (positions.isEmpty()) {
            return scaled;
        }

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (GephiGraphLayout.Position position : positions.values()) {
            minX = Math.min(minX, position.x);
            maxX = Math.max(maxX, position.x);
            minY = Math.min(minY, position.y);
            maxY = Math.max(maxY, position.y);
        }

        double graphWidth = Math.max(maxX - minX, 1.0);
        double graphHeight = Math.max(maxY - minY, 1.0);
        double drawableWidth = Math.max(getWidth() - MARGIN * 2.0, 1.0);
        double drawableHeight = Math.max(getHeight() - MARGIN * 2.0, 1.0);
        logger.debug(
                "Scaling {} Gephi positions: bounds x=[{}, {}], y=[{}, {}], drawable={}x{}",
                positions.size(), minX, maxX, minY, maxY, drawableWidth, drawableHeight
        );

        for (Map.Entry<String, GephiGraphLayout.Position> entry : positions.entrySet()) {
            int x = MARGIN + (int) Math.round(((entry.getValue().x - minX) / graphWidth) * drawableWidth);
            int y = MARGIN + (int) Math.round(((entry.getValue().y - minY) / graphHeight) * drawableHeight);
            scaled.put(entry.getKey(), new Point(x, y));
        }

        scaled.putAll(manualPositions);

        return scaled;
    }

    private void paintMessage(Graphics2D g, String text) {
        g.setColor(MESSAGE_COLOR);
        FontMetrics metrics = g.getFontMetrics();
        int x = Math.max((getWidth() - metrics.stringWidth(text)) / 2, MARGIN);
        int y = Math.max(getHeight() / 2, MARGIN);
        g.drawString(text, x, y);
    }

    private void paintEdges(Graphics2D g, Map<String, Point> screenPositions) {
        g.setStroke(new BasicStroke(1.4f));

        for (OntologyGraph.Edge edge : graph.getEdges()) {
            Point source = screenPositions.get(edge.source);
            Point target = screenPositions.get(edge.target);
            if (source == null || target == null) {
                continue;
            }

            g.setColor(edge.type == OntologyGraph.EdgeType.SUBCLASS ? SUBCLASS_EDGE_COLOR : PROPERTY_EDGE_COLOR);
            g.draw(new Line2D.Double(source.x, source.y, target.x, target.y));
            if (edge.type == OntologyGraph.EdgeType.PROPERTY) {
                g.drawString(abbreviate(edge.label, 24), (source.x + target.x) / 2, (source.y + target.y) / 2);
            }
        }
    }

    private void paintNodes(Graphics2D g, Map<String, Point> screenPositions) {
        FontMetrics metrics = g.getFontMetrics();

        for (OntologyGraph.Node node : graph.getNodes()) {
            Point point = screenPositions.get(node.id);
            if (point == null) {
                continue;
            }

            int x = point.x - NODE_WIDTH / 2;
            int y = point.y - NODE_HEIGHT / 2;
            g.setColor(CLASS_COLOR);
            g.fillRoundRect(x, y, NODE_WIDTH, NODE_HEIGHT, 18, 18);
            g.setColor(Color.WHITE);
            g.drawRoundRect(x, y, NODE_WIDTH, NODE_HEIGHT, 18, 18);

            String label = abbreviate(node.label, 22);
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

    private static boolean containsNode(Point point, int x, int y) {
        return x >= point.x - NODE_WIDTH / 2
                && x <= point.x + NODE_WIDTH / 2
                && y >= point.y - NODE_HEIGHT / 2
                && y <= point.y + NODE_HEIGHT / 2;
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
