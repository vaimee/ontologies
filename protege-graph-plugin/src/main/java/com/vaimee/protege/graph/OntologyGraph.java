package com.vaimee.protege.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OntologyGraph {

    enum NodeType {
        CLASS
    }

    enum EdgeType {
        SUBCLASS,
        PROPERTY
    }

    static final class Node {
        final String id;
        final String label;
        final NodeType type;

        Node(String id, String label, NodeType type) {
            this.id = id;
            this.label = label;
            this.type = type;
        }
    }

    static final class Edge {
        final String source;
        final String target;
        final String label;
        final EdgeType type;

        Edge(String source, String target, String label, EdgeType type) {
            this.source = source;
            this.target = target;
            this.label = label;
            this.type = type;
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();

    void addNode(String id, String label, NodeType type) {
        if (!nodes.containsKey(id)) {
            nodes.put(id, new Node(id, label, type));
        }
    }

    void addEdge(String source, String target, String label, EdgeType type) {
        if (nodes.containsKey(source) && nodes.containsKey(target)) {
            edges.add(new Edge(source, target, label, type));
        }
    }

    Collection<Node> getNodes() {
        return nodes.values();
    }

    List<Edge> getEdges() {
        return edges;
    }
}
