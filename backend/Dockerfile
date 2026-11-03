# syntax=docker/dockerfile:1
# Build: the Maven wrapper downloads Maven itself; dependencies are cached in their own layer.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests && cp target/invoicing-*.jar app.jar

# Run: JRE only, as a non-root user.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 fatoora
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER fatoora
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
