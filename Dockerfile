# Runtime-only image. The JAR is built and tested ONCE by Maven in the pipeline,
# then copied in here - we do not rebuild or re-test inside Docker (that would be waste).
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Run as a non-root user (security best practice)
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --chown=app:app target/app.jar app.jar

EXPOSE 8080

# Respect the container's memory limit instead of the node's total RAM
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
