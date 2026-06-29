package com.vaimee.protege.graph;

import javax.swing.JPanel;
import javax.swing.Timer;
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
import java.awt.geom.QuadCurve2D;
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
    private static final Color PROPERTY_NODE_COLOR = new Color(16, 177, 216);
    private static final Color MESSAGE_COLOR = new Color(78, 103, 126);
    private static final Color SUBCLASS_EDGE_COLOR = new Color(78, 103, 126);
    private static final Color PROPERTY_EDGE_COLOR = new Color(16, 177, 216, 180);
    private static final int NODE_MIN_WIDTH = 180;
    private static final int NODE_MAX_WIDTH = 360;
    private static final int NODE_MIN_HEIGHT = 48;
    private static final int PROPERTY_NODE_HEIGHT = 28;
    private static final int PROPERTY_NODE_MIN_WIDTH = 80;
    private static final int PROPERTY_NODE_PADDING = 16;
    private static final int ANNOTATION_LINE_HEIGHT = 16;
    private static final int ANNOTATION_BOTTOM_GAP = 16;
    private static final int PROPERTY_LINE_HEIGHT = 18;
    private static final int NODE_PADDING = 12;
    private static final int TABLE_GAP = 14;
    private static final int MARGIN = 70;
    private static final int ARROW_SIZE = 10;
    private static final int EDGE_LABEL_PADDING = 4;
    private static final int FIT_PADDING = 36;
    private static final int ROUTE_OFFSET = 90;
    private static final int CURVE_OFFSET = 30;
    private static final int STEPS_PER_FRAME = 3;
    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 3.5;

    private OntologyGraph graph = new OntologyGraph();
    private Map<String, Color> namespaceColors = Collections.emptyMap();
    private Set<String> visibleNamespaces = Collections.emptySet();
    private ForceGraphLayout simulation;
    private Timer animationTimer;
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

        MouseAdapter handler = new MouseAdapter() {
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
                        if (simulation != null) {
                            simulation.pinNode(node.id, point.x, point.y);
                            if (!animationTimer.isRunning()) {
                                animationTimer.start();
                            }
                        }
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
                    if (simulation != null) {
                        simulation.pinNode(draggedNodeId, worldPoint.x - dragOffsetX, worldPoint.y - dragOffsetY);
                    }
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
                if (draggedNodeId != null && simulation != null) {
                    simulation.unpinNode();
                }
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
        addMouseListener(handler);
        addMouseMotionListener(handler);
        addMouseWheelListener(handler);

        animationTimer = new Timer(16, event -> {
            if (simulation != null) {
                for (int i = 0; i < STEPS_PER_FRAME; i++) {
                    simulation.step();
                }
                repaint();
                if (simulation.isSettled() && draggedNodeId == null) {
                    animationTimer.stop();
                    logger.debug("Force simulation settled");
                }
            }
        });
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
        animationTimer.stop();
        zoom = 1.0;
        panX = 0.0;
        panY = 0.0;
        simulation = new ForceGraphLayout(graph);
        FontMetrics metrics = getFontMetrics(getFont());
        Map<String, double[]> sizes = new HashMap<>();
        for (OntologyGraph.Node node : graph.getNodes()) {
            sizes.put(node.id, new double[]{nodeWidth(node, metrics), nodeHeight(node, metrics)});
        }
        simulation.setNodeSizes(sizes);
        for (int i = 0; i < 2000 && !simulation.isSettled(); i++) {
            simulation.step();
        }
        fitToViewOnNextPaint = true;
        message = null;
        logger.info("Graph panel reset: {} nodes, {} edges", graph.getNodes().size(), graph.getEdges().size());
        repaint();
    }

    void dispose() {
        animationTimer.stop();
        simulation = null;
    }

    void setCharge(double value) {
        if (simulation != null) {
            simulation.setCharge(value);
            reheatSimulation();
        }
    }

    void setLinkDistance(double value) {
        if (simulation != null) {
            simulation.setLinkDistance(value);
            reheatSimulation();
        }
    }

    void setLinkStrength(double value) {
        if (simulation != null) {
            simulation.setLinkStrength(value);
            reheatSimulation();
        }
    }

    void setGravity(double value) {
        if (simulation != null) {
            simulation.setGravity(value);
            reheatSimulation();
        }
    }

    private void reheatSimulation() {
        if (simulation != null) {
            simulation.pinNode("__reheat__", 0, 0);
            simulation.unpinNode();
            if (!animationTimer.isRunning()) {
                animationTimer.start();
            }
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        worldPositions = simulationPoints();
        if (message != null) {
            paintMessage(g, message);
            g.dispose();
            return;
        }
        if (fitToViewOnNextPaint && !worldPositions.isEmpty()) {
            fitVisibleGraphToPanel(g.getFontMetrics(), worldPositions);
            fitToViewOnNextPaint = false;
        }
        g.translate(panX, panY);
        g.scale(zoom, zoom);
        paintEdges(g, worldPositions);
        paintNodes(g, worldPositions);
        g.dispose();
    }

    private Map<String, Point> simulationPoints() {
        Map<String, Point> points = new HashMap<>();
        if (simulation == null) {
            return points;
        }
        Map<String, GephiGraphLayout.Position> positions = simulation.getPositions();
        for (Map.Entry<String, GephiGraphLayout.Position> entry : positions.entrySet()) {
            if (!isVisible(entry.getKey())) {
                continue;
            }
            points.put(entry.getKey(), new Point(entry.getValue().x, entry.getValue().y));
        }
        return points;
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
            EdgeGeometry geometry = edgeGeometry(source, sourceNode, target, targetNode, metrics);
            Point ctrl = curveControl(geometry, sourceNode, targetNode, metrics, worldPositions);
            drawCurveEdge(g, geometry, ctrl);

            if (edge.label != null && !edge.label.isEmpty()) {
                drawEdgeLabel(g, edge.label, geometry, ctrl, metrics);
            }
        }
    }

    private Point curveControl(EdgeGeometry geometry, OntologyGraph.Node sourceNode, OntologyGraph.Node targetNode, FontMetrics metrics, Map<String, Point> points) {
        double midX = (geometry.x + geometry.endX) / 2.0;
        double midY = (geometry.y + geometry.endY) / 2.0;
        double length = Math.max(Math.sqrt(geometry.dx * geometry.dx + geometry.dy * geometry.dy), 1.0);
        double normalX = -geometry.dy / length;
        double normalY = geometry.dx / length;

        Line2D direct = new Line2D.Double(geometry.x, geometry.y, geometry.endX, geometry.endY);
        for (OntologyGraph.Node node : graph.getNodes()) {
            if (!isVisible(node) || node.id.equals(sourceNode.id) || node.id.equals(targetNode.id)) {
                continue;
            }
            Point point = points.get(node.id);
            if (point != null && nodeBounds(node, point, metrics).intersectsLine(direct)) {
                return new Point(midX + normalX * ROUTE_OFFSET, midY + normalY * ROUTE_OFFSET);
            }
        }

        return new Point(midX + normalX * CURVE_OFFSET, midY + normalY * CURVE_OFFSET);
    }

    private void drawCurveEdge(Graphics2D g, EdgeGeometry geometry, Point ctrl) {
        g.draw(new QuadCurve2D.Double(geometry.x, geometry.y, ctrl.x, ctrl.y, geometry.endX, geometry.endY));

        double arrowDx = geometry.endX - ctrl.x;
        double arrowDy = geometry.endY - ctrl.y;
        AffineTransform previousTransform = g.getTransform();
        g.translate(geometry.endX, geometry.endY);
        g.rotate(Math.atan2(arrowDy, arrowDx));
        g.fillPolygon(
                new int[]{0, -ARROW_SIZE, -ARROW_SIZE},
                new int[]{0, -ARROW_SIZE / 2, ARROW_SIZE / 2},
                3
        );
        g.setTransform(previousTransform);
    }

    private static void drawEdgeLabel(Graphics2D g, String label, EdgeGeometry geometry, Point ctrl, FontMetrics metrics) {
        double mx = 0.25 * geometry.x + 0.5 * ctrl.x + 0.25 * geometry.endX;
        double my = 0.25 * geometry.y + 0.5 * ctrl.y + 0.25 * geometry.endY;
        int labelWidth = metrics.stringWidth(label);
        int labelHeight = metrics.getHeight();
        int lx = (int) Math.round(mx - labelWidth / 2.0) - EDGE_LABEL_PADDING;
        int ly = (int) Math.round(my - labelHeight / 2.0);

        Color prev = g.getColor();
        g.setColor(new Color(255, 255, 255, 210));
        g.fillRoundRect(lx, ly, labelWidth + EDGE_LABEL_PADDING * 2, labelHeight, 6, 6);
        g.setColor(PROPERTY_EDGE_COLOR.darker());
        g.drawString(label, lx + EDGE_LABEL_PADDING, ly + metrics.getAscent());
        g.setColor(prev);
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

            if (node.type == OntologyGraph.NodeType.PROPERTY) {
                continue;
            }
            paintClassNode(g, node, x, y, nodeWidth, nodeHeight, point, metrics);
        }
    }

    private void paintPropertyNode(Graphics2D g, OntologyGraph.Node node, int x, int y, int w, int h, Point center, FontMetrics metrics) {
        int labelWidth = metrics.stringWidth(node.label);
        int labelHeight = metrics.getHeight();
        int lx = (int) Math.round(center.x - labelWidth / 2.0) - EDGE_LABEL_PADDING;
        int ly = (int) Math.round(center.y - labelHeight / 2.0);
        g.setColor(new Color(255, 255, 255, 210));
        g.fillRoundRect(lx, ly, labelWidth + EDGE_LABEL_PADDING * 2, labelHeight, 6, 6);
        g.setColor(PROPERTY_NODE_COLOR.darker());
        g.drawString(node.label, lx + EDGE_LABEL_PADDING, ly + metrics.getAscent());
    }

    private void paintClassNode(Graphics2D g, OntologyGraph.Node node, int x, int y, int nodeWidth, int nodeHeight, Point center, FontMetrics metrics) {
        g.setColor(namespaceColors.getOrDefault(NamespaceColors.namespaceOf(node.id), CLASS_COLOR));
        g.fillRoundRect(x, y, nodeWidth, nodeHeight, 18, 18);
        g.setColor(Color.WHITE);
        g.drawRoundRect(x, y, nodeWidth, nodeHeight, 18, 18);

        int labelWidth = metrics.stringWidth(node.label);
        g.drawString(node.label, (int) Math.round(center.x - labelWidth / 2.0), y + 20);

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

    private static int nodeHeight(OntologyGraph.Node node, FontMetrics metrics) {
        if (node.type == OntologyGraph.NodeType.PROPERTY) {
            return PROPERTY_NODE_HEIGHT;
        }
        int width = nodeWidth(node, metrics);
        return Math.max(NODE_MIN_HEIGHT, headerHeight(node, metrics, width) + 12 + node.datatypeProperties.size() * PROPERTY_LINE_HEIGHT);
    }

    private static int nodeWidth(OntologyGraph.Node node, FontMetrics metrics) {
        if (node.type == OntologyGraph.NodeType.PROPERTY) {
            return Math.max(PROPERTY_NODE_MIN_WIDTH, metrics.stringWidth(node.label) + PROPERTY_NODE_PADDING * 2);
        }
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

    private static Rectangle2D nodeBounds(OntologyGraph.Node node, Point point, FontMetrics metrics) {
        return new Rectangle2D.Double(point.x - nodeWidth(node, metrics) / 2.0, point.y - nodeHeight(node, metrics) / 2.0, nodeWidth(node, metrics), nodeHeight(node, metrics));
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
