package com.vaimee.protege.graph;

import org.gephi.graph.api.DirectedGraph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.layout.plugin.force.StepDisplacement;
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

final class GephiGraphLayout {

    private static final Logger logger = LoggerFactory.getLogger(GephiGraphLayout.class);
    private static final Lookup gephiLookup = Lookups.metaInfServices(GephiGraphLayout.class.getClassLoader());

    static final class Position {
        final double x;
        final double y;

        Position(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private GephiGraphLayout() {
    }

    static Map<String, Position> layout(OntologyGraph ontologyGraph) {
        Map<String, Position> positions = new HashMap<>();
        if (ontologyGraph.getNodes().isEmpty()) {
            logger.info("Skipping Gephi layout because graph has no nodes");
            return positions;
        }

        logger.info("Starting Gephi layout for {} nodes and {} edges", ontologyGraph.getNodes().size(), ontologyGraph.getEdges().size());

        ProjectController projectController = gephiLookup.lookup(ProjectController.class);
        if (projectController == null) {
            logger.warn(
                    "Gephi ProjectController not available from plugin META-INF/services lookup; default lookup has controller: {}",
                    Lookup.getDefault().lookup(ProjectController.class) != null
            );
            return positions;
        }

        projectController.newProject();
        try {
            Workspace workspace = projectController.getCurrentWorkspace();

            GraphController graphController = gephiLookup.lookup(GraphController.class);
            if (graphController == null) {
                logger.warn(
                        "Gephi GraphController not available from plugin META-INF/services lookup; default lookup has controller: {}",
                        Lookup.getDefault().lookup(GraphController.class) != null
                );
                return positions;
            }

            GraphModel graphModel = graphController.getGraphModel(workspace);
            DirectedGraph graph = graphModel.getDirectedGraph();
            Map<String, Node> gephiNodes = new HashMap<>();

            for (OntologyGraph.Node node : ontologyGraph.getNodes()) {
                Node gephiNode = graphModel.factory().newNode(node.id);
                gephiNode.setLabel(node.label);
                graph.addNode(gephiNode);
                gephiNodes.put(node.id, gephiNode);
            }

            for (OntologyGraph.Edge edge : ontologyGraph.getEdges()) {
                Node source = gephiNodes.get(edge.source);
                Node target = gephiNodes.get(edge.target);
                if (source != null && target != null) {
                    graph.addEdge(graphModel.factory().newEdge(source, target, true));
                }
            }

            YifanHuLayout layout = new YifanHuLayout(null, new StepDisplacement(1f));
            layout.setGraphModel(graphModel);
            layout.resetPropertiesValues();
            layout.setOptimalDistance(180f);
            layout.initAlgo();
            int iterations = 0;
            for (; iterations < 180 && layout.canAlgo(); iterations++) {
                layout.goAlgo();
            }
            layout.endAlgo();

            for (OntologyGraph.Node node : ontologyGraph.getNodes()) {
                Node gephiNode = gephiNodes.get(node.id);
                if (gephiNode != null) {
                    positions.put(node.id, new Position(gephiNode.x(), gephiNode.y()));
                }
            }

            logger.info("Gephi layout completed: {} positions after {} iterations", positions.size(), iterations);
            if (logger.isDebugEnabled()) {
                logBounds(positions);
            }

            return positions;
        } finally {
            projectController.closeCurrentProject();
        }
    }

    private static void logBounds(Map<String, Position> positions) {
        if (positions.isEmpty()) {
            logger.debug("Gephi layout bounds unavailable because no positions were produced");
            return;
        }

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (Position position : positions.values()) {
            minX = Math.min(minX, position.x);
            maxX = Math.max(maxX, position.x);
            minY = Math.min(minY, position.y);
            maxY = Math.max(maxY, position.y);
        }

        logger.debug("Gephi layout bounds: x=[{}, {}], y=[{}, {}]", minX, maxX, minY, maxY);
    }
}
