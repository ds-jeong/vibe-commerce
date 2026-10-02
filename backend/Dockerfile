FROM gradle:8.5-jdk17 AS builder
WORKDIR /workspace

COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN gradle build -x test --no-daemon \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN mkdir -p /app/uploads

COPY --from=builder /workspace/app.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV FILE_UPLOAD_DIR=/app/uploads

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "/app/app.jar"]
