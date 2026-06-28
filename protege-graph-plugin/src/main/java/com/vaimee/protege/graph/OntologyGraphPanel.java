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
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OntologyGraphPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(OntologyGraphPanel.class);

    private static final Color CLASS_COLOR = new Color(15, 62, 101);
    private static final Color MESSAGE_COLOR = new Color(78, 103, 126);
    private static final Color SUBCLASS_EDGE_COLOR = new Color(78, 103, 126);
    private static final Color PROPERTY_EDGE_COLOR = new Color(16, 177, 216, 180);
    private static final int NODE_MIN_WIDTH = 180;
    private static final int NODE_MAX_WIDTH = 360;
    private static final int NODE_MIN_HEIGHT = 48;
    private static final int ANNOTATION_LINE_HEIGHT = 16;
    private static final int ANNOTATION_BOTTOM_GAP = 16;
    private static final int PROPERTY_LINE_HEIGHT = 18;
    private static final int NODE_PADDING = 12;
    private static final int TABLE_GAP = 14;
    private static final int MARGIN = 70;
    private static final int ARROW_SIZE = 10;
    private static final int EDGE_LABEL_PADDING = 4;
    private static final int EDGE_LABEL_OFFSET = 6;
    private static final int COLLISION_PADDING = 28;
    private static final int FIT_PADDING = 36;
    private static final int OVERLAP_ITERATIONS = 80;
    private static final int ROUTE_OFFSET = 90;
    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 3.5;

    private OntologyGraph graph = new OntologyGraph();
    private Map<String, Color> namespaceColors = Collections.emptyMap();
    private Set<String> visibleNamespaces = Collections.emptySet();
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
    private boolean fitToViewOnNextPaint;
    private String message;

    OntologyGraphPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(1000, 760));
        MouseAdapter dragHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                Point worldPoint = toWorld(event.getX(), event.getY());
                for (OntologyGraph.Node node : graph.getNodes()) {
                    if (!isVisible(node)) {
                        continue;
                    }
                    Point point = worldPositions.get(node.id);
                    FontMetrics metrics = getFontMetrics(getFont());
                    if (point != null && containsNode(point, worldPoint.x, worldPoint.y, nodeWidth(node, metrics), nodeHeight(node, metrics))) {
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
        this.visibleNamespaces = namespaceColors.keySet();
        resetView();
    }

    void setVisibleNamespaces(Set<String> visibleNamespaces) {
        this.visibleNamespaces = visibleNamespaces;
        draggedNodeId = null;
        panning = false;
        fitToViewOnNextPaint = true;
        repaint();
    }

    void resetView() {
        manualPositions.clear();
        zoom = 1.0;
        panX = 0.0;
        panY = 0.0;
        fitToViewOnNextPaint = true;
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
        if (fitToViewOnNextPaint) {
            fitVisibleGraphToPanel(g.getFontMetrics(), worldPositions);
            fitToViewOnNextPaint = false;
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
        int verticalMargin = Math.max(MARGIN, maxNodeHeight(metrics) / 2 + 24);

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (Map.Entry<String, GephiGraphLayout.Position> entry : positions.entrySet()) {
            if (!isVisible(entry.getKey())) {
                continue;
            }
            GephiGraphLayout.Position position = entry.getValue();
            minX = Math.min(minX, position.x);
            maxX = Math.max(maxX, position.x);
            minY = Math.min(minY, position.y);
            maxY = Math.max(maxY, position.y);
        }

        if (minX == Double.POSITIVE_INFINITY) {
            return scaled;
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
            if (!isVisible(entry.getKey())) {
                continue;
            }
            int x = horizontalMargin + (int) Math.round(((entry.getValue().x - minX) / graphWidth) * drawableWidth);
            int y = verticalMargin + (int) Math.round(((entry.getValue().y - minY) / graphHeight) * drawableHeight);
            scaled.put(entry.getKey(), new Point(x, y));
        }

        for (Map.Entry<String, Point> entry : manualPositions.entrySet()) {
            if (isVisible(entry.getKey())) {
                scaled.put(entry.getKey(), entry.getValue());
            }
        }

        avoidNodeOverlaps(scaled, getFontMetrics(getFont()));
        for (Map.Entry<String, Point> entry : manualPositions.entrySet()) {
            if (isVisible(entry.getKey())) {
                scaled.put(entry.getKey(), entry.getValue());
            }
        }

        return scaled;
    }

    private void avoidNodeOverlaps(Map<String, Point> points, FontMetrics metrics) {
        for (int iteration = 0; iteration < OVERLAP_ITERATIONS; iteration++) {
            boolean moved = false;
            OntologyGraph.Node[] nodes = graph.getNodes().stream()
                    .filter(this::isVisible)
                    .toArray(OntologyGraph.Node[]::new);

            for (int i = 0; i < nodes.length; i++) {
                for (int j = i + 1; j < nodes.length; j++) {
                    Point a = points.get(nodes[i].id);
                    Point b = points.get(nodes[j].id);
                    if (a == null || b == null) {
                        continue;
                    }

                    Rectangle2D aBounds = visualBoundsForNode(nodes[i], a, metrics, points);
                    Rectangle2D bBounds = visualBoundsForNode(nodes[j], b, metrics, points);
                    double minDx = (aBounds.getWidth() + bBounds.getWidth()) / 2.0 + COLLISION_PADDING;
                    double minDy = (aBounds.getHeight() + bBounds.getHeight()) / 2.0 + COLLISION_PADDING;
                    double dx = b.x - a.x;
                    double dy = b.y - a.y;
                    double overlapX = minDx - Math.abs(dx);
                    double overlapY = minDy - Math.abs(dy);

                    if (overlapX > 0.0 && overlapY > 0.0) {
                        if (overlapX < overlapY) {
                            double shift = overlapX / 2.0 + 1.0;
                            double direction = dx >= 0.0 ? 1.0 : -1.0;
                            points.put(nodes[i].id, new Point(a.x - shift * direction, a.y));
                            points.put(nodes[j].id, new Point(b.x + shift * direction, b.y));
                        } else {
                            double shift = overlapY / 2.0 + 1.0;
                            double direction = dy >= 0.0 ? 1.0 : -1.0;
                            points.put(nodes[i].id, new Point(a.x, a.y - shift * direction));
                            points.put(nodes[j].id, new Point(b.x, b.y + shift * direction));
                        }
                        moved = true;
                    }
                }
            }

            if (!moved) {
                return;
            }
        }
    }

    private Rectangle2D visualBoundsForNode(OntologyGraph.Node node, Point point, FontMetrics metrics, Map<String, Point> points) {
        Rectangle2D bounds = new Rectangle2D.Double(
                point.x - nodeWidth(node, metrics) / 2.0,
                point.y - nodeHeight(node, metrics) / 2.0,
                nodeWidth(node, metrics),
                nodeHeight(node, metrics)
        );

        for (OntologyGraph.Edge edge : graph.getEdges()) {
            if (edge.type != OntologyGraph.EdgeType.PROPERTY || !node.id.equals(edge.source)) {
                continue;
            }
            Point target = points.get(edge.target);
            OntologyGraph.Node targetNode = graph.getNode(edge.target);
            if (target == null || targetNode == null || !isVisible(targetNode)) {
                continue;
            }
            EdgeGeometry geometry = edgeGeometry(point, node, target, targetNode, metrics);
            bounds = union(bounds, propertyLabelBounds(edge.label, geometry, metrics));
        }

        return bounds;
    }

    private void fitVisibleGraphToPanel(FontMetrics metrics, Map<String, Point> points) {
        Rectangle2D bounds = contentBounds(metrics, points);
        if (bounds == null || bounds.getWidth() <= 0.0 || bounds.getHeight() <= 0.0) {
            return;
        }

        double availableWidth = Math.max(getWidth() - FIT_PADDING * 2.0, 1.0);
        double availableHeight = Math.max(getHeight() - FIT_PADDING * 2.0, 1.0);
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.min(availableWidth / bounds.getWidth(), availableHeight / bounds.getHeight())));
        panX = FIT_PADDING - bounds.getX() * zoom + (availableWidth - bounds.getWidth() * zoom) / 2.0;
        panY = FIT_PADDING - bounds.getY() * zoom + (availableHeight - bounds.getHeight() * zoom) / 2.0;
        logger.info("Reset graph view fitted to content bounds {}x{} with zoom {}", bounds.getWidth(), bounds.getHeight(), zoom);
    }

    private Rectangle2D contentBounds(FontMetrics metrics, Map<String, Point> points) {
        Rectangle2D bounds = null;
        for (OntologyGraph.Node node : graph.getNodes()) {
            if (!isVisible(node)) {
                continue;
            }
            Point point = points.get(node.id);
            if (point == null) {
                continue;
            }
            Rectangle2D nodeBounds = new Rectangle2D.Double(
                    point.x - nodeWidth(node, metrics) / 2.0,
                    point.y - nodeHeight(node, metrics) / 2.0,
                    nodeWidth(node, metrics),
                    nodeHeight(node, metrics)
            );
            bounds = union(bounds, nodeBounds);
        }

        for (OntologyGraph.Edge edge : graph.getEdges()) {
            if (edge.type != OntologyGraph.EdgeType.PROPERTY) {
                continue;
            }
            Point source = points.get(edge.source);
            Point target = points.get(edge.target);
            OntologyGraph.Node sourceNode = graph.getNode(edge.source);
            OntologyGraph.Node targetNode = graph.getNode(edge.target);
            if (source == null || target == null || sourceNode == null || targetNode == null || !isVisible(sourceNode) || !isVisible(targetNode)) {
                continue;
            }
            EdgeGeometry geometry = edgeGeometry(source, sourceNode, target, targetNode, metrics);
            bounds = union(bounds, propertyLabelBounds(edge.label, geometry, metrics));
        }
        return bounds;
    }

    private static Rectangle2D union(Rectangle2D current, Rectangle2D addition) {
        if (current == null) {
            return addition;
        }
        Rectangle2D.union(current, addition, current);
        return current;
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
            if (source == null || target == null || sourceNode == null || targetNode == null || !isVisible(sourceNode) || !isVisible(targetNode)) {
                continue;
            }

            g.setColor(edge.type == OntologyGraph.EdgeType.SUBCLASS ? SUBCLASS_EDGE_COLOR : PROPERTY_EDGE_COLOR);
            EdgeGeometry edgeGeometry = drawDirectedEdge(g, source, sourceNode, target, targetNode, metrics, worldPositions);
            if (edge.type == OntologyGraph.EdgeType.PROPERTY) {
                drawPropertyLabel(g, edge.label, edgeGeometry, metrics);
            }
        }
    }

    private EdgeGeometry drawDirectedEdge(Graphics2D g, Point source, OntologyGraph.Node sourceNode, Point target, OntologyGraph.Node targetNode, FontMetrics metrics, Map<String, Point> points) {
        EdgeGeometry geometry = edgeGeometry(source, sourceNode, target, targetNode, metrics);
        Point bend = routeBend(geometry, sourceNode, targetNode, metrics, points);

        if (bend == null) {
            g.draw(new Line2D.Double(geometry.x, geometry.y, geometry.endX, geometry.endY));
        } else {
            g.draw(new Line2D.Double(geometry.x, geometry.y, bend.x, bend.y));
            g.draw(new Line2D.Double(bend.x, bend.y, geometry.endX, geometry.endY));
        }

        AffineTransform previousTransform = g.getTransform();
        g.translate(geometry.endX, geometry.endY);
        double arrowDx = bend == null ? geometry.dx : geometry.endX - bend.x;
        double arrowDy = bend == null ? geometry.dy : geometry.endY - bend.y;
        g.rotate(Math.atan2(arrowDy, arrowDx));
        g.fillPolygon(
                new int[]{0, -ARROW_SIZE, -ARROW_SIZE},
                new int[]{0, -ARROW_SIZE / 2, ARROW_SIZE / 2},
                3
        );
        g.setTransform(previousTransform);

        return geometry;
    }

    private Point routeBend(EdgeGeometry geometry, OntologyGraph.Node sourceNode, OntologyGraph.Node targetNode, FontMetrics metrics, Map<String, Point> points) {
        Line2D direct = new Line2D.Double(geometry.x, geometry.y, geometry.endX, geometry.endY);
        boolean intersectsNode = false;
        for (OntologyGraph.Node node : graph.getNodes()) {
            if (!isVisible(node) || node.id.equals(sourceNode.id) || node.id.equals(targetNode.id)) {
                continue;
            }
            Point point = points.get(node.id);
            if (point != null && nodeBounds(node, point, metrics).intersectsLine(direct)) {
                intersectsNode = true;
                break;
            }
        }
        if (!intersectsNode) {
            return null;
        }

        double length = Math.max(Math.sqrt(geometry.dx * geometry.dx + geometry.dy * geometry.dy), 1.0);
        double normalX = -geometry.dy / length;
        double normalY = geometry.dx / length;
        return new Point((geometry.x + geometry.endX) / 2.0 + normalX * ROUTE_OFFSET, (geometry.y + geometry.endY) / 2.0 + normalY * ROUTE_OFFSET);
    }

    private static EdgeGeometry edgeGeometry(Point source, OntologyGraph.Node sourceNode, Point target, OntologyGraph.Node targetNode, FontMetrics metrics) {
        double dx = target.x - source.x;
        double dy = target.y - source.y;
        int sourceWidth = nodeWidth(sourceNode, metrics);
        int sourceHeight = nodeHeight(sourceNode, metrics);
        double sourceScale = edgeIntersectionScale(dx, dy, sourceWidth, sourceHeight);
        double targetScale = edgeIntersectionScale(dx, dy, nodeWidth(targetNode, metrics), nodeHeight(targetNode, metrics));
        double startX = source.x + dx * sourceScale;
        double startY = source.y + dy * sourceScale;
        double endX = target.x - dx * targetScale;
        double endY = target.y - dy * targetScale;
        return new EdgeGeometry(startX, startY, endX, endY, dx, dy, edgeSide(dx, dy, sourceWidth, sourceHeight));
    }

    private static void drawPropertyLabel(Graphics2D g, String label, EdgeGeometry edgeGeometry, FontMetrics metrics) {
        Rectangle2D bounds = propertyLabelBounds(label, edgeGeometry, metrics);
        int x = (int) Math.round(bounds.getX()) + EDGE_LABEL_PADDING;
        int y = (int) Math.round(bounds.getY());

        Color previousColor = g.getColor();
        g.setColor(new Color(255, 255, 255, 230));
        g.fillRoundRect((int) Math.round(bounds.getX()), y, (int) Math.round(bounds.getWidth()), (int) Math.round(bounds.getHeight()), 6, 6);
        g.setColor(PROPERTY_EDGE_COLOR.darker());
        g.drawString(label, x, y + metrics.getAscent());
        g.setColor(previousColor);
    }

    private static Rectangle2D propertyLabelBounds(String label, EdgeGeometry edgeGeometry, FontMetrics metrics) {
        int labelWidth = metrics.stringWidth(label);
        int labelHeight = metrics.getHeight();
        int x;
        int y;

        if (edgeGeometry.side == EdgeSide.RIGHT) {
            x = (int) Math.round(edgeGeometry.x + EDGE_LABEL_OFFSET);
            y = (int) Math.round(edgeGeometry.y - labelHeight / 2.0);
        } else if (edgeGeometry.side == EdgeSide.LEFT) {
            x = (int) Math.round(edgeGeometry.x - EDGE_LABEL_OFFSET - labelWidth);
            y = (int) Math.round(edgeGeometry.y - labelHeight / 2.0);
        } else if (edgeGeometry.side == EdgeSide.TOP) {
            x = (int) Math.round(edgeGeometry.x - labelWidth / 2.0);
            y = (int) Math.round(edgeGeometry.y - EDGE_LABEL_OFFSET - labelHeight);
        } else {
            x = (int) Math.round(edgeGeometry.x - labelWidth / 2.0);
            y = (int) Math.round(edgeGeometry.y + EDGE_LABEL_OFFSET);
        }

        return new Rectangle2D.Double(x - EDGE_LABEL_PADDING, y, labelWidth + EDGE_LABEL_PADDING * 2.0, labelHeight);
    }

    private static EdgeSide edgeSide(double dx, double dy, int width, int height) {
        double scaleX = Math.abs(dx) < 1.0 ? Double.POSITIVE_INFINITY : (width / 2.0) / Math.abs(dx);
        double scaleY = Math.abs(dy) < 1.0 ? Double.POSITIVE_INFINITY : (height / 2.0) / Math.abs(dy);
        if (scaleX < scaleY) {
            return dx >= 0 ? EdgeSide.RIGHT : EdgeSide.LEFT;
        }
        return dy >= 0 ? EdgeSide.BOTTOM : EdgeSide.TOP;
    }

    private static double edgeIntersectionScale(double dx, double dy, int width, int height) {
        double scaleX = Math.abs(dx) < 1.0 ? Double.POSITIVE_INFINITY : (width / 2.0) / Math.abs(dx);
        double scaleY = Math.abs(dy) < 1.0 ? Double.POSITIVE_INFINITY : (height / 2.0) / Math.abs(dy);
        return Math.min(scaleX, scaleY);
    }

    private void paintNodes(Graphics2D g, Map<String, Point> worldPositions) {
        FontMetrics metrics = g.getFontMetrics();

        for (OntologyGraph.Node node : graph.getNodes()) {
            if (!isVisible(node)) {
                continue;
            }
            Point point = worldPositions.get(node.id);
            if (point == null) {
                continue;
            }

            int nodeWidth = nodeWidth(node, metrics);
            int nodeHeight = nodeHeight(node, metrics);
            int x = (int) Math.round(point.x - nodeWidth / 2.0);
            int y = (int) Math.round(point.y - nodeHeight / 2.0);
            g.setColor(namespaceColors.getOrDefault(NamespaceColors.namespaceOf(node.id), CLASS_COLOR));
            g.fillRoundRect(x, y, nodeWidth, nodeHeight, 18, 18);
            g.setColor(Color.WHITE);
            g.drawRoundRect(x, y, nodeWidth, nodeHeight, 18, 18);

            int labelWidth = metrics.stringWidth(node.label);
            g.drawString(node.label, (int) Math.round(point.x - labelWidth / 2.0), y + 20);

            int separatorY = y + headerHeight(node, metrics, nodeWidth);
             if (!node.annotations.isEmpty()) {
                 g.drawLine(x + 10, y + 30, x + nodeWidth - 10, y + 30);
             }
             int annotationY = y + 45;
            for (OntologyGraph.Annotation annotation : node.annotations) {
                for (String line : annotationLines(annotation, metrics, nodeWidth - NODE_PADDING * 2)) {
                    g.drawString(line, x + NODE_PADDING, annotationY);
                    annotationY += ANNOTATION_LINE_HEIGHT;
                }
            }

            g.drawLine(x + 10, separatorY, x + nodeWidth - 10, separatorY);
            if (!node.datatypeProperties.isEmpty()) {
                int nameColumnWidth = datatypeNameColumnWidth(node, metrics);
                int tableTop = separatorY;
                int separatorX = x + NODE_PADDING + nameColumnWidth + TABLE_GAP / 2;
                g.drawLine(separatorX, tableTop, separatorX, y + nodeHeight - 10);
                int propertyY = tableTop + 15;
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

    private static int nodeHeight(OntologyGraph.Node node, FontMetrics metrics) {
        int width = nodeWidth(node, metrics);
        return Math.max(NODE_MIN_HEIGHT, headerHeight(node, metrics, width) + 12 + node.datatypeProperties.size() * PROPERTY_LINE_HEIGHT);
    }

    private static int nodeWidth(OntologyGraph.Node node, FontMetrics metrics) {
        int width = Math.max(NODE_MIN_WIDTH, metrics.stringWidth(node.label) + NODE_PADDING * 2);
        if (!node.datatypeProperties.isEmpty()) {
            int tableWidth = datatypeNameColumnWidth(node, metrics) + datatypeRangeColumnWidth(node, metrics) + TABLE_GAP + NODE_PADDING * 2;
            width = Math.max(width, tableWidth);
        }
        return Math.min(Math.max(width, NODE_MIN_WIDTH), NODE_MAX_WIDTH);
    }

    private static int headerHeight(OntologyGraph.Node node, FontMetrics metrics, int nodeWidth) {
        int lineCount = 0;
        for (OntologyGraph.Annotation annotation : node.annotations) {
            lineCount += annotationLines(annotation, metrics, nodeWidth - NODE_PADDING * 2).size();
        }
        return 30 + lineCount * ANNOTATION_LINE_HEIGHT + ANNOTATION_BOTTOM_GAP;
    }

    private static String annotationLabel(OntologyGraph.Annotation annotation) {
        return annotation.value;
    }

    private static List<String> annotationLines(OntologyGraph.Annotation annotation, FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String text = annotationLabel(annotation);
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (metrics.stringWidth(candidate) <= maxWidth || current.length() == 0) {
                current.setLength(0);
                current.append(candidate);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? Collections.singletonList(text) : lines;
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
            if (!isVisible(node)) {
                continue;
            }
            width = Math.max(width, nodeWidth(node, metrics));
        }
        return width;
    }

    private int maxNodeHeight(FontMetrics metrics) {
        int height = NODE_MIN_HEIGHT;
        for (OntologyGraph.Node node : graph.getNodes()) {
            if (!isVisible(node)) {
                continue;
            }
            height = Math.max(height, nodeHeight(node, metrics));
        }
        return height;
    }

    private static Rectangle2D nodeBounds(OntologyGraph.Node node, Point point, FontMetrics metrics) {
        return new Rectangle2D.Double(point.x - nodeWidth(node, metrics) / 2.0, point.y - nodeHeight(node, metrics) / 2.0, nodeWidth(node, metrics), nodeHeight(node, metrics));
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

    private boolean isVisible(OntologyGraph.Node node) {
        return visibleNamespaces.contains(NamespaceColors.namespaceOf(node.id));
    }

    private boolean isVisible(String nodeId) {
        OntologyGraph.Node node = graph.getNode(nodeId);
        return node != null && isVisible(node);
    }

    private enum EdgeSide {
        TOP,
        RIGHT,
        BOTTOM,
        LEFT
    }

    private static final class EdgeGeometry {
        final double x;
        final double y;
        final double endX;
        final double endY;
        final double dx;
        final double dy;
        final EdgeSide side;

        EdgeGeometry(double x, double y, double endX, double endY, double dx, double dy, EdgeSide side) {
            this.x = x;
            this.y = y;
            this.endX = endX;
            this.endY = endY;
            this.dx = dx;
            this.dy = dy;
            this.side = side;
        }
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
