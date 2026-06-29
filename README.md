# ontologies

A collection of ontologies developed by VAIMEE, integrating classes and properties from well-known ontologies to ensure interoperability and alignment with existing standards.

## Requirements

- Node.js 20 or newer for local HTML generation.
- npm for installing the renderer dependencies.
- Docker for building and running the nginx container locally.
- A GitHub repository with GitHub Actions enabled for CI/CD publishing to Docker Hub.

## Ontology Requirements

The renderer discovers ontology sources automatically from first-level ontology folders such as `agora/`, `jsap/`, `msg/`, `users/`, and `fsm/`.

Each ontology source should:

- Use Turtle syntax and the `.ttl` extension.
- Declare one ontology resource with `rdf:type owl:Ontology` or `a owl:Ontology`.
- Define classes as `owl:Class` and properties as `owl:ObjectProperty` or `owl:DatatypeProperty`.
- Prefer `rdfs:label` and `rdfs:comment` on classes and properties so the generated documentation has readable names and descriptions.
- Prefer ontology-level metadata: `owl:title`, `dct:abstract`, and `dct:description`.

If ontology-level title, abstract, or description metadata is missing, the renderer adds fallback metadata in memory while generating HTML. The source ontology file is not modified.

## Generate HTML Documentation

Install dependencies once:

```sh
npm install --prefix rdf2html
```

Generate all ontology pages and the landing page:

```sh
npm run --prefix rdf2html build
```

The build writes generated HTML beside each ontology source and creates a `public/` directory ready to be served by nginx. The landing page uses `public/ICON_VAIMEE.svg`, `#10B1D8`, and `#0F3E65`.

## Ontology Graph Visualization

The landing page embeds a WebVOWL visualization for every ontology card.

By default the generated page uses the hosted WebVOWL service at `https://service.tib.eu/webvowl/` and passes each ontology URL with the `#iri=` parameter.

WebVOWL is not only static HTML/JavaScript for Turtle or OWL files. Loading an ontology IRI requires the OWL2VOWL conversion endpoints provided by the WebVOWL application. Because this repository publishes a static nginx container, the WebVOWL application must run separately if you want to host it yourself.

To run WebVOWL from the upstream source code:

```sh
git clone https://github.com/VisualDataWeb/WebVOWL.git
cd WebVOWL
docker build -t webvowl:v1 .
docker compose up -d
```

Expose that WebVOWL service at a public URL, for example `https://ontologies.example.com/webvowl/`, then override `window.WEBVOWL_BASE_URL` before the generated script runs or adapt `rdf2html/render.js` to use that base URL.

The ontology files must also be reachable by the WebVOWL service. Local-only URLs such as `localhost` cannot be fetched by a remote WebVOWL service.

## Protégé Graph Plugin

A Protégé Desktop tab plugin that renders a force-directed graph of the active OWL ontology. Classes are shown as rounded boxes containing datatype properties, connected by curved edges for `rdfs:subClassOf` and object property relations with labels at the arc midpoint.

### Build

Requires Maven and JDK 8+.

```sh
cd protege-graph-plugin
mvn clean package
```

The plugin JAR is generated under `protege-graph-plugin/target/`.

### Install

Copy the generated JAR into the `plugins/` directory of your Protégé Desktop installation, then restart Protégé. Enable the tab from **Window > Tabs > VAIMEE Graph**.

### Features

- Force-directed layout with D3-style simulation (charge, link distance, gravity).
- Interactive drag: grab a class node and connected nodes follow via spring forces.
- Pan (drag background), zoom (mouse wheel), and fit-to-view on reset.
- Namespace legend with color-coded checkboxes to show/hide classes.
- Layout settings panel with sliders for charge, link distance, link strength, and gravity.
- `rdfs:comment` annotations displayed inside class nodes with word wrapping.
- Curved edge routing around intermediate nodes.

## Build Locally

Build the nginx image:

```sh
docker build -t ontologies .
```

Run it locally:

```sh
docker run --rm -p 8080:80 ontologies
```

Open `http://localhost:8080` to browse the generated ontology documentation.

## Deploy With CI/CD

The workflow in `.github/workflows/publish.yml` builds the Docker image and publishes it to Docker Hub.

It runs automatically on pushes to `main` or `master`, and can also be started manually from the GitHub Actions tab.

### Configure Docker Hub

Create a Docker Hub repository before publishing the image. For example, create `vaimee/ontologies` from the Docker Hub web interface.

Create a Docker Hub access token from Docker Hub account settings. Use this token for automation instead of your account password.

Configure these GitHub repository settings before running the workflow:

- Secret `DOCKERHUB_USERNAME`: your Docker Hub username.
- Secret `DOCKERHUB_TOKEN`: a Docker Hub access token. Use an access token instead of your account password.
- Variable `DOCKERHUB_REPOSITORY`: the Docker Hub image name, for example `vaimee/ontologies`.

### Publish From GitHub Actions

After configuring the repository settings, push to `main` or `master`:

```sh
git push origin main
```

Or start the `Publish ontologies` workflow manually from the GitHub Actions tab.

The workflow builds the Docker image from `Dockerfile`, regenerates the ontology HTML during the Docker build, and pushes the image to Docker Hub.

The published image is tagged as:

- `<dockerhub-repository>:latest` for the default branch.
- `<dockerhub-repository>:<branch>` for branch builds.
- `<dockerhub-repository>:sha-<commit>` for immutable commit builds.

### Publish Manually

To build and push the image from your local machine:

```sh
docker login -u <dockerhub-username>
docker build -t <dockerhub-repository>:latest .
docker push <dockerhub-repository>:latest
```

For example:

```sh
docker login -u vaimee
docker build -t vaimee/ontologies:latest .
docker push vaimee/ontologies:latest
```

Use a Docker Hub access token when Docker asks for the password.

### Build Multi-Architecture Images On macOS

Docker Desktop for macOS includes Buildx. Use it to build images for both Apple Silicon and Linux AMD64 servers.

Create and select a Buildx builder:

```sh
docker buildx create --name ontologies-builder --use
docker buildx inspect --bootstrap
```

Build and push a multi-architecture image to Docker Hub:

```sh
docker login -u <dockerhub-username>
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t <dockerhub-repository>:latest \
  --push \
  .
```

For example:

```sh
docker login -u vaimee
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t vaimee/ontologies:latest \
  --push \
  .
```

Use `--push` for multi-architecture images. Docker cannot load a multi-platform manifest into the local Docker image store with `--load`.

To build only for the current Mac architecture and load it locally:

```sh
docker buildx build --platform linux/arm64 -t ontologies:local --load .
```

To build an AMD64 image on Apple Silicon and load it locally for testing:

```sh
docker buildx build --platform linux/amd64 -t ontologies:amd64 --load .
```

### Run The Published Image

To deploy the published container on a server:

```sh
docker login -u <dockerhub-username>
docker pull <dockerhub-repository>:latest
docker run -d --name ontologies --restart unless-stopped -p 80:80 <dockerhub-repository>:latest
```

Replace `<dockerhub-username>` and `<dockerhub-repository>` with your Docker Hub account and repository values.
