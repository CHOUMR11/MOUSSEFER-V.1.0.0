FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Moussefer Team"
LABEL service="notification-service"
LABEL version="1.0.0"

RUN addgroup -S moussefer && adduser -S moussefer -G moussefer

WORKDIR /app

COPY target/notification-service-*.jar app.jar

RUN chown moussefer:moussefer app.jar
RUN apk add --no-cache curl

USER moussefer

EXPOSE 8086

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl --fail http://localhost:8086/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]