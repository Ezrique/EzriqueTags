# Build
FROM openjdk:21-jdk-oracle as builder

## Set up our working directory
ENV APP_HOME=/app
WORKDIR $APP_HOME

## Copy relevant build files
COPY gradlew $APP_HOME/
COPY gradle $APP_HOME/gradle
COPY gradle.properties $APP_HOME/
COPY build.gradle.kts $APP_HOME/
COPY settings.gradle.kts $APP_HOME/
COPY src $APP_HOME/src

## Build the application
RUN ./gradlew build

# Run
FROM openjdk:21-jdk-oracle

## Set up our working directory
ENV APP_HOME=/app
WORKDIR $APP_HOME

## Copy the built JAR file
COPY --from=builder $APP_HOME/build/libs/ezrique-tags.jar $APP_HOME/ezrique-tags.jar

## Set up healthchecks
HEALTHCHECK --interval=30s --timeout=30s --start-period=30s \
    --retries=3 CMD curl -f http://localhost:6139/health || exit 1

## Run the application
ENTRYPOINT ["java", "-jar", "ezrique-tags.jar"]
