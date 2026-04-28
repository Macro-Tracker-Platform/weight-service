FROM olehprukhnytskyi/base-java-otel:21
WORKDIR /app
COPY target/macro-tracker-weight-service-0.0.1-SNAPSHOT.jar macro-tracker-weight-service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "macro-tracker-weight-service.jar"]
