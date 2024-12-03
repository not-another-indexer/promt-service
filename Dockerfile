FROM openjdk:21-jdk-slim AS build

RUN apt-get update && apt-get install -y maven

ENV APP_HOME=/app
WORKDIR $APP_HOME

COPY . .

RUN chmod +x gradlew

RUN ./gradlew shadowJar

FROM openjdk:21-jdk-slim

COPY --from=build /app/build/libs/*.jar app.jar

ENV DB_URL=jdbc:postgresql://localhost:5432/nai_db
ENV DB_USER=nai_user
ENV DB_PASSWORD=nai_password
ENV GRPC_PORT=8080
ENV CLOUDBERRY_HOST=localhost
ENV CLOUDBERRY_PORT=50051

ENTRYPOINT ["java", "-jar", "app.jar"]