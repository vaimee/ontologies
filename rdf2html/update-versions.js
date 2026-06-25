const fs = require('fs');
const path = require('path');

const version = process.env.ONTOLOGY_VERSION || '1.0.0';

if (!/^\d+\.\d+\.\d+$/.test(version)) {
    throw new Error(`ONTOLOGY_VERSION must be semantic version x.y.z, got: ${version}`);
}

const repoRoot = path.resolve(__dirname, '..');
const ontologies = [
    {
        file: 'agora/agora_ontology.ttl',
        versionIri: `http://onto.vaimee.com/agora/${version}#`,
    },
    {
        file: 'fsm/fsm.owl',
        versionIri: `http://onto.vaimee.com/fsm/${version}`,
    },
    {
        file: 'jsap/jsap_ontology.ttl',
        versionIri: `https://onto.vaimee.com/jsap/${version}#`,
    },
    {
        file: 'msg/messages_ontology.ttl',
        versionIri: `https://onto.vaimee.com/msg/${version}#`,
    },
    {
        file: 'users/users_ontology.ttl',
        versionIri: `https://onto.vaimee.com/users/${version}#`,
    },
];

ontologies.forEach(({ file, versionIri }) => {
    const filePath = path.join(repoRoot, file);
    let ttl = fs.readFileSync(filePath, 'utf8');

    ttl = ttl.replace(/owl:versionIRI\s+<[^>]+>/, `owl:versionIRI <${versionIri}>`);
    ttl = ttl.replace(/owl:versionInfo\s+(?:"[^"]+"|[0-9]+(?:\.[0-9]+)?)/, `owl:versionInfo "${version}"`);

    fs.writeFileSync(filePath, ttl);
    console.log(`Set ${file} to version ${version}`);
});
