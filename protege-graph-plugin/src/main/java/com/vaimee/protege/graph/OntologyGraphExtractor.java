package com.vaimee.protege.graph;

import org.protege.editor.owl.model.OWLModelManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

import java.util.HashMap;
import java.util.Map;

final class OntologyGraphExtractor {

    private OntologyGraphExtractor() {
    }

    static OntologyGraph extract(OWLModelManager modelManager) {
        OntologyGraph graph = new OntologyGraph();
        OWLOntology ontology = modelManager.getActiveOntology();

        if (ontology == null) {
            return graph;
        }

        Map<OWLObjectProperty, OWLClassExpression> propertyDomains = new HashMap<>();

        for (OWLClass owlClass : ontology.getClassesInSignature()) {
            graph.addNode(iri(owlClass), label(modelManager, owlClass), OntologyGraph.NodeType.CLASS);
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
                propertyDomains.put(property, axiom.getDomain());
                graph.addNode(iri(property), label(modelManager, property), OntologyGraph.NodeType.PROPERTY);
                graph.addEdge(iri(axiom.getDomain().asOWLClass()), iri(property), "domain", OntologyGraph.EdgeType.PROPERTY_DOMAIN);
            }
        }

        for (OWLObjectPropertyRangeAxiom axiom : ontology.getAxioms(AxiomType.OBJECT_PROPERTY_RANGE)) {
            if (!axiom.getProperty().isAnonymous() && !axiom.getRange().isAnonymous()) {
                OWLObjectProperty property = axiom.getProperty().asOWLObjectProperty();
                graph.addNode(iri(property), label(modelManager, property), OntologyGraph.NodeType.PROPERTY);
                if (!propertyDomains.containsKey(property)) {
                    graph.addEdge(iri(property), iri(axiom.getRange().asOWLClass()), "range", OntologyGraph.EdgeType.PROPERTY_RANGE);
                } else {
                    graph.addEdge(iri(property), iri(axiom.getRange().asOWLClass()), "range", OntologyGraph.EdgeType.PROPERTY_RANGE);
                }
            }
        }

        return graph;
    }

    private static String iri(OWLEntity entity) {
        return entity.getIRI().toString();
    }

    private static String label(OWLModelManager modelManager, OWLEntity entity) {
        String rendering = modelManager.getRendering(entity);
        if (rendering != null && !rendering.trim().isEmpty()) {
            return rendering;
        }
        return entity.getIRI().getShortForm();
    }
}
