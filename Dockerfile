FROM eclipse-temurin:23-jdk
WORKDIR /app
COPY PollutionScoring.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
