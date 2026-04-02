# Use a lightweight base image with Java 17
FROM eclipse-temurin:17-jre-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy the packaged jar file into the container
# Adjust the path if your jar is built somewhere other than /target
COPY target/*.jar app.jar

# Expose the port your application runs on (default Spring Boot port is 8080)
EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]