# VAIMEE Protégé Graph Plugin

Protégé Desktop tab plugin for visual ontology graph exploration.

## What It Shows

The implementation renders a graph of the active ontology:

- OWL classes as nodes.
- Class comments in the class node header, wrapped inside the box when long.
- Datatype properties inside class nodes as a two-column table, including XSD datatype when declared.
- `rdfs:subClassOf` relations as blue edges.
- Object property domain/range relations as cyan labeled edges.
- Namespaces in a left-side legend, with class node colors derived from namespace.
- Namespace checkboxes to show/hide classes and their connected object property edges.
- QName labels with prefixes shown in the namespace legend; the active ontology namespace is rendered with `:`.

The tab renders a force-directed layout in Swing. `Reset view` recomputes an initial view with all classes and property labels on screen, using edge attraction, node repulsion, collision avoidance, and central gravity inspired by WebVOWL-style layouts.

Drag the empty graph area to pan, and use the mouse wheel to zoom. Class nodes remain draggable.
Object property labels are anchored at the domain class border where the edge starts.
Edges are routed around class boxes when a direct segment would cross an intermediate class box.

## Build

Install Maven and run:

```sh
mvn clean package
```

The plugin bundle is generated under `target/`.

The JAR embeds Gephi Toolkit, so you only need to copy the generated plugin JAR to Protégé.

## Install In Protégé

Copy the generated JAR into the `plugins/` directory of your Protégé Desktop installation, then restart Protégé.

Enable the tab from:

```text
Window > Tabs > VAIMEE Graph
```

## Logging

The plugin logs through SLF4J, so the output follows the logging configuration used by Protégé.

Useful logger names:

- `com.vaimee.protege.graph.OntologyGraphTab`
- `com.vaimee.protege.graph.OntologyGraphExtractor`
- `com.vaimee.protege.graph.ForceGraphLayout`
- `com.vaimee.protege.graph.GephiGraphLayout`
- `com.vaimee.protege.graph.OntologyGraphPanel`

Use `INFO` to see extraction/layout summaries and `DEBUG` to see layout bounds and paint/scaling details.

## Next Iterations

- Add filters by namespace, relation type, and imported/local entities.
- Add WebVOWL export/import compatibility.
- Add graph layout options for larger ontologies.
