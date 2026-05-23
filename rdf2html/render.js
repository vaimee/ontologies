const fs = require('fs');
const path = require('path');
const sttl = require('sttl');
const urdf = require('urdf');

const repoRoot = path.resolve(__dirname, '..');
const siteRoot = path.join(repoRoot, 'public');
const templateURI = 'http://vaimee.com/template#ontology';
const ontologySourcePattern = /\.(ttl|owl)$/;

const tpl = fs.readFileSync(path.join(__dirname, 'vaimee.rq'), 'utf-8');

sttl.register(tpl);
sttl.connect(q => {
    return urdf.query(q)
    .then(b => ({
        results: {
            bindings: b
        }
    }));
});

const ontologies = findOntologies(repoRoot);

console.log('Rendering ontology documentation...');
const promiseChain = ontologies.reduce((p, ontology) => {
    return p
        .then(() => render(ontology))
        .then(() => urdf.clear());
}, Promise.resolve());

promiseChain
    .then(() => buildSite(ontologies))
    .then(() => console.log('Static site generated in', path.relative(repoRoot, siteRoot)))
    .catch((e) => {
        console.error('Error while rendering ontology documentation:', e);
        process.exitCode = 1;
    });


function render(ontology) {
    let ttl = withRenderingMetadata(fs.readFileSync(ontology.source, 'UTF-8'), ontology);
    return Promise.resolve()
    .then(() => urdf.load(ttl, { format: 'text/turtle' }))
    .then(() => sttl.callTemplate(templateURI))
    .then(html => fs.writeFileSync(ontology.html, html))
    .then(_ => console.log('File', path.relative(repoRoot, ontology.html), 'generated'))
    .catch((e) => {
        console.error('Error while rendering ' + path.relative(repoRoot, ontology.source) + ': ' + e);
        process.exitCode = 1;
    });
}

function findOntologies(root) {
    return fs.readdirSync(root, { withFileTypes: true })
        .filter(entry => entry.isDirectory())
        .filter(entry => !entry.name.startsWith('.') && entry.name !== 'rdf2html' && entry.name !== 'public')
        .flatMap(entry => {
            const dir = path.join(root, entry.name);
            return fs.readdirSync(dir)
                .filter(file => ontologySourcePattern.test(file))
                .filter(file => isOntologyFile(path.join(dir, file)))
                .map(file => {
                    const source = path.join(dir, file);
                    const html = source.replace(/\.(ttl|owl)$/, '.html');

                    return {
                        name: entry.name,
                        source,
                        html,
                        namespace: getOntologyNamespace(source),
                        title: getOntologyTitle(source, entry.name),
                        abstract: getOntologyAbstract(source)
                    };
                });
        })
        .sort((a, b) => a.name.localeCompare(b.name));
}

function getOntologyAbstract(source) {
    const rdf = fs.readFileSync(source, 'UTF-8');
    const namespace = getOntologyNamespace(source);
    const ontologyBlock = getOntologyBlock(rdf, namespace);

    return firstMatch(ontologyBlock, [
        /dct(?:erms?)?:abstract\s+"([^"]+)"/,
        /dct(?:erms?)?:description\s+"([^"]+)"/,
        /rdfs:comment\s+"([^"]+)"/,
    ]) || '';
}

function getOntologyTitle(source, name) {
    const rdf = fs.readFileSync(source, 'UTF-8');
    const namespace = getOntologyNamespace(source);
    const ontologyBlock = getOntologyBlock(rdf, namespace);

    return firstMatch(ontologyBlock, [
        /owl:title\s+"([^"]+)"/,
        /dct(?:erms?)?:title\s+"([^"]+)"/,
        /rdfs:label\s+"([^"]+)"/,
    ]) || titleFromName(name);
}

function isOntologyFile(source) {
    const rdf = fs.readFileSync(source, 'UTF-8');

    return /(?:rdf:type|\ba)\s+owl:Ontology/.test(rdf);
}

function getOntologyNamespace(source) {
    const rdf = fs.readFileSync(source, 'UTF-8');
    const ontologyMatch = rdf.match(/<([^>]+)>\s+(?:rdf:type|a)\s+owl:Ontology/);
    const prefixMatch = rdf.match(/@prefix\s+[^:]+:\s+<([^>]+)>\s*\./);

    if (ontologyMatch) {
        return ontologyMatch[1];
    }

    if (prefixMatch) {
        return prefixMatch[1];
    }

    throw new Error('Cannot detect ontology namespace for ' + source);
}

function withRenderingMetadata(ttl, ontology) {
    const ontologyBlock = getOntologyBlock(ttl, ontology.namespace);
    const title = firstMatch(ontologyBlock, [
        /owl:title\s+"([^"]+)"/,
        /dct(?:erms?)?:title\s+"([^"]+)"/,
        /rdfs:label\s+"([^"]+)"/,
    ]) || titleFromName(ontology.name);
    const description = firstMatch(ontologyBlock, [
        /dct(?:erms?)?:abstract\s+"([^"]+)"/,
        /dct(?:erms?)?:description\s+"([^"]+)"/,
        /rdfs:comment\s+"([^"]+)"/,
    ]) || `${title} documentation.`;
    const triples = [];

    if (!/owl:title\s+/.test(ttl)) {
        triples.push(`<${ontology.namespace}> <http://www.w3.org/2002/07/owl#title> ${literal(title)} .`);
    }

    if (!/(?:dct|dcterms):abstract\s+/.test(ttl)) {
        triples.push(`<${ontology.namespace}> <http://purl.org/dc/terms/abstract> ${literal(description)} .`);
    }

    if (!/(?:dct|dcterms):description\s+/.test(ttl)) {
        triples.push(`<${ontology.namespace}> <http://purl.org/dc/terms/description> ${literal(description)} .`);
    }

    return triples.length ? `${ttl}\n\n${triples.join('\n')}\n` : ttl;
}

function getOntologyBlock(ttl, namespace) {
    const ontologyStart = new RegExp(`<${escapeRegExp(namespace)}>\\s+(?:rdf:type|a)\\s+owl:Ontology`).exec(ttl);
    const start = ontologyStart ? ontologyStart.index : -1;

    if (start === -1) {
        return '';
    }

    const end = ttl.indexOf('\n\n', start);

    return ttl.slice(start, end === -1 ? undefined : end);
}

function escapeRegExp(value) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function firstMatch(text, patterns) {
    for (const pattern of patterns) {
        const match = text.match(pattern);

        if (match) {
            return match[1];
        }
    }

    return null;
}

function titleFromName(name) {
    return name
        .split(/[-_]/)
        .map(part => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ') + ' Ontology';
}

function literal(value) {
    return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

function escapeHtml(value) {
    return value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function buildSite(ontologies) {
    const preservedAssets = preserveAssets(['ICON_VAIMEE.svg']);

    fs.rmSync(siteRoot, { recursive: true, force: true });
    fs.mkdirSync(siteRoot, { recursive: true });

    preservedAssets.forEach(asset => fs.writeFileSync(path.join(siteRoot, asset.name), asset.content));
    ontologies.forEach(ontology => copyDirectory(path.dirname(ontology.source), path.join(siteRoot, ontology.name)));
    fs.writeFileSync(path.join(siteRoot, 'index.html'), renderIndex(ontologies));
}

function preserveAssets(assetNames) {
    return assetNames
        .map(name => {
            const filePath = path.join(siteRoot, name);

            if (!fs.existsSync(filePath)) {
                return null;
            }

            return {
                name,
                content: fs.readFileSync(filePath)
            };
        })
        .filter(Boolean);
}

function copyDirectory(source, target) {
    fs.mkdirSync(target, { recursive: true });

    fs.readdirSync(source, { withFileTypes: true }).forEach(entry => {
        const sourcePath = path.join(source, entry.name);
        const targetPath = path.join(target, entry.name);

        if (entry.isDirectory()) {
            copyDirectory(sourcePath, targetPath);
            return;
        }

        fs.copyFileSync(sourcePath, targetPath);
    });
}

function renderIndex(ontologies) {
    const links = ontologies
        .map(ontology => {
            const href = `${ontology.name}/${path.basename(ontology.html)}`;
            return `                    <li>
                        <a href="${href}">
                            <span class="card-title">${escapeHtml(ontology.title)}</span>
                            <span class="card-abstract">${escapeHtml(ontology.abstract)}</span>
                            <span class="card-action">Open documentation</span>
                        </a>
                    </li>`;
        })
        .join('\n');

    return `<!doctype html>
<html lang="en">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>VAIMEE Ontologies</title>
        <style>
            :root {
                --vaimee-cyan: #10B1D8;
                --vaimee-blue: #0F3E65;
                --surface: #ffffff;
                --muted: #5c7184;
            }

            * {
                box-sizing: border-box;
            }

            body {
                min-height: 100vh;
                margin: 0;
                font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                color: var(--vaimee-blue);
                background:
                    radial-gradient(circle at top right, rgba(16, 177, 216, 0.28), transparent 34rem),
                    linear-gradient(135deg, #f7fcff 0%, #eef8fb 48%, #ffffff 100%);
            }

            main {
                width: min(1120px, calc(100% - 2rem));
                margin: 0 auto;
                padding: 4rem 0;
            }

            .hero {
                display: grid;
                grid-template-columns: minmax(0, 1fr) auto;
                gap: 3rem;
                align-items: center;
                padding: 3rem;
                border: 1px solid rgba(16, 177, 216, 0.24);
                border-radius: 2rem;
                background: rgba(255, 255, 255, 0.82);
                box-shadow: 0 24px 80px rgba(15, 62, 101, 0.12);
                backdrop-filter: blur(14px);
            }

            .logo {
                width: min(220px, 36vw);
                height: auto;
            }

            .eyebrow {
                margin: 0 0 1rem;
                color: var(--vaimee-cyan);
                font-size: 0.82rem;
                font-weight: 800;
                letter-spacing: 0.16em;
                text-transform: uppercase;
            }

            h1 {
                max-width: 760px;
                margin: 0;
                font-size: clamp(2.6rem, 7vw, 5.6rem);
                line-height: 0.95;
                letter-spacing: -0.06em;
            }

            .summary {
                max-width: 640px;
                margin: 1.5rem 0 0;
                color: var(--muted);
                font-size: 1.12rem;
                line-height: 1.7;
            }

            .catalog {
                margin-top: 2rem;
                padding: 0;
                list-style: none;
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
                gap: 1rem;
            }

            .catalog a {
                display: block;
                min-height: 12rem;
                padding: 1.35rem;
                border: 1px solid rgba(15, 62, 101, 0.12);
                border-radius: 1.25rem;
                color: var(--vaimee-blue);
                background: var(--surface);
                text-decoration: none;
                font-size: 1.15rem;
                font-weight: 800;
                box-shadow: 0 14px 36px rgba(15, 62, 101, 0.08);
                transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
            }

            .card-title,
            .card-abstract,
            .card-action {
                display: block;
            }

            .card-title {
                font-size: 1.15rem;
                font-weight: 800;
            }

            .card-abstract {
                margin-top: 0.85rem;
                color: var(--muted);
                font-size: 0.95rem;
                font-weight: 500;
                line-height: 1.55;
            }

            .card-action {
                margin-top: 1.5rem;
                color: var(--vaimee-cyan);
                font-size: 0.82rem;
                font-weight: 800;
                text-transform: uppercase;
                letter-spacing: 0.08em;
            }

            .catalog a:hover,
            .catalog a:focus-visible {
                border-color: var(--vaimee-cyan);
                box-shadow: 0 20px 48px rgba(16, 177, 216, 0.2);
                transform: translateY(-3px);
            }

            @media (max-width: 720px) {
                main {
                    padding: 1rem 0;
                }

                .hero {
                    grid-template-columns: 1fr;
                    gap: 2rem;
                    padding: 1.5rem;
                }

                .logo {
                    width: 160px;
                    order: -1;
                }
            }
        </style>
    </head>
    <body>
        <main>
            <section class="hero" aria-labelledby="page-title">
                <div>
                    <p class="eyebrow">VAIMEE semantic resources</p>
                    <h1 id="page-title">Ontology documentation</h1>
                    <p class="summary">Browse the generated HTML documentation for the ontologies maintained in this repository.</p>
                </div>
                <img class="logo" src="ICON_VAIMEE.svg" alt="VAIMEE">
            </section>
            <ul class="catalog" aria-label="Ontologies">
${links}
            </ul>
        </main>
    </body>
</html>
`;
}
