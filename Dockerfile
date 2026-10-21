# Two stages: build with the full JDK + Maven, then run on a small Java runtime image.

# ---- Build ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Download dependencies first, so Docker can reuse this layer when only the code changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
# Tests run in GitHub Actions on every push (see .github/workflows/ci.yml)
RUN ./mvnw -q -B package -DskipTests

# ---- Run ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Don't run as root
RUN useradd --system --uid 1001 app
USER app

COPY --from=build /app/target/statdugout-api.jar app.jar

# Render's free plan has 512 MB of memory; let the JVM size its heap to fit
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
