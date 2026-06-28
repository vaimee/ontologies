package com.vaimee.protege.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

final class ForceGraphLayout {

    private static final Logger logger = LoggerFactory.getLogger(ForceGraphLayout.class);
    private static final int ITERATIONS = 450;
    private static final double CANVAS_RADIUS = 420.0;
    private static final double REPULSION = 52000.0;
    private static final double EDGE_DISTANCE = 190.0;
    private static final double EDGE_STRENGTH = 0.018;
    private static final double GRAVITY = 0.012;
    private static final double COOLING = 0.985;

    private ForceGraphLayout() {
    }

    static Map<String, GephiGraphLayout.Position> layout(OntologyGraph graph) {
        Map<String, Body> bodies = new HashMap<>();
        int nodeCount = graph.getNodes().size();
        if (nodeCount == 0) {
            return new HashMap<>();
        }

        int index = 0;
        double initialRadius = Math.max(120.0, Math.min(CANVAS_RADIUS, nodeCount * 24.0));
        for (OntologyGraph.Node node : graph.getNodes()) {
            double angle = (Math.PI * 2.0 * index) / nodeCount;
            bodies.put(node.id, new Body(Math.cos(angle) * initialRadius, Math.sin(angle) * initialRadius));
            index++;
        }

        double temperature = 1.0;
        for (int i = 0; i < ITERATIONS; i++) {
            resetForces(bodies);
            applyRepulsion(bodies);
            applyEdges(graph, bodies);
            applyGravity(bodies);
            integrate(bodies, temperature);
            temperature *= COOLING;
        }

        Map<String, GephiGraphLayout.Position> positions = new HashMap<>();
        for (Map.Entry<String, Body> entry : bodies.entrySet()) {
            positions.put(entry.getKey(), new GephiGraphLayout.Position(entry.getValue().x, entry.getValue().y));
        }

        logger.info("Force layout completed: {} positions after {} iterations", positions.size(), ITERATIONS);
        return positions;
    }

    private static void resetForces(Map<String, Body> bodies) {
        for (Body body : bodies.values()) {
            body.fx = 0.0;
            body.fy = 0.0;
        }
    }

    private static void applyRepulsion(Map<String, Body> bodies) {
        Body[] values = bodies.values().toArray(new Body[0]);
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                Body a = values[i];
                Body b = values[j];
                double dx = a.x - b.x;
                double dy = a.y - b.y;
                double distanceSquared = Math.max(dx * dx + dy * dy, 25.0);
                double distance = Math.sqrt(distanceSquared);
                double force = REPULSION / distanceSquared;
                double fx = (dx / distance) * force;
                double fy = (dy / distance) * force;

                a.fx += fx;
                a.fy += fy;
                b.fx -= fx;
                b.fy -= fy;
            }
        }
    }

    private static void applyEdges(OntologyGraph graph, Map<String, Body> bodies) {
        for (OntologyGraph.Edge edge : graph.getEdges()) {
            Body source = bodies.get(edge.source);
            Body target = bodies.get(edge.target);
            if (source == null || target == null) {
                continue;
            }

            double dx = target.x - source.x;
            double dy = target.y - source.y;
            double distance = Math.max(Math.sqrt(dx * dx + dy * dy), 1.0);
            double force = (distance - EDGE_DISTANCE) * EDGE_STRENGTH;
            double fx = (dx / distance) * force;
            double fy = (dy / distance) * force;

            source.fx += fx;
            source.fy += fy;
            target.fx -= fx;
            target.fy -= fy;
        }
    }

    private static void applyGravity(Map<String, Body> bodies) {
        for (Body body : bodies.values()) {
            body.fx -= body.x * GRAVITY;
            body.fy -= body.y * GRAVITY;
        }
    }

    private static void integrate(Map<String, Body> bodies, double temperature) {
        for (Body body : bodies.values()) {
            body.vx = (body.vx + body.fx) * 0.72;
            body.vy = (body.vy + body.fy) * 0.72;
            body.x += body.vx * temperature;
            body.y += body.vy * temperature;
        }
    }

    private static final class Body {
        double x;
        double y;
        double vx;
        double vy;
        double fx;
        double fy;

        Body(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}
