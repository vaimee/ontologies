# VAIMEE Protégé Graph Plugin

Protégé Desktop tab plugin for visual ontology graph exploration.

## What It Shows

The first implementation renders a lightweight graph of the active ontology:

- OWL classes as nodes.
- `rdfs:subClassOf` relations as blue edges.
- Object property domain/range relations as cyan edges through the property node.

The goal is to provide a pragmatic starting point inside Protégé before introducing heavier graph libraries or WebVOWL-style conversion.

## Build

Install Maven and run:

```sh
mvn clean package
```

The plugin bundle is generated under `target/`.

## Install In Protégé

Copy the generated JAR into the `plugins/` directory of your Protégé Desktop installation, then restart Protégé.

Enable the tab from:

```text
Window > Tabs > VAIMEE Graph
```

## Next Iterations

- Add zoom/pan interaction.
- Add filters by namespace, relation type, and imported/local entities.
- Add WebVOWL export/import compatibility.
- Add graph layout options for larger ontologies.
