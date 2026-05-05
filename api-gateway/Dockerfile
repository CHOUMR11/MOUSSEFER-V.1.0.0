FROM eclipse-temurin:21-jre-alpine

# Métadonnées
LABEL maintainer="Moussefer Team"
LABEL service="api-gateway"
LABEL version="1.0.0"

# Création d'un utilisateur non-root
RUN addgroup -S moussefer && adduser -S moussefer -G moussefer

WORKDIR /app

# Copie du JAR (utilise un wildcard précis pour éviter les conflits)
COPY target/api-gateway-*.jar app.jar

# Changement de propriétaire
RUN chown moussefer:moussefer app.jar

# Installation de curl pour le healthcheck Docker
RUN apk add --no-cache curl

# Passage à l'utilisateur non-root
USER moussefer

# Exposition du port
EXPOSE 8080

# Healthcheck (utilise l'endpoint Actuator)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl --fail http://localhost:8080/actuator/health || exit 1

# Point d'entrée optimisé pour conteneur
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]