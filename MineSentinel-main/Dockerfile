FROM docker.io/library/maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
COPY python_client/epp_config.yaml ./python_client/
RUN mvn -q package -DskipTests

FROM docker.io/library/eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75" \
    BROWSER_AUTO_LAUNCH=false \
    SPRING_THYMELEAF_CACHE=true
CMD ["java", "-jar", "app.jar"]
