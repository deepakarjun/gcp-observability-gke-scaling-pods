# ----------------------
# Stage 1: Build
# ----------------------
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# ----------------------
# Stage 2: Runtime
# ----------------------
FROM eclipse-temurin:17-jre-alpine

RUN apk update && apk upgrade --no-cache

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

USER appuser
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]