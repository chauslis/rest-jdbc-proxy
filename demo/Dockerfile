FROM gradle:9.2.1-jdk21 AS build

WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
COPY src/main/resources/access_descriptor /app/access_descriptor

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
