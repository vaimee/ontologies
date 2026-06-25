FROM node:20-alpine AS build

RUN apk add --no-cache git

WORKDIR /app

COPY rdf2html/package.json ./rdf2html/package.json
RUN npm install --prefix rdf2html

COPY . .
ARG ONTOLOGY_VERSION=1.0.0
ENV ONTOLOGY_VERSION=$ONTOLOGY_VERSION
RUN npm run --prefix rdf2html version && npm run --prefix rdf2html build

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/public /usr/share/nginx/html

EXPOSE 80
