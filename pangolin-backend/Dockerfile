# Copyright © 2026 Pangolin
# SPDX-License-Identifier: Apache-2.0

# STEP 1: Build the JAR with Maven + JDK 21
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom and source
COPY pom.xml .
COPY src ./src

# Build the JAR (skip tests for speed)
RUN mvn clean package -DskipTests -B

# STEP 2: Run the application with JRE 21 Alpine
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built JAR
COPY --from=build /app/target/pangolin-backend-0.1.0.jar app.jar

# Standard Spring Boot port
EXPOSE 8080

# Tell Docker how to run the app
ENTRYPOINT ["java", "-jar", "app.jar"]