# Build the JAR locally first: mvnw package -DskipTests
# Then run: docker-compose up -d --build
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
