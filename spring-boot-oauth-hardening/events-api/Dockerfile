FROM gradle:9.8.0-jdk25 AS build
WORKDIR /src
COPY settings.gradle build.gradle ./
# Resolve dependencies in their own layer so code changes don't re-download them
RUN gradle --no-daemon --quiet dependencies > /dev/null
COPY src ./src
RUN gradle --no-daemon --quiet bootJar

FROM eclipse-temurin:25.0.4.1_1-jre-noble
# curl: for the compose health check
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/build/libs/events-api.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
