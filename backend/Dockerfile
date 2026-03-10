FROM eclipse-temurin:17
WORKDIR /app
COPY target/chest-xray-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]