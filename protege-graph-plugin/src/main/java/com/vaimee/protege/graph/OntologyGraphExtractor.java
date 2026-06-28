package com.vaimee.protege.graph;

import org.protege.editor.owl.model.OWLModelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLDataPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class OntologyGraphExtractor {

    private static final Logger logger = LoggerFactory.getLogger(OntologyGraphExtractor.class);
    private static final String RDFS_COMMENT = "http://www.w3.org/2000/01/rdf-schema#comment";

    private OntologyGraphExtractor() {
    }

    static OntologyGraph extract(OWLModelManager modelManager) {
        OntologyGraph graph = new OntologyGraph();
        OWLOntology ontology = modelManager.getActiveOntology();

        if (ontology == null) {
            logger.warn("No active ontology available for VAIMEE graph extraction");
            return graph;
        }

        logger.info("Extracting VAIMEE graph from ontology {}", ontology.getOntologyID());
        graph.setBaseNamespace(baseNamespace(ontology));

        Map<OWLObjectProperty, List<OWLClass>> propertyDomains = new HashMap<>();
        Map<OWLObjectProperty, List<OWLClass>> propertyRanges = new HashMap<>();
        Map<OWLDataProperty, List<OWLClass>> datatypePropertyDomains = new HashMap<>();
        Map<OWLDataProperty, String> datatypePropertyRanges = new HashMap<>();

        for (OWLClass owlClass : ontology.getClassesInSignature()) {
            graph.addNode(iri(owlClass), label(modelManager, owlClass), OntologyGraph.NodeType.CLASS);
            for (OWLAnnotationAssertionAxiom axiom : ontology.getAnnotationAssertionAxioms(owlClass.getIRI())) {
                if (!RDFS_COMMENT.equals(axiom.getProperty().getIRI().toString())) {
                    continue;
                }
                graph.addAnnotation(
                        iri(owlClass),
                        NamespacePrefixes.qName(axiom.getProperty().getIRI().toString(), graph.getBaseNamespace()),
                        annotationValue(axiom.getValue(), graph.getBaseNamespace())
                );
            }
        }

        for (OWLSubClassOfAxiom axiom : ontology.getAxioms(AxiomType.SUBCLASS_OF)) {
            if (!axiom.getSubClass().isAnonymous() && !axiom.getSuperClass().isAnonymous()) {
                OWLClass subClass = axiom.getSubClass().asOWLClass();
                OWLClass superClass = axiom.getSuperClass().asOWLClass();
                graph.addEdge(iri(subClass), iri(superClass), "subClassOf", OntologyGraph.EdgeType.SUBCLASS);
            }
        }

        for (OWLObjectPropertyDomainAxiom axiom : ontology.getAxioms(AxiomType.OBJECT_PROPERTY_DOMAIN)) {
            if (!axiom.getProperty().isAnonymous() && !axiom.getDomain().isAnonymous()) {
                OWLObjectProperty property = axiom.getProperty().asOWLObjectProperty();
                propertyDomains.computeIfAbsent(property, key -> new ArrayList<>()).add(axiom.getDomain().asOWLClass());
            }
        }

        for (OWLObjectPropertyRangeAxiom axiom : ontology.getAxioms(AxiomType.OBJECT_PROPERTY_RANGE)) {
            if (!axiom.getProperty().isAnonymous() && !axiom.getRange().isAnonymous()) {
                OWLObjectProperty property = axiom.getProperty().asOWLObjectProperty();
                propertyRanges.computeIfAbsent(property, key -> new ArrayList<>()).add(axiom.getRange().asOWLClass());
            }
        }

        for (Map.Entry<OWLObjectProperty, List<OWLClass>> entry : propertyDomains.entrySet()) {
            List<OWLClass> ranges = propertyRanges.get(entry.getKey());
            if (ranges == null) {
                continue;
            }

            for (OWLClass domain : entry.getValue()) {
                for (OWLClass range : ranges) {
                    graph.addEdge(iri(domain), iri(range), label(modelManager, entry.getKey()), OntologyGraph.EdgeType.PROPERTY);
                }
            }
        }

        for (OWLDataPropertyDomainAxiom axiom : ontology.getAxioms(AxiomType.DATA_PROPERTY_DOMAIN)) {
            if (!axiom.getProperty().isAnonymous() && !axiom.getDomain().isAnonymous()) {
                OWLDataProperty property = axiom.getProperty().asOWLDataProperty();
                datatypePropertyDomains.computeIfAbsent(property, key -> new ArrayList<>()).add(axiom.getDomain().asOWLClass());
            }
        }

        for (OWLDataPropertyRangeAxiom axiom : ontology.getAxioms(AxiomType.DATA_PROPERTY_RANGE)) {
            if (!axiom.getProperty().isAnonymous() && axiom.getRange().isDatatype()) {
                OWLDataProperty property = axiom.getProperty().asOWLDataProperty();
                datatypePropertyRanges.put(property, datatypeLabel(axiom.getRange().asOWLDatatype()));
            }
        }

        for (Map.Entry<OWLDataProperty, List<OWLClass>> entry : datatypePropertyDomains.entrySet()) {
            String propertyLabel = label(modelManager, entry.getKey());
            String datatypeLabel = datatypePropertyRanges.get(entry.getKey());
            for (OWLClass domain : entry.getValue()) {
                graph.addDatatypeProperty(iri(domain), propertyLabel, datatypeLabel);
            }
        }

        logger.info(
                "Extracted VAIMEE graph: {} class nodes, {} total edges, {} object property domains, {} object property ranges, {} datatype property domains",
                graph.getNodes().size(),
                graph.getEdges().size(),
                propertyDomains.size(),
                propertyRanges.size(),
                datatypePropertyDomains.size()
        );

        return graph;
    }

    private static String iri(OWLEntity entity) {
        return entity.getIRI().toString();
    }

    private static String label(OWLModelManager modelManager, OWLEntity entity) {
        return NamespacePrefixes.qName(entity.getIRI().toString(), baseNamespace(modelManager.getActiveOntology()));
    }

    private static String datatypeLabel(OWLDatatype datatype) {
        return NamespacePrefixes.qName(datatype.getIRI().toString(), "");
    }

    private static String annotationValue(OWLAnnotationValue value, String baseNamespace) {
        if (value instanceof IRI) {
            return NamespacePrefixes.qName(value.toString(), baseNamespace);
        }
        if (value instanceof OWLLiteral) {
            return ((OWLLiteral) value).getLiteral();
        }
        return value.toString().replaceAll("^\\\"|\\\"(@[a-zA-Z-]+|\\^\\^.+)?$", "");
    }

    private static String baseNamespace(OWLOntology ontology) {
        if (ontology == null || !ontology.getOntologyID().getOntologyIRI().isPresent()) {
            return "";
        }
        return NamespaceColors.namespaceOf(ontology.getOntologyID().getOntologyIRI().get().toString());
    }
}
