package com.vaimee.protege.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ForceGraphLayout {

    private static final Logger logger = LoggerFactory.getLogger(ForceGraphLayout.class);
    private static final double PROPERTY_CHARGE_FACTOR = 0.8;
    private static final double OVERLAP_PADDING = 60.0;
    private static final double ALPHA_DECAY = 0.02;
    private static final double ALPHA_MIN = 0.001;
    private static final double VELOCITY_DECAY = 0.4;
    private static final double DRAG_ALPHA = 0.3;
    private static final double DEFAULT_WIDTH = 180.0;
    private static final double DEFAULT_HEIGHT = 48.0;

    static final double DEFAULT_CHARGE = -3000.0;
    static final double DEFAULT_LINK_DISTANCE = 150.0;
    static final double DEFAULT_LINK_STRENGTH = 0.5;
    static final double DEFAULT_GRAVITY = 0.01;

    private final OntologyGraph graph;
    private double charge = DEFAULT_CHARGE;
    private double linkDistance = DEFAULT_LINK_DISTANCE;
    private double linkStrength = DEFAULT_LINK_STRENGTH;
    private double gravity = DEFAULT_GRAVITY;
    private final String[] nodeIds;
    private final Body[] bodies;
    private final Set<String> fixedNodes = new HashSet<>();
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

        int index = 0;
        Map<String, Integer> idToIndex = new HashMap<>();
        for (OntologyGraph.Node node : graph.getNodes()) {
            nodeIds[index] = node.id;
            bodies[index] = new Body(0, 0);
            idToIndex.put(node.id, index);
            index++;
        }

        Set<String> hasIncoming = new HashSet<>();
        Set<String> hasOutgoing = new HashSet<>();
        Map<String, List<String>> outEdges = new HashMap<>();
        for (OntologyGraph.Edge edge : graph.getEdges()) {
            hasOutgoing.add(edge.source);
            hasIncoming.add(edge.target);
            outEdges.computeIfAbsent(edge.source, k -> new ArrayList<>()).add(edge.target);
        }

        Map<String, Integer> depth = new HashMap<>();
        Map<String, String> parent = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        List<String> sources = new ArrayList<>();
        for (int i = 0; i < nodeIds.length; i++) {
            String id = nodeIds[i];
            if (!hasIncoming.contains(id) && hasOutgoing.contains(id)) {
                depth.put(id, 0);
                queue.add(id);
                sources.add(id);
            }
        }
        if (sources.isEmpty()) {
            for (int i = 0; i < nodeIds.length; i++) {
                if (hasOutgoing.contains(nodeIds[i])) {
                    depth.put(nodeIds[i], 0);
                    queue.add(nodeIds[i]);
                    sources.add(nodeIds[i]);
                    break;
                }
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int d = depth.get(current);
            for (String target : outEdges.getOrDefault(current, java.util.Collections.emptyList())) {
                if (!depth.containsKey(target)) {
                    depth.put(target, d + 1);
                    parent.put(target, current);
                    queue.add(target);
                }
            }
        }

        int maxDepth = 0;
        Map<Integer, List<Integer>> rings = new HashMap<>();
        List<Integer> isolated = new ArrayList<>();
        for (int i = 0; i < nodeIds.length; i++) {
            String id = nodeIds[i];
            if (!depth.containsKey(id)) {
                boolean connected = hasIncoming.contains(id) || hasOutgoing.contains(id);
                if (connected) {
                    depth.put(id, 0);
                    rings.computeIfAbsent(0, k -> new ArrayList<>()).add(i);
                } else {
                    isolated.add(i);
                }
            } else {
                int d = depth.get(id);
                if (d > maxDepth) maxDepth = d;
                rings.computeIfAbsent(d, k -> new ArrayList<>()).add(i);
            }
        }

        Map<String, Double> nodeAngle = new HashMap<>();
        List<Integer> ring0 = rings.getOrDefault(0, java.util.Collections.emptyList());
        for (int i = 0; i < ring0.size(); i++) {
            double angle = (Math.PI * 2.0 * i) / Math.max(ring0.size(), 1);
            nodeAngle.put(nodeIds[ring0.get(i)], angle);
            bodies[ring0.get(i)].x = 0;
            bodies[ring0.get(i)].y = 0;
            if (ring0.size() > 1) {
                bodies[ring0.get(i)].x = Math.cos(angle) * 200;
                bodies[ring0.get(i)].y = Math.sin(angle) * 200;
            }
        }

        double ringSpacing = Math.max(500.0, nodeCount * 35.0);
        for (int d = 1; d <= maxDepth; d++) {
            List<Integer> ring = rings.getOrDefault(d, java.util.Collections.emptyList());
            final Map<String, Double> angles = nodeAngle;
            ring.sort((a, b) -> {
                double angleA = angles.getOrDefault(parent.getOrDefault(nodeIds[a], ""), 0.0);
                double angleB = angles.getOrDefault(parent.getOrDefault(nodeIds[b], ""), 0.0);
                return Double.compare(angleA, angleB);
            });

            double radius = d * ringSpacing;
            for (int i = 0; i < ring.size(); i++) {
                String parentId = parent.get(nodeIds[ring.get(i)]);
                double parentAngle = nodeAngle.getOrDefault(parentId, 0.0);
                double spread = Math.PI * 2.0 / Math.max(ring.size(), 1);
                double angle = parentAngle + (i - ring.size() / 2.0) * spread * 0.6;
                nodeAngle.put(nodeIds[ring.get(i)], angle);
                bodies[ring.get(i)].x = Math.cos(angle) * radius;
                bodies[ring.get(i)].y = Math.sin(angle) * radius;
            }
        }

        double cornerOffset = (maxDepth + 1) * ringSpacing + 300;
        for (int i = 0; i < isolated.size(); i++) {
            bodies[isolated.get(i)].x = cornerOffset + (i % 3) * 300;
            bodies[isolated.get(i)].y = -(cornerOffset * 0.5) + (i / 3) * 250;
            fixedNodes.add(nodeIds[isolated.get(i)]);
        }

        logger.info("Force simulation created: {} bodies, {} depth levels, {} isolated (fixed)", nodeCount, maxDepth + 1, isolated.size());
    }

    void setInitialPositions(Map<String, GephiGraphLayout.Position> positions) {
        for (int i = 0; i < nodeIds.length; i++) {
            GephiGraphLayout.Position pos = positions.get(nodeIds[i]);
            if (pos != null) {
                bodies[i].x = pos.x;
                bodies[i].y = pos.y;
            }
        }
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

    void fixNode(String id) {
        fixedNodes.add(id);
    }

    void unfixNode(String id) {
        fixedNodes.remove(id);
    }

    boolean isFixed(String id) {
        return fixedNodes.contains(id);
    }

    Set<String> getFixedNodes() {
        return fixedNodes;
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
            if (isNodeLocked(i)) {
                continue;
            }
            Body b = bodies[i];
            b.vx *= (1.0 - VELOCITY_DECAY);
            b.vy *= (1.0 - VELOCITY_DECAY);
            b.x += b.vx;
            b.y += b.vy;
        }
    }

    private boolean isNodeLocked(int index) {
        String id = nodeIds[index];
        return id.equals(pinnedNodeId) || fixedNodes.contains(id);
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
                    boolean aPinned = isNodeLocked(i);
                    boolean bPinned = isNodeLocked(j);

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
