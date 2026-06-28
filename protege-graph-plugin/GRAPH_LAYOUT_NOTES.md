# Graph Layout Notes

These notes capture the useful layout and interaction ideas from "Interactive 3D Exploration of RDF Graphs through Semantic Planes" for the current 2D Protégé graph plugin. The goal is not to implement 3D now, but to improve the 2D graph with the same principles: readable placement, filtering, incremental exploration, and RDF/OWL-aware rendering.

## Requirements To Preserve

- Pre-filtering: large RDF graphs should be reduced before rendering. For the plugin this maps to namespace, relation-type, class, object-property, datatype-property, and future SPARQL filters.
- Node placement: linked resources/classes should be close enough to understand relationships quickly, while avoiding overlap with nodes, edges, and labels.
- Incremental approach: users should be able to progressively build a useful view instead of rendering everything with one fixed strategy.
- Filtering: filters should be composable and reversible, not destructive.
- RDFS/OWL support: classes, object properties, datatype properties, domain, range, labels, comments, and datatypes should remain first-class concepts in the visualization.
- Scenario independence: the layout must not encode Agora-specific assumptions.

## Current Plugin Mapping

- Classes are rendered as boxes.
- Datatype properties are rendered inside class boxes as a two-column table.
- Object properties are rendered as directed labeled edges from domain to range.
- Namespace colors and namespace visibility checkboxes provide an initial filtering mechanism.
- Pan, zoom, node drag, reset view, collision avoidance, and auto-fit are implemented in 2D.

## Layout References Worth Studying

- Gansner, Koutsofios, North, Vo, "A technique for drawing directed graphs". Relevant for directed ontology graphs and edge-aware layered layouts.
- Gansner and North, "An open graph visualization system and its applications to software engineering". Relevant because Graphviz-style layout is mature for directed labeled graphs.
- Gansner, Koren, North, "Graph Drawing by Stress Majorization". Relevant for proximity-preserving layouts where connected nodes should stay close.
- Gansner and Hu, "Efficient, Proximity-Preserving Node Overlap Removal". Directly relevant to our reset-view overlap problem.
- Binucci et al., "Placing Arrows in Directed Graph Drawings". Relevant to object-property arrow placement and readability.
- Shneiderman and Aris, "Network visualization by semantic substrates". Relevant to future 2D semantic planes or lanes grouped by class/namespace/filter.
- Gansner and Koren, "Improved circular layouts". Relevant for fallback views or small highly connected subgraphs.
- WebVOWL/VOWL 2. Relevant for ontology-oriented visual primitives and class/property readability expectations.
- Gephi and ForceAtlas/Yifan Hu style layouts. Relevant for interactive force-directed exploration, but should be combined with explicit label and box collision handling.

## Practical Layout Direction

The plugin should move toward a hybrid layout instead of relying on one force algorithm:

1. Build an RDF/OWL-aware graph model.
2. Group or weight nodes using semantic signals: namespace, direct object-property relations, subclass hierarchy, and imported/local ontology origin.
3. Compute a relationship-aware placement where directly connected classes are close.
4. Apply deterministic overlap removal using actual rendered box sizes.
5. Place edge labels at stable anchor points near their source/domain class.
6. Route or offset edges only after node placement is stable.
7. Fit the full visible visual bounds, including node boxes and property labels.

## Near-Term 2D Improvements

- Keep manual node positions stable during repaint and filtering.
- Improve collision avoidance so it uses actual visual bounds for both class boxes and edge labels.
- Add object-property visibility filters, analogous to namespace filters.
- Add class visibility filters.
- Add datatype-property visibility filters.
- Add a small status/readout panel for selected class/property details.
- Add saved views: visible namespaces, hidden properties, manual node positions, zoom, and pan.

## Medium-Term 2D Semantic Planes

Semantic planes can be adapted to 2D without 3D rendering:

- Render planes as horizontal or vertical lanes.
- Allow moving selected classes/resources to a lane based on namespace, class, property, or SPARQL result.
- Keep cross-plane object properties visible and visually emphasized.
- Use lanes as an incremental exploration mechanism, not as a replacement for filtering.

This would retain the core Tarsier idea while staying compatible with Swing and Protégé.

## Implementation Caution

- Generic force layouts alone are not enough because ontology nodes are not points: they are boxes with internal datatype-property tables.
- Collision removal must use rendered dimensions, not abstract node counts.
- Edge labels must be considered part of the visual bounds.
- Reset view should be deterministic enough that users can orient themselves after repeated refresh/reset operations.
- Manual edits should never trigger a full layout reset unless the user explicitly requests `Reset view`.
