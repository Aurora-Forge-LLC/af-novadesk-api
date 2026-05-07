FROM eclipse-temurin:25-jre-alpine
LABEL maintainer="af-core-team"
LABEL service="af-novadesk-api"
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY target/af-novadesk-api-*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
