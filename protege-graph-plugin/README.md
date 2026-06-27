# VAIMEE Protégé Graph Plugin

Protégé Desktop tab plugin for visual ontology graph exploration.

## What It Shows

The implementation uses Gephi Toolkit to layout a graph of the active ontology:

- OWL classes as nodes.
- `rdfs:subClassOf` relations as blue edges.
- Object property domain/range relations as cyan labeled edges.

The current tab renders the Gephi-computed layout in Swing. The next step is to insert the rdf2gephi conversion path so RDF/SPARQL query output can be converted to GEXF and fed into Gephi Toolkit.

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
- `com.vaimee.protege.graph.GephiGraphLayout`
- `com.vaimee.protege.graph.OntologyGraphPanel`

Use `INFO` to see extraction/layout summaries and `DEBUG` to see layout bounds and paint/scaling details.

## Next Iterations

- Add zoom/pan interaction.
- Add filters by namespace, relation type, and imported/local entities.
- Add WebVOWL export/import compatibility.
- Add graph layout options for larger ontologies.
