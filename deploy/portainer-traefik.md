# Portainer and Traefik Deployment

The ontology container serves plain HTTP on port `80`. TLS termination and public HTTP to HTTPS redirects should be handled by Traefik.

Use `deploy/portainer-stack.yml` as the Portainer stack template, or apply these labels on the Portainer stack/service that runs the ontology image:

```yaml
labels:
  - traefik.enable=true
  - traefik.http.routers.ontologies-http.rule=Host(`onto.vaimee.com`)
  - traefik.http.routers.ontologies-http.entrypoints=web
  - traefik.http.routers.ontologies-http.middlewares=ontologies-https
  - traefik.http.middlewares.ontologies-https.redirectscheme.scheme=https
  - traefik.http.middlewares.ontologies-https.redirectscheme.permanent=true
  - traefik.http.routers.ontologies-https.rule=Host(`onto.vaimee.com`)
  - traefik.http.routers.ontologies-https.entrypoints=websecure
  - traefik.http.routers.ontologies-https.tls=true
  - traefik.http.routers.ontologies-https.tls.certresolver=letsencrypt
  - traefik.http.services.ontologies.loadbalancer.server.port=80
```

If your Traefik instance already has a global `web -> websecure` redirect, omit the `ontologies-http` router and `ontologies-https` middleware labels.

Set these Portainer stack environment variables:

```text
ONTOLOGIES_IMAGE=<dockerhub repository>:latest
TRAEFIK_NETWORK=<external traefik docker network>
```

The GitHub Actions workflow builds the image with `ONTOLOGY_VERSION=1.0.<run_number>` on every push. That version is applied inside the Docker build before the static site is generated.

Hash IRIs such as `http://onto.vaimee.com/fsm#Configuration` are resolved by the browser as a request to `/fsm`; the fragment is not sent to the server. The Nginx configuration therefore serves the FSM ontology document at `/fsm`, and the browser keeps `#Configuration` after the HTTP to HTTPS redirect.

The container also serves versioned ontology documents, for example `/fsm/1.0.0` and `/agora/1.0.0`, matching the `owl:versionIRI` values in the ontology sources. During CI builds the deployed image can override the source baseline with `ONTOLOGY_VERSION`, so paths such as `/fsm/1.0.123` are also served.
