FROM node:22-alpine AS build

WORKDIR /app

RUN chown node:node /app

USER node

COPY --chown=node:node package.json package-lock.json ./

RUN npm ci

COPY --chown=node:node . .

RUN npm run build -- --configuration=production


FROM nginxinc/nginx-unprivileged:1.27-alpine

ARG NGINX_CONF=nginx.conf.template



COPY --from=build \
     --chown=nginx:nginx \
     /app/dist/frontend/browser \
     /usr/share/nginx/html

COPY --chown=nginx:nginx \
     ${NGINX_CONF} \
     /etc/nginx/templates/default.conf.template

EXPOSE 8080
