# syntax=docker/dockerfile:1
FROM gradle:8.14.5-jdk21 AS build
USER root
RUN install -d -o gradle -g gradle /workspace
WORKDIR /workspace
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts ./
COPY --chown=gradle:gradle services/ services/
USER gradle
ARG SERVICE
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000,sharing=locked \
    test -n "$SERVICE" && \
    gradle --no-daemon --max-workers=2 ":services:${SERVICE}:bootJar" && \
    set -- services/"$SERVICE"/build/libs/*.jar && \
    test "$#" -eq 1 && cp "$1" /tmp/app.jar

FROM eclipse-temurin:21-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system --gid 10001 app && \
    useradd --system --uid 10001 --gid app --home-dir /app app && \
    install -d -o app -g app /app
WORKDIR /app
COPY --from=build --chown=app:app /tmp/app.jar app.jar
ARG SERVICE_PORT
ENV SERVER_PORT=${SERVICE_PORT}
EXPOSE ${SERVICE_PORT}
USER app
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=4 \
    CMD status=$(curl -sS --max-time 4 -o /dev/null -w '%{http_code}' "http://127.0.0.1:${SERVER_PORT}/") && \
    case "$status" in 2??|3??|401|403|404) exit 0 ;; *) exit 1 ;; esac
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
