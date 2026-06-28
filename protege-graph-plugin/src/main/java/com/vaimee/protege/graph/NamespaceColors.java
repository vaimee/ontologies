package com.vaimee.protege.graph;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class NamespaceColors {

    private static final Color[] PALETTE = new Color[]{
            new Color(15, 62, 101),
            new Color(16, 177, 216),
            new Color(122, 75, 196),
            new Color(227, 125, 0),
            new Color(62, 150, 81),
            new Color(193, 65, 83),
            new Color(104, 117, 128),
            new Color(142, 104, 41)
    };

    private NamespaceColors() {
    }

    static Map<String, Color> forGraph(OntologyGraph graph) {
        Set<String> namespaces = new TreeSet<>();
        for (OntologyGraph.Node node : graph.getNodes()) {
            namespaces.add(namespaceOf(node.id));
        }

        Map<String, Color> colors = new LinkedHashMap<>();
        int index = 0;
        for (String namespace : namespaces) {
            colors.put(namespace, PALETTE[index % PALETTE.length]);
            index++;
        }
        return colors;
    }

    static String namespaceOf(String iri) {
        int hashIndex = iri.lastIndexOf('#');
        if (hashIndex >= 0) {
            return iri.substring(0, hashIndex + 1);
        }

        int slashIndex = iri.lastIndexOf('/');
        if (slashIndex >= 0) {
            return iri.substring(0, slashIndex + 1);
        }

        return iri;
    }
}
