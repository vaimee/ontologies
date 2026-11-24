# Permanent identifiers with w3id.org

How VAIMEE ontologies are named, why one of them is named differently from the
rest, and what it takes to register and maintain a `w3id.org` identifier.

---

## The problem this solves

An ontology IRI is not a download link. It is a name, and other people's data
contains it. A dataset published today may still assert
`rdf:type onto.vaimee.com/pac#Device` in fifteen years, inside systems nobody
here will have access to. Whatever that IRI resolves to then — a page, a 404, or
somebody else's parking site — is the answer the world gets.

That is a different requirement from the one a hostname is designed to meet.
`onto.vaimee.com` resolves as long as VAIMEE holds the domain, keeps the DNS
record, pays the registrar, and keeps that server answering. Each of those is a
reasonable thing to rely on for a service. None of them is a thing to rely on
for a name that must outlive the service.

[w3id.org](https://w3id.org/) separates the two. It is a redirect service run by
the [W3C Permanent Identifier Community Group](https://www.w3.org/community/perma-id/):
the identifier lives there permanently, and the redirect points at wherever the
documentation currently is. When the hosting moves, you change one line in a
public Git repository and every existing IRI keeps working. The identifier never
had to move, because it was never tied to the host.

The cost is real and worth stating: you take on a dependency on w3id.org, a
volunteer-run service, and every change goes through a pull request reviewed by
someone outside VAIMEE. That is the trade. You give up unilateral control of the
redirect in exchange for an identifier that does not depend on a domain
registration.

---

## Where VAIMEE stands today

Two schemes are in use, which is worth knowing before adding a third.

**`onto.vaimee.com`** — seven ontologies, plus one under a project domain.
The last column is the result of dereferencing each IRI on 24 November 2026:

| Ontology | IRI | Resolves |
| --- | --- | --- |
| Agora | `http://onto.vaimee.com/agora#` | 200 |
| Agora API | `http://onto.vaimee.com/agora/api#` | **404** |
| DTDL | `http://onto.vaimee.com/agora/dtdl#` | **404** |
| FSM | `http://onto.vaimee.com/fsm#` | 200 |
| Messages | `https://onto.vaimee.com/msg#` | 200 |
| PAC | `https://onto.vaimee.com/pac#` | 200 |
| Users | `https://onto.vaimee.com/users#` | 200 |
| HARMONI | `http://harmoni-project.eu/ontology` | **404** |

**`w3id.org/vaimee`** — AgoràWasteMap. Registration is *pending*: the pull
request is [perma-id/w3id.org#7283](https://github.com/perma-id/w3id.org/pull/7283),
opened 24 November 2026. Until it is merged these IRIs do not resolve, though the
documents behind them are already live:

| Ontology | IRI |
| --- | --- |
| AgoràWasteMap core | `https://w3id.org/vaimee/awm` |
| Data sources module | `https://w3id.org/vaimee/awm/sources` |
| Regulatory module | `https://w3id.org/vaimee/awm/legal` |

Note the `http` / `https` split in the first table. **These are different IRIs.**
RDF compares them as strings: `http://onto.vaimee.com/pac#Device` and
`https://onto.vaimee.com/pac#Device` are two unrelated terms, and no amount of
server-side redirecting changes that — a 308 moves a *request*, not a *name*.
Any data that used the `http` form still refers to the `http` term. This is the
kind of thing that is cheap to decide once and expensive to discover later,
which is the argument for settling the naming question deliberately rather than
per project.

HARMONI is under a project domain rather than VAIMEE's, which is normal for a
consortium deliverable and worth flagging as a third pattern with a third
lifetime attached to it.

### Three of these do not currently resolve

`agora/api` and `agora/dtdl` have no `location` for them in `nginx.conf`, so
nothing is served at either IRI. `harmoni-project.eu/ontology` returns 404 as
well.

This is not an argument for w3id — w3id would not have fixed any of them, since
it only redirects and the target still has to answer. It is an argument for
treating "the IRI dereferences" as something to **test**, the way any other
contract is tested, rather than assumed because the file exists in the
repository.

HARMONI is the more interesting one. The usual worry is that a project domain
lapses; here it is alive and well, and the ontology path was simply never
served. An identifier can die without anything dramatic happening to the host.

---

## How w3id works

There is no magic. w3id.org is an Apache server whose configuration is a public
GitHub repository, [`perma-id/w3id.org`](https://github.com/perma-id/w3id.org).
Each identifier is a directory under `ids/` containing an `.htaccess` file:

```
ids/
└── vaimee/
    ├── .htaccess     the redirect rules — the part that does the work
    └── README.md     identifier and maintainer information (optional)
```

The directory name is the identifier: `ids/vaimee/` serves
`https://w3id.org/vaimee/` and everything beneath it. A request arrives, Apache
applies your rewrite rules, and the client is redirected to your server, which
serves the actual content.

So w3id holds **the name**. You still host **the documents**, and they still have
to be served correctly — w3id does not do content negotiation for you.

---

## Registering an identifier

The authoritative guide is at **<https://w3id.org/docs/>**. What follows is the
short version plus the parts we got wrong or nearly wrong.

### 1. Claim one top-level directory

Policy is one top-level directory per organisation, with everything beneath it.
`ids/vaimee/` is ours; a second claim such as `ids/vaimee-awm/` would be refused.
Lowercase, digits and hyphens only, and names that could be confused with an
existing identifier or a well-known organisation are rejected.

Check the name is free by browsing
[`ids/`](https://github.com/perma-id/w3id.org/tree/master/ids), including case
variants — `ids/Foo/` and `ids/foo/` cannot coexist.

### 2. Have the target live first

Reviewers check that the redirect target resolves over HTTPS **before** merging.
An identifier pointing at a URL that does not exist yet will be asked to wait.
Publish the documentation, confirm content negotiation works, then open the pull
request.

### 3. Write the rules

```apache
RewriteEngine on

RewriteRule ^awm$          https://wastemap.vaimee.com/ontology/awm [R=303,L]
RewriteRule ^awm/sources$  https://wastemap.vaimee.com/ontology/awm-sources [R=303,L]
RewriteRule ^awm/legal$    https://wastemap.vaimee.com/ontology/awm-legal [R=303,L]

RewriteRule ^awm/          https://wastemap.vaimee.com/ontology/ [R=302,L]
RewriteRule ^(.*)$         https://vaimee.com/$1 [R=302,L]
```

Three things in there are deliberate.

**303, not 302, for ontology IRIs.** The identifier names a *concept*; the
redirect leads to a *document about it*. 303 says exactly that, and it is the
httpRange-14 convention linked-data tooling expects. Use 302 for everything else.

**Never 301.** Browsers and caches honour it indefinitely and many never
re-check. Get a 301 wrong and you cannot take it back for anyone who already
cached it — on a service designed for identifiers meant to last decades.

**Ordering, with `$` anchors.** The regulatory module is `/awm/legal`; the acts
it cites are `/awm/legal/dlgs-152-2006` and friends. Without the anchor and
without putting the exact matches first, the module rule swallows nine
individuals. This is the single easiest mistake to make here and it is invisible
until someone dereferences one of them.

Keep the file minimal. No `Options`, no `RewriteBase`, no `AddType`, no
`RewriteCond` you do not need — unnecessary directives are a standing review
comment.

### 4. Put a GitHub username in the contact block

This is the most frequent single review comment on the repository. Without it a
reviewer cannot tell whether a future pull request touching your directory is
legitimate. Put it in `.htaccess` comments, in `README.md`, or both.

### 5. Test before opening the pull request

A syntax error in `.htaccess` returns **500 for every URL in that directory**,
not just the rule you got wrong. Two checks, and both are worth doing:

```sh
# the project's own checker — 42 rules
(cd tools/check && npm ci)
node tools/check/bin/w3id-check.js
```

The checker validates form. It cannot tell you whether a redirect goes where you
meant, so also run the rules against a real Apache:

```sh
docker run -d --name w3idtest -p 8901:80 \
  -v "$PWD/htdocs:/usr/local/apache2/htdocs:ro" \
  -v "$PWD/httpd.conf:/usr/local/apache2/conf/httpd.conf:ro" \
  httpd:2.4
```

with `AllowOverride All` on the document root and your `.htaccess` at
`htdocs/<identifier>/`. Then walk every IRI pattern your data actually uses —
not just the happy path, and specifically the pairs where one path is a prefix
of another.

### 6. Open the pull request

Fork, branch, one identifier per pull request. Title it
`Register new namespace: <name>`.

---

## What the target server must do

w3id redirects; your server answers. For an ontology IRI that means content
negotiation: documentation to a browser, Turtle to a client that asks for it, at
the same URL.

Both VAIMEE repositories do this the same way in nginx — a `map` on
`$http_accept` selecting the file, and an exact-match `location`:

```nginx
map $http_accept $pac_document {
    default                 /pac/pac.html;
    ~*text/turtle           /pac/pac.ttl;
    ~*application/rdf\+xml  /pac/pac.ttl;
}

server {
    location = /pac {
        try_files $pac_document =404;
    }
}
```

Two cautions learned the hard way.

**Do not put a `types { }` block inside `server`.** It *replaces* the inherited
MIME table rather than extending it, so every extension not listed becomes
`application/octet-stream` — HTML included. `nginx.conf` in this repository has
such a block listing `html`, `ttl` and `svg`. It does no harm today because the
generated pages are self-contained, but it is a trap for the first person to add
a stylesheet. Prefer `default_type text/turtle` scoped to the ontology location,
which fills in only where the MIME table has no answer.

**Test against the real image, not a dev server.** `python3 -m http.server` has
its own MIME table and will happily hide a content-type bug that production
will not.

---

## The modular namespace trap

This one is worth its own section, because it is silent and we shipped it before
catching it.

AgoràWasteMap mints every term in a single namespace, `https://w3id.org/vaimee/awm#`,
while the definitions are written across three module files. That split is good
for people — one subject at a time — and wrong for machines by default.

A client that encounters `awm:AccessBarrier` strips the fragment and dereferences
`https://w3id.org/vaimee/awm`. If that serves only the core module, the client
gets a document that does not define the term it came for. In our case **78 of
252 terms** resolved to a document that said nothing about them. Nothing errors;
the terms are simply undefined to every consumer that follows its nose.

Two ways out:

1. **One namespace, one document.** Serve the union of the modules at the
   namespace IRI, generated from them so it cannot drift. Modules stay
   individually retrievable at their own IRIs. This is what AgoràWasteMap does
   now — see `scripts/build-namespace.py` in the AgoràWasteMap repository, wired
   into the build so a module change regenerates it.
2. **One namespace per module.** `…/awm/sources#` and `…/awm/legal#` instead of
   a shared `awm#`. Cleaner in principle, more verbose in use, and a breaking
   change once anything has been published.

The ontologies in *this* repository each use their own namespace and a single
file, so they do not have this problem — but any of them that grows into modules
will, and the choice is much cheaper before publication than after.

---

## Versioned IRIs

`nginx.conf` here already serves `/agora/1.2.3` from the same document as
`/agora`:

```nginx
location ~ ^/agora/[0-9]+\.[0-9]+\.[0-9]+$ {
    try_files $agora_document =404;
}
```

If these move behind w3id, that pattern has to move with them, or every
version-pinned IRI breaks. The rule is a one-liner:

```apache
RewriteRule ^(agora/[0-9]+\.[0-9]+\.[0-9]+)$  https://onto.vaimee.com/$1 [R=303,L]
```

Worth deciding deliberately whether a version-pinned IRI should keep resolving
to the *current* document, as it does today, or to the document *as it was*. The
current behaviour means a pinned IRI does not actually pin anything — which may
be fine, but it should be a decision rather than an artefact of the regex.

---

## Should the existing ontologies migrate?

Honestly: migrating published IRIs is not a move, it is a **rename**. Every
existing dataset keeps the old terms, and the two sets are unrelated as far as
RDF is concerned. `owl:sameAs` or `owl:equivalentClass` can bridge them, but only
for consumers doing inference, which in practice is few.

So the useful split is:

- **New ontologies** → `w3id.org/vaimee/<name>`. Costs one pull request, and
  settles the lifetime question at the point where it is free to settle.
- **Published ontologies** → leave the IRIs alone unless there is a concrete
  reason. Keep `onto.vaimee.com` resolving. Adding a w3id alias that redirects to
  the same documents is cheap and harmless, but it does not make the old IRIs go
  away and should not be described as if it did.
- **The `http` / `https` inconsistency** in the table above is a live issue
  regardless of w3id, and deserves deciding on its own terms.

---

## Checklist for a new identifier

- [ ] Name is free, including case variants, under `ids/`
- [ ] Documentation is live over HTTPS and content-negotiated
- [ ] Namespace IRI resolves to a document defining **every** term in that namespace
- [ ] 303 for ontology IRIs, 302 for the rest, never 301
- [ ] Exact matches anchored with `$` and ordered before looser rules
- [ ] A GitHub username in the contact block
- [ ] `node tools/check/bin/w3id-check.js` passes
- [ ] Every IRI pattern exercised against a real Apache
- [ ] One identifier per pull request, titled `Register new namespace: <name>`

---

## References

- w3id documentation — <https://w3id.org/docs/>
- Repository — <https://github.com/perma-id/w3id.org>
- Permanent Identifier Community Group — <https://www.w3.org/community/perma-id/>
- Our registration pull request — <https://github.com/perma-id/w3id.org/pull/7283>
- `httpRange-14` and the 303 convention — <https://www.w3.org/2001/tag/doc/httpRange-14/2007-05-31/HttpRange-14>
