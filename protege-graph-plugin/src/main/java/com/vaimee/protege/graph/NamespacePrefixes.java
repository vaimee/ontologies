package com.vaimee.protege.graph;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class NamespacePrefixes {

    private static final Map<String, String> KNOWN_PREFIXES = new LinkedHashMap<>();

    static {
        KNOWN_PREFIXES.put("http://onto.vaimee.com/agora/criteria#", "criteria");
        KNOWN_PREFIXES.put("http://onto.vaimee.com/fsm#", "fsm");
        KNOWN_PREFIXES.put("http://www.opengis.net/ont/geosparql#", "geo");
        KNOWN_PREFIXES.put("http://www.w3.org/2001/XMLSchema#", "xsd");
        KNOWN_PREFIXES.put("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf");
        KNOWN_PREFIXES.put("http://www.w3.org/2000/01/rdf-schema#", "rdfs");
        KNOWN_PREFIXES.put("http://www.w3.org/2002/07/owl#", "owl");
        KNOWN_PREFIXES.put("http://www.w3.org/2004/02/skos/core#", "skos");
        KNOWN_PREFIXES.put("http://www.w3.org/ns/sosa/", "sosa");
        KNOWN_PREFIXES.put("http://schema.org/", "schema");
    }

    private NamespacePrefixes() {
    }

    static Map<String, String> forNamespaces(String baseNamespace, Set<String> namespaces) {
        Map<String, String> prefixes = new LinkedHashMap<>();
        int generatedIndex = 1;
        for (String namespace : namespaces) {
            if (namespace.equals(baseNamespace)) {
                prefixes.put(namespace, "");
            } else if (KNOWN_PREFIXES.containsKey(namespace)) {
                prefixes.put(namespace, KNOWN_PREFIXES.get(namespace));
            } else {
                prefixes.put(namespace, "ns" + generatedIndex);
                generatedIndex++;
            }
        }
        return prefixes;
    }

    static String qName(String iri, String baseNamespace) {
        String namespace = NamespaceColors.namespaceOf(iri);
        String localName = iri.substring(namespace.length());
        if (localName.isEmpty()) {
            return iri;
        }
        if (namespace.equals(baseNamespace)) {
            return ":" + localName;
        }
        String prefix = KNOWN_PREFIXES.get(namespace);
        if (prefix != null) {
            return prefix + ":" + localName;
        }
        return iri;
    }
}
