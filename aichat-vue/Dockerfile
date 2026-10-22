ARG NODE_IMAGE=node:22-alpine
ARG NGINX_IMAGE=nginx:alpine
FROM ${NODE_IMAGE} AS build
WORKDIR /build
COPY aichat-vue/package*.json ./
RUN npm ci --no-audit --no-fund
COPY aichat-vue/ ./
RUN npm run build
FROM ${NGINX_IMAGE}
COPY aichat-vue/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
COPY docker/portraits /usr/share/nginx/html/portraits
EXPOSE 80
