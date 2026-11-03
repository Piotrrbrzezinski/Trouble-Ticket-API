# Runtime images do not need Maven or the source tree.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
# Tests run separately with `mvn verify`; the image build only packages the application.
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system ticket && useradd --system --gid ticket --no-create-home ticket
COPY --from=build /build/target/trouble-ticket-1.0.0.jar /app/app.jar
USER ticket
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
