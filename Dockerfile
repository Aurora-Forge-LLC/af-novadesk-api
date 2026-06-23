FROM eclipse-temurin:25-jre-alpine
LABEL maintainer="af-core-team"
LABEL service="af-novadesk-api"
WORKDIR /app

# Install/update CA certificates so the JVM trusts Let's Encrypt (and other public CAs).
# The minimal Alpine JRE image may have an outdated or incomplete CA bundle,
# causing SSL handshake failures when fetching JWKS from authhub via Traefik.
RUN apk add --no-cache ca-certificates && update-ca-certificates

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY target/af-novadesk-api-*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
