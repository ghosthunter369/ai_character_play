ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JAVA_IMAGE=eclipse-temurin:21-jre
FROM ${MAVEN_IMAGE} AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package
COPY docker/health/HealthCheck.java /tmp/HealthCheck.java
RUN javac -d /tmp/health /tmp/HealthCheck.java
FROM ${JAVA_IMAGE}
WORKDIR /app
COPY --from=build /build/target/AiChat-*.jar /app/app.jar
COPY --from=build /tmp/health /app/health
USER 10001:10001
EXPOSE 8123
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
