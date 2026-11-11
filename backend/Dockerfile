# syntax=docker/dockerfile:1
# Двухэтапная сборка: в первом образе Maven собирает jar, во второй (лёгкий JRE) копируется только jar.

# ---------- 1. Сборка ----------
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# --mount=type=cache: скачанные зависимости Maven кешируются между сборками
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package \
 && cp target/hotel-booking-backend-*.jar /build/app.jar

# ---------- 2. Запуск ----------
FROM eclipse-temurin:21-jre
# приложение работает не от root
RUN useradd --system --uid 10001 --home-dir /app hotel
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
USER hotel
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Dfile.encoding=UTF-8", "-jar", "/app/app.jar"]
