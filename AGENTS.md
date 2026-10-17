# Project Memory

## Current PAC Work

- Active feature branch for this work: `pac`.
- The PAC ontology was renamed from `jsap` to `pac`; do not reintroduce `jsap` paths, prefixes, or namespace IRIs.
- Main PAC namespace: `https://onto.vaimee.com/pac#` with prefix `pac:`.
- Dedicated PAC individual namespaces:
  - `pacq:` -> `https://onto.vaimee.com/pac/query#`
  - `pacu:` -> `https://onto.vaimee.com/pac/update#`
  - `pacp:` -> `https://onto.vaimee.com/pac/producer#`
  - `pacc:` -> `https://onto.vaimee.com/pac/consumer#`
  - `paca:` -> `https://onto.vaimee.com/pac/aggregator#`
- Best practice: the prefix expresses the role, so do not repeat the role in the local name. Prefer `pacu:AtmosphereObservation` over `pacu:AtmosphereObservationUpdate`, `pacq:Atmospheres` over `pacq:AtmospheresQuery`, etc.

## PAC Modeling Decisions

- PAC components are reusable software primitives, not the same thing as software agents.
- A software agent can use one or more PAC components.
- `Producer` is bound to one `Update`.
- `Consumer` is bound to one `Query`.
- `Aggregator` reacts to one subscription query and performs one update.
- Queries and updates can use templates/snippets and forced bindings, following SEPA/JSAP patterns.
- Multiple forced bindings model repeated update execution with different runtime values.
- Forced binding variables use `pac:variableName`, not `pac:name`.

## Named Graph Modeling

- PAC includes named graph modeling.
- A `NamedGraph` must be either `HistoricalGraph` or `LiveGraph`.
- A `NamedGraph` must be either `FixedNamedGraph` or `InstanceNamedGraph`.
- `readsFromGraph` can target either historical or live graphs.
- `writesToGraph` can target historical graphs (appended) or live graphs (kept at the current state, e.g. the latest day and the forecast).
- `subscribesToGraph` must target live graphs.
- SPARQL strings for PAC queries and updates should include explicit `GRAPH ... {}` clauses matching the graph individual linked by `readsFromGraph`, `writesToGraph`, or `subscribesToGraph`.
- Fixed graphs have a stable URI.
- Instance graphs derive their URI at runtime from bindings, e.g. one graph per digital twin.

## Agora Integration

- `agora.ttl` uses PAC terms but should not `owl:imports` `pac.ttl`; this avoids Protégé load failures from import/catalog resolution.
- Declare locally only the PAC terms used in `agora.ttl` and keep the `pac:*` / `pacq:*` / `pacu:*` / `pacp:*` / `pacc:*` / `paca:*` prefixes.
- The relation from agents to PAC components is `:usesPacComponent`, not `:implementsPacComponent`.
- Current agent/component shape:
  - `WeatherAgent` uses a consumer to discover atmospheres and a producer to write weather observations.
  - `FieldDigitalTwin` uses an aggregator subscribed to run activities and writes field/crop/soil/model outputs.
  - `DigitalTwinSynchronizer` uses a producer to create run activities and an aggregator to react to activity state notifications.
- Activity graph is fixed; digital-twin data graphs are instance-scoped.

## Protégé / Turtle Stability

- Preserve manually introduced prefixes in `agora.ttl`; do not treat them as formatting noise.
- Protégé/OWL API may rewrite Turtle and remove unused prefixes. Keep important prefixes used in triples where possible.
- `agora/catalog-v001.xml` maps local imports for API, FSM, and PAC convenience. PAC mapping is optional because `agora.ttl` should not import PAC.
- Avoid `git restore --source=HEAD agora/agora.ttl` unless explicitly preserving user edits first; this previously removed local namespace work.

## Build / Verification

- Regenerate docs with `npm run --prefix rdf2html build` after ontology changes.
- Remove generated untracked side files if created during verification and not intended: `agora/agora-dtdl.html`, `agora/dtdl-v4-metamodel.html`, `dtdl/dtdl.html`.
- Run `git diff --check` before commit.

## MDW and Agorà

- `mdw/mdw.ttl` is the Milky Data Way ontology: horizontal and domain independent (agents, operations, channels, deployments, groundings, live/history graph bindings, derivations, agent states). Its terms keep the namespace already used in data: `semagent:` -> `http://onto.vaimee.com/semanticagentframework/0.1#`.
- `agora/agora.ttl` is the vertical agritech ontology: every class and observed property specific to the domain (fields, soils, crops, weather, CRITERIA plans and outputs). Agents of the domain (e.g. CRITERIA, the weather agents) and their contracts reference Agorà terms; MDW never does.
- Live and history graphs are `pac:LiveGraph` / `pac:HistoricalGraph`; MDW bindings (`semagent:liveGraph`, `semagent:historyGraph`) range over them. Subscriptions only on live graphs; history is read with queries.
- Agent lifecycle states are `semagent:AgentState` (`fsm:State`): `semagent:Instantiated`, `semagent:Online`, `semagent:Offline`.
