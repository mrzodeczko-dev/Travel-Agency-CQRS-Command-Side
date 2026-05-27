FROM maven:3.9-eclipse-temurin-25 AS build

ARG APP_VERSION=unknown
ARG BUILD_DATE=unknown
ARG GIT_COMMIT=unknown

WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B --no-transfer-progress -U


RUN java -Djarmode=tools -jar target/*.jar extract --destination target/extracted

FROM eclipse-temurin:25-jre-alpine AS runtime

ARG APP_VERSION=unknown
ARG BUILD_DATE=unknown
ARG GIT_COMMIT=unknown


LABEL org.opencontainers.image.title="Spring Boot Application"
LABEL org.opencontainers.image.version="${APP_VERSION}"
LABEL org.opencontainers.image.created="${BUILD_DATE}"
LABEL org.opencontainers.image.revision="${GIT_COMMIT}"
LABEL org.opencontainers.image.vendor="Rzodeczko"


RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app


COPY --from=build --chown=appuser:appgroup /build/target/extracted/lib/   ./lib/
COPY --from=build --chown=appuser:appgroup /build/target/extracted/*.jar  ./

USER appuser


EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar *.jar"]