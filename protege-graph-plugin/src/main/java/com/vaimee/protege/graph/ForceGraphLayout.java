package com.vaimee.protege.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

final class ForceGraphLayout {

    private static final Logger logger = LoggerFactory.getLogger(ForceGraphLayout.class);
    private static final double PROPERTY_CHARGE_FACTOR = 0.8;
    private static final double OVERLAP_PADDING = 20.0;
    private static final double ALPHA_DECAY = 0.02;
    private static final double ALPHA_MIN = 0.001;
    private static final double VELOCITY_DECAY = 0.4;
    private static final double DRAG_ALPHA = 0.3;
    private static final double DEFAULT_WIDTH = 180.0;
    private static final double DEFAULT_HEIGHT = 48.0;

    static final double DEFAULT_CHARGE = -500.0;
    static final double DEFAULT_LINK_DISTANCE = 180.0;
    static final double DEFAULT_LINK_STRENGTH = 0.3;
    static final double DEFAULT_GRAVITY = 0.025;

    private final OntologyGraph graph;
    private double charge = DEFAULT_CHARGE;
    private double linkDistance = DEFAULT_LINK_DISTANCE;
    private double linkStrength = DEFAULT_LINK_STRENGTH;
    private double gravity = DEFAULT_GRAVITY;
    private final String[] nodeIds;
    private final Body[] bodies;
    private double alpha = 1.0;
    private double alphaTarget = 0.0;
    private String pinnedNodeId;

    ForceGraphLayout(OntologyGraph graph) {
        this.graph = graph;
        int nodeCount = graph.getNodes().size();
        nodeIds = new String[nodeCount];
        bodies = new Body[nodeCount];

        if (nodeCount == 0) {
            return;
        }

        double radius = Math.max(200.0, Math.min(800.0, nodeCount * 40.0));
        int index = 0;
        for (OntologyGraph.Node node : graph.getNodes()) {
            double angle = (Math.PI * 2.0 * index) / nodeCount;
            nodeIds[index] = node.id;
            bodies[index] = new Body(Math.cos(angle) * radius, Math.sin(angle) * radius);
            index++;
        }
        logger.info("Force simulation created: {} bodies, initial radius {}", nodeCount, radius);
    }

    void setNodeSizes(Map<String, double[]> sizes) {
        for (int i = 0; i < nodeIds.length; i++) {
            double[] size = sizes.get(nodeIds[i]);
            if (size != null) {
                bodies[i].width = size[0];
                bodies[i].height = size[1];
            }
        }
    }

    void step() {
        if (bodies.length == 0) {
            return;
        }
        alpha += (alphaTarget - alpha) * ALPHA_DECAY;
        if (alpha < ALPHA_MIN) {
            alpha = ALPHA_MIN;
            return;
        }
        applyCharge();
        applyLinks();
        applyGravity();
        integrate();
        resolveOverlaps();
    }

    void pinNode(String id, double x, double y) {
        pinnedNodeId = id;
        alphaTarget = DRAG_ALPHA;
        alpha = Math.max(alpha, 0.1);
        for (int i = 0; i < nodeIds.length; i++) {
            if (nodeIds[i].equals(id)) {
                bodies[i].x = x;
                bodies[i].y = y;
                bodies[i].vx = 0;
                bodies[i].vy = 0;
                return;
            }
        }
    }

    void unpinNode() {
        pinnedNodeId = null;
        alphaTarget = 0.0;
    }

    boolean isSettled() {
        return alpha <= ALPHA_MIN && alphaTarget < ALPHA_MIN;
    }

    Map<String, GephiGraphLayout.Position> getPositions() {
        Map<String, GephiGraphLayout.Position> positions = new HashMap<>();
        for (int i = 0; i < nodeIds.length; i++) {
            positions.put(nodeIds[i], new GephiGraphLayout.Position(bodies[i].x, bodies[i].y));
        }
        return positions;
    }

    private void applyCharge() {
        for (int i = 0; i < bodies.length; i++) {
            double chargeI = chargeFor(nodeIds[i]);
            for (int j = i + 1; j < bodies.length; j++) {
                double chargeJ = chargeFor(nodeIds[j]);
                double dx = bodies[j].x - bodies[i].x;
                double dy = bodies[j].y - bodies[i].y;
                double distSq = Math.max(dx * dx + dy * dy, 1.0);
                double dist = Math.sqrt(distSq);
                double fx = dx / dist * alpha;
                double fy = dy / dist * alpha;
                bodies[i].vx += fx * chargeJ / distSq;
                bodies[i].vy += fy * chargeJ / distSq;
                bodies[j].vx -= fx * chargeI / distSq;
                bodies[j].vy -= fy * chargeI / distSq;
            }
        }
    }

    void setCharge(double value) { charge = value; }
    void setLinkDistance(double value) { linkDistance = value; }
    void setLinkStrength(double value) { linkStrength = value; }
    void setGravity(double value) { gravity = value; }

    private double chargeFor(String nodeId) {
        OntologyGraph.Node node = graph.getNode(nodeId);
        if (node != null && node.type == OntologyGraph.NodeType.PROPERTY) {
            return charge * PROPERTY_CHARGE_FACTOR;
        }
        return charge;
    }

    private void applyLinks() {
        for (OntologyGraph.Edge edge : graph.getEdges()) {
            int si = indexOf(edge.source);
            int ti = indexOf(edge.target);
            if (si < 0 || ti < 0) {
                continue;
            }
            Body source = bodies[si];
            Body target = bodies[ti];
            double dx = target.x - source.x;
            double dy = target.y - source.y;
            double dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1.0);

            double radiusS = rectRadius(dx, dy, source.width, source.height);
            double radiusT = rectRadius(-dx, -dy, target.width, target.height);
            double targetDist = radiusS + radiusT + linkDistance;

            double force = (dist - targetDist) / dist * linkStrength * alpha;
            double fx = dx * force;
            double fy = dy * force;
            source.vx += fx;
            source.vy += fy;
            target.vx -= fx;
            target.vy -= fy;
        }
    }

    private void applyGravity() {
        for (Body body : bodies) {
            body.vx -= body.x * gravity * alpha;
            body.vy -= body.y * gravity * alpha;
        }
    }

    private void integrate() {
        for (int i = 0; i < bodies.length; i++) {
            if (nodeIds[i].equals(pinnedNodeId)) {
                continue;
            }
            Body b = bodies[i];
            b.vx *= (1.0 - VELOCITY_DECAY);
            b.vy *= (1.0 - VELOCITY_DECAY);
            b.x += b.vx;
            b.y += b.vy;
        }
    }

    private void resolveOverlaps() {
        for (int i = 0; i < bodies.length; i++) {
            for (int j = i + 1; j < bodies.length; j++) {
                Body a = bodies[i];
                Body b = bodies[j];
                double dx = b.x - a.x;
                double dy = b.y - a.y;

                double minDx = (a.width + b.width) / 2.0 + OVERLAP_PADDING;
                double minDy = (a.height + b.height) / 2.0 + OVERLAP_PADDING;
                double overlapX = minDx - Math.abs(dx);
                double overlapY = minDy - Math.abs(dy);

                if (overlapX > 0.0 && overlapY > 0.0) {
                    boolean aPinned = nodeIds[i].equals(pinnedNodeId);
                    boolean bPinned = nodeIds[j].equals(pinnedNodeId);

                    double dirX = dx >= 0.0 ? 1.0 : -1.0;
                    double dirY = dy >= 0.0 ? 1.0 : -1.0;
                    if (Math.abs(dx) < 1.0 && Math.abs(dy) < 1.0) {
                        dirX = (i % 2 == 0) ? 1.0 : -1.0;
                        dirY = (j % 2 == 0) ? 1.0 : -1.0;
                    }

                    if (overlapX < overlapY) {
                        if (aPinned) { b.x += overlapX * dirX; }
                        else if (bPinned) { a.x -= overlapX * dirX; }
                        else { a.x -= overlapX / 2.0 * dirX; b.x += overlapX / 2.0 * dirX; }
                    } else {
                        if (aPinned) { b.y += overlapY * dirY; }
                        else if (bPinned) { a.y -= overlapY * dirY; }
                        else { a.y -= overlapY / 2.0 * dirY; b.y += overlapY / 2.0 * dirY; }
                    }
                }
            }
        }
    }

    private int indexOf(String nodeId) {
        for (int i = 0; i < nodeIds.length; i++) {
            if (nodeIds[i].equals(nodeId)) {
                return i;
            }
        }
        return -1;
    }

    private static double rectRadius(double dx, double dy, double width, double height) {
        double absDx = Math.abs(dx);
        double absDy = Math.abs(dy);
        if (absDx < 0.001 && absDy < 0.001) {
            return Math.max(width, height) / 2.0;
        }
        double dist = Math.sqrt(dx * dx + dy * dy);
        double scaleX = absDx < 0.001 ? Double.POSITIVE_INFINITY : (width / 2.0) / absDx;
        double scaleY = absDy < 0.001 ? Double.POSITIVE_INFINITY : (height / 2.0) / absDy;
        return Math.min(scaleX, scaleY) * dist;
    }

    private static final class Body {
        double x, y;
        double vx, vy;
        double width = DEFAULT_WIDTH;
        double height = DEFAULT_HEIGHT;

        Body(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}
