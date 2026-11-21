# Builds the whole app into one image: React UI + Spring Boot + Stockfish.
# "Multi-stage" build: the first two stages contain build tools (Node, Maven),
# the final image only gets their OUTPUT, so it stays small.
#
#   docker compose --profile app up --build      (see docker-compose.yml)

# ---------- Stage 1: React -> static files ----------
FROM node:22-bookworm-slim AS frontend
WORKDIR /build/frontend
# Copy only the package files first: Docker caches this layer, so `npm ci` reruns
# only when dependencies change, not on every code edit.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# vite.config.ts writes the build to ../src/main/resources/static = /build/src/main/resources/static
RUN npm run build

# ---------- Stage 2: Spring Boot jar ----------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml .
# Download all Java libraries in their own cached layer.
RUN mvn -q dependency:go-offline
COPY src ./src
COPY --from=frontend /build/src/main/resources/static ./src/main/resources/static
# Tests already ran in CI / on your machine; they need Docker themselves, so skip them here.
RUN mvn -q -DskipTests package

# ---------- Stage 3: what actually runs ----------
FROM eclipse-temurin:21-jre-noble
# Stockfish from Ubuntu's packages lands in /usr/games/stockfish, the path in application.yml.
RUN apt-get update \
    && apt-get install -y --no-install-recommends stockfish \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=backend /build/target/chess-analyzer-*.jar app.jar
# Don't run as root (the base image already has an unprivileged "ubuntu" user).
USER ubuntu
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
