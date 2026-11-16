# Этап 1: собираем jar
FROM gradle:8.7-jdk17 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle shadowJar --no-daemon -x test

# Этап 2: лёгкий образ только для запуска
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/server.jar server.jar
EXPOSE 8080
# -Xmx300m: на VPS всего ~1,8 ГБ ОЗУ, сервер не должен «съесть» всё
ENTRYPOINT ["java", "-Xmx300m", "-jar", "server.jar"]
