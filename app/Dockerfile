# Production image of the web UI behind Caddy (task 8.1). Build context: app/
# Caddy serves the app and proxies /api to the backend (see deploy/Caddyfile),
# so the app talks to its own origin and needs no hostname at build time.
FROM debian:bookworm-slim AS build
# Keep in line with the Flutter version used for development and in CI.
ARG FLUTTER_VERSION=3.47.8
RUN apt-get update \
	&& apt-get install -y --no-install-recommends ca-certificates curl git unzip xz-utils \
	&& rm -rf /var/lib/apt/lists/* \
	&& git clone --depth 1 --branch "$FLUTTER_VERSION" https://github.com/flutter/flutter.git /opt/flutter
ENV PATH=/opt/flutter/bin:$PATH
RUN flutter config --no-analytics --enable-web && flutter precache --web
WORKDIR /src
COPY pubspec.yaml pubspec.lock ./
RUN flutter pub get
COPY . .
RUN flutter build web --release --dart-define=API_BASE_URL=/api

FROM caddy:2-alpine
COPY --from=build /src/build/web /srv
