# Node LTS (22) — compiles the native better-sqlite3 module cleanly. The host's
# Node 26 does not, which is why this app runs in a container.
FROM node:22-bookworm-slim

# Build toolchain for native modules (better-sqlite3).
RUN apt-get update \
  && apt-get install -y --no-install-recommends python3 make g++ \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install dependencies first for better layer caching. The compiled
# better-sqlite3 binary lives in this image's node_modules. .npmrc carries
# resilient network settings (retries, capped concurrency).
COPY package.json package-lock.json* .npmrc ./
RUN npm install

# App source is bind-mounted in dev (see docker-compose.yml); copying it here
# keeps the image runnable on its own too.
COPY . .

EXPOSE 3000

# Bind to 0.0.0.0 so the port is reachable from the host.
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
