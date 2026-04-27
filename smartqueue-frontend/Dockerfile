# ─────────────────────────────────────────────────────────────────────────────
# SmartQueue Frontend — multi-stage Dockerfile
#
# Stage 1: Node.js build
# Stage 2: Nginx alpine runtime (serve static assets + proxy /api to backend)
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM node:20-alpine AS build

WORKDIR /app

# Cache node_modules layer
COPY package.json package-lock.json ./
RUN npm ci --silent

COPY . .

# Build-time env vars — injected from docker-compose environment
ARG REACT_APP_API_BASE_URL
ARG REACT_APP_WS_BASE_URL
ENV REACT_APP_API_BASE_URL=$REACT_APP_API_BASE_URL
ENV REACT_APP_WS_BASE_URL=$REACT_APP_WS_BASE_URL

RUN npm run build

# ── Stage 2: Nginx runtime ────────────────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime

# Remove default config and copy ours
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/smartqueue.conf

COPY --from=build /app/build /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
