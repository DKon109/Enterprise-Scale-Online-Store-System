FROM gradle:8.9.0-jdk17 AS build
WORKDIR /home/gradle/app

# Copy Gradle wrapper and build scripts first for better layer caching
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle

# Copy application source
COPY src src

# Ensure the wrapper is executable and build the Spring Boot fat jar
RUN chmod +x gradlew && \
    ./gradlew bootJar --no-daemon && \
    JAR_PATH=$(find build/libs -maxdepth 1 -type f -name "*.jar" ! -name "*-plain.jar" | head -n 1) && \
    mkdir -p build/output && \
    cp "${JAR_PATH}" build/output/app.jar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /home/gradle/app/build/output/app.jar ./app.jar

EXPOSE 10000

ENTRYPOINT ["java","-jar","/app/app.jar"]
