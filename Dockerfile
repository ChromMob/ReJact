# Use a Gradle image that includes the latest Java version
FROM gradle:7.4.2-jdk17 AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy the entire project into the container
COPY . .

# Build the project using the shadowJar task
RUN ./gradlew shadowJar

# Use a smaller base image for the final run stage with the latest Java version
FROM openjdk:17-jre-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the built JAR file from the builder stage to the final stage
COPY --from=builder /app/build/libs/*.jar /app/app.jar

# Expose the port the application will run on
EXPOSE 3000

# Command to run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
