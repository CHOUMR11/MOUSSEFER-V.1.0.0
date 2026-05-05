FROM eclipse-temurin:21-jre-alpine

# Création d'un utilisateur non-root
RUN addgroup -S moussefer && adduser -S moussefer -G moussefer

WORKDIR /app

# Copie du JAR (nom explicite)
COPY target/service-registry-*.jar app.jar

# Changement de propriétaire et permissions
RUN chown moussefer:moussefer app.jar

# Installation de curl pour le healthcheck (léger)
RUN apk add --no-cache curl

# Utilisateur non-root
USER moussefer

# Exposition du port Eureka
EXPOSE 8761

# Healthcheck Docker
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl --fail http://localhost:8761/actuator/health || exit 1

# Point d'entrée optimisé pour conteneur
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]