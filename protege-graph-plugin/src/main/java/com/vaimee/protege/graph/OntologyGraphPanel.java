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
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
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
    private static final int NODE_MIN_WIDTH = 180;
    private static final int NODE_MIN_HEIGHT = 48;
    private static final int PROPERTY_LINE_HEIGHT = 18;
    private static final int NODE_PADDING = 12;
    private static final int TABLE_GAP = 14;
    private static final int MARGIN = 70;
    private static final int ARROW_SIZE = 10;
    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 3.5;

    private OntologyGraph graph = new OntologyGraph();
    private Map<String, Color> namespaceColors = Collections.emptyMap();
    private Map<String, GephiGraphLayout.Position> positions = Collections.emptyMap();
    private final Map<String, Point> manualPositions = new HashMap<>();
    private Map<String, Point> worldPositions = Collections.emptyMap();
    private String draggedNodeId;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean panning;
    private int lastMouseX;
    private int lastMouseY;
    private double zoom = 1.0;
    private double panX;
    private double panY;
    private String message;

    OntologyGraphPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(1000, 760));
        MouseAdapter dragHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                Point worldPoint = toWorld(event.getX(), event.getY());
                for (OntologyGraph.Node node : graph.getNodes()) {
                    Point point = worldPositions.get(node.id);
                    if (point != null && containsNode(point, worldPoint.x, worldPoint.y, nodeWidth(node, getFontMetrics(getFont())), nodeHeight(node))) {
                        draggedNodeId = node.id;
                        dragOffsetX = worldPoint.x - point.x;
                        dragOffsetY = worldPoint.y - point.y;
                        return;
                    }
                }
                panning = true;
                lastMouseX = event.getX();
                lastMouseY = event.getY();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (draggedNodeId != null) {
                    Point worldPoint = toWorld(event.getX(), event.getY());
                    manualPositions.put(draggedNodeId, new Point(worldPoint.x - dragOffsetX, worldPoint.y - dragOffsetY));
                    repaint();
                } else if (panning) {
                    panX += event.getX() - lastMouseX;
                    panY += event.getY() - lastMouseY;
                    lastMouseX = event.getX();
                    lastMouseY = event.getY();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                draggedNodeId = null;
                panning = false;
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                double previousZoom = zoom;
                double factor = Math.pow(1.1, -event.getPreciseWheelRotation());
                zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));

                double worldX = (event.getX() - panX) / previousZoom;
                double worldY = (event.getY() - panY) / previousZoom;
                panX = event.getX() - worldX * zoom;
                panY = event.getY() - worldY * zoom;
                repaint();
            }
        };
        addMouseListener(dragHandler);
        addMouseMotionListener(dragHandler);
        addMouseWheelListener(dragHandler);
    }

    void setGraph(OntologyGraph graph, Map<String, Color> namespaceColors) {
        this.graph = graph;
        this.namespaceColors = namespaceColors;
        resetView();
    }

    void resetView() {
        manualPositions.clear();
        zoom = 1.0;
        panX = 0.0;
        panY = 0.0;
        positions = ForceGraphLayout.layout(graph);
        if (!graph.getNodes().isEmpty() && positions.isEmpty()) {
            message = "Graph layout produced no positions. See Protégé log for details.";
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

        worldPositions = scalePositions();
        if (message != null) {
            paintMessage(g, message);
            g.dispose();
            return;
        }
        g.translate(panX, panY);
        g.scale(zoom, zoom);
        paintEdges(g, worldPositions);
        paintNodes(g, worldPositions);
        logger.debug("Painted VAIMEE graph: {} world positions, panel size {}x{}, zoom {}", worldPositions.size(), getWidth(), getHeight(), zoom);
        g.dispose();
    }

    private Map<String, Point> scalePositions() {
        Map<String, Point> scaled = new HashMap<>();
        if (positions.isEmpty()) {
            return scaled;
        }

        FontMetrics metrics = getFontMetrics(getFont());
        int horizontalMargin = Math.max(MARGIN, maxNodeWidth(metrics) / 2 + 24);
        int verticalMargin = Math.max(MARGIN, maxNodeHeight() / 2 + 24);

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
        double drawableWidth = Math.max(getWidth() - horizontalMargin * 2.0, 1.0);
        double drawableHeight = Math.max(getHeight() - verticalMargin * 2.0, 1.0);
        logger.debug(
                "Scaling {} Gephi positions: bounds x=[{}, {}], y=[{}, {}], drawable={}x{}",
                positions.size(), minX, maxX, minY, maxY, drawableWidth, drawableHeight
        );

        for (Map.Entry<String, GephiGraphLayout.Position> entry : positions.entrySet()) {
            int x = horizontalMargin + (int) Math.round(((entry.getValue().x - minX) / graphWidth) * drawableWidth);
            int y = verticalMargin + (int) Math.round(((entry.getValue().y - minY) / graphHeight) * drawableHeight);
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

    private void paintEdges(Graphics2D g, Map<String, Point> worldPositions) {
        g.setStroke(new BasicStroke(1.4f));
        FontMetrics metrics = g.getFontMetrics();

        for (OntologyGraph.Edge edge : graph.getEdges()) {
            Point source = worldPositions.get(edge.source);
            Point target = worldPositions.get(edge.target);
            OntologyGraph.Node sourceNode = graph.getNode(edge.source);
            OntologyGraph.Node targetNode = graph.getNode(edge.target);
            if (source == null || target == null || sourceNode == null || targetNode == null) {
                continue;
            }

            g.setColor(edge.type == OntologyGraph.EdgeType.SUBCLASS ? SUBCLASS_EDGE_COLOR : PROPERTY_EDGE_COLOR);
            drawDirectedEdge(g, source, sourceNode, target, targetNode, metrics);
            if (edge.type == OntologyGraph.EdgeType.PROPERTY) {
                g.drawString(edge.label, (int) Math.round((source.x + target.x) / 2.0), (int) Math.round((source.y + target.y) / 2.0));
            }
        }
    }

    private static void drawDirectedEdge(Graphics2D g, Point source, OntologyGraph.Node sourceNode, Point target, OntologyGraph.Node targetNode, FontMetrics metrics) {
        double dx = target.x - source.x;
        double dy = target.y - source.y;
        double distance = Math.max(Math.sqrt(dx * dx + dy * dy), 1.0);
        double sourceScale = edgeIntersectionScale(dx, dy, nodeWidth(sourceNode, metrics), nodeHeight(sourceNode));
        double targetScale = edgeIntersectionScale(dx, dy, nodeWidth(targetNode, metrics), nodeHeight(targetNode));
        double startX = source.x + dx * sourceScale;
        double startY = source.y + dy * sourceScale;
        double endX = target.x - dx * targetScale;
        double endY = target.y - dy * targetScale;

        g.draw(new Line2D.Double(startX, startY, endX, endY));

        AffineTransform previousTransform = g.getTransform();
        g.translate(endX, endY);
        g.rotate(Math.atan2(dy, dx));
        g.fillPolygon(
                new int[]{0, -ARROW_SIZE, -ARROW_SIZE},
                new int[]{0, -ARROW_SIZE / 2, ARROW_SIZE / 2},
                3
        );
        g.setTransform(previousTransform);
    }

    private static double edgeIntersectionScale(double dx, double dy, int width, int height) {
        double scaleX = Math.abs(dx) < 1.0 ? Double.POSITIVE_INFINITY : (width / 2.0) / Math.abs(dx);
        double scaleY = Math.abs(dy) < 1.0 ? Double.POSITIVE_INFINITY : (height / 2.0) / Math.abs(dy);
        return Math.min(scaleX, scaleY);
    }

    private void paintNodes(Graphics2D g, Map<String, Point> worldPositions) {
        FontMetrics metrics = g.getFontMetrics();

        for (OntologyGraph.Node node : graph.getNodes()) {
            Point point = worldPositions.get(node.id);
            if (point == null) {
                continue;
            }

            int nodeWidth = nodeWidth(node, metrics);
            int nodeHeight = nodeHeight(node);
            int x = (int) Math.round(point.x - nodeWidth / 2.0);
            int y = (int) Math.round(point.y - nodeHeight / 2.0);
            g.setColor(namespaceColors.getOrDefault(NamespaceColors.namespaceOf(node.id), CLASS_COLOR));
            g.fillRoundRect(x, y, nodeWidth, nodeHeight, 18, 18);
            g.setColor(Color.WHITE);
            g.drawRoundRect(x, y, nodeWidth, nodeHeight, 18, 18);

            int labelWidth = metrics.stringWidth(node.label);
            g.drawString(node.label, (int) Math.round(point.x - labelWidth / 2.0), y + 20);

            if (!node.datatypeProperties.isEmpty()) {
                int nameColumnWidth = datatypeNameColumnWidth(node, metrics);
                int tableTop = y + 30;
                int separatorX = x + NODE_PADDING + nameColumnWidth + TABLE_GAP / 2;
                g.drawLine(x + 10, tableTop, x + nodeWidth - 10, tableTop);
                g.drawLine(separatorX, tableTop, separatorX, y + nodeHeight - 10);
                int propertyY = y + 45;
                for (OntologyGraph.DatatypeProperty property : node.datatypeProperties) {
                    g.drawString(property.label, x + NODE_PADDING, propertyY);
                    if (property.datatypeLabel != null && !property.datatypeLabel.trim().isEmpty()) {
                        g.drawString(property.datatypeLabel, separatorX + TABLE_GAP / 2, propertyY);
                    }
                    propertyY += PROPERTY_LINE_HEIGHT;
                }
            }
        }
    }

    private static int nodeHeight(OntologyGraph.Node node) {
        if (node.datatypeProperties.isEmpty()) {
            return NODE_MIN_HEIGHT;
        }
        return NODE_MIN_HEIGHT + 12 + node.datatypeProperties.size() * PROPERTY_LINE_HEIGHT;
    }

    private static int nodeWidth(OntologyGraph.Node node, FontMetrics metrics) {
        int width = Math.max(NODE_MIN_WIDTH, metrics.stringWidth(node.label) + NODE_PADDING * 2);
        if (!node.datatypeProperties.isEmpty()) {
            int tableWidth = datatypeNameColumnWidth(node, metrics) + datatypeRangeColumnWidth(node, metrics) + TABLE_GAP + NODE_PADDING * 2;
            width = Math.max(width, tableWidth);
        }
        return width;
    }

    private static int datatypeNameColumnWidth(OntologyGraph.Node node, FontMetrics metrics) {
        int width = 0;
        for (OntologyGraph.DatatypeProperty property : node.datatypeProperties) {
            width = Math.max(width, metrics.stringWidth(property.label));
        }
        return width;
    }

    private static int datatypeRangeColumnWidth(OntologyGraph.Node node, FontMetrics metrics) {
        int width = 0;
        for (OntologyGraph.DatatypeProperty property : node.datatypeProperties) {
            if (property.datatypeLabel != null) {
                width = Math.max(width, metrics.stringWidth(property.datatypeLabel));
            }
        }
        return width;
    }

    private int maxNodeWidth(FontMetrics metrics) {
        int width = NODE_MIN_WIDTH;
        for (OntologyGraph.Node node : graph.getNodes()) {
            width = Math.max(width, nodeWidth(node, metrics));
        }
        return width;
    }

    private int maxNodeHeight() {
        int height = NODE_MIN_HEIGHT;
        for (OntologyGraph.Node node : graph.getNodes()) {
            height = Math.max(height, nodeHeight(node));
        }
        return height;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "...";
    }

    private static boolean containsNode(Point point, double x, double y, int nodeWidth, int nodeHeight) {
        return x >= point.x - nodeWidth / 2.0
                && x <= point.x + nodeWidth / 2.0
                && y >= point.y - nodeHeight / 2
                && y <= point.y + nodeHeight / 2;
    }

    private Point toWorld(int x, int y) {
        return new Point((x - panX) / zoom, (y - panY) / zoom);
    }

    private static final class Point {
        final double x;
        final double y;

        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}
