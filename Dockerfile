# Use Eclipse Temurin JDK 17 as the base image
FROM eclipse-temurin:17-jdk

# Set working directory in the container
WORKDIR /app

# Copy Maven wrapper and project files
COPY . .

# Make the Maven wrapper executable (important!)
RUN chmod +x ./mvnw

# Build the application without running tests
RUN ./mvnw clean package -DskipTests

# Expose port 8080 (Spring Boot default)
EXPOSE 8080

# Run the built JAR
CMD ["java", "-jar", "target/baseball-stats-0.0.1-SNAPSHOT.jar"]
