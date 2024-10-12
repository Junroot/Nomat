FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY src ./src
COPY gradle ./gradle

RUN ./gradlew build -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/build/libs/nomat-0.0.1-SNAPSHOT.jar /app/nomat.jar

ENTRYPOINT ["java", "-jar", "/app/nomat.jar"]
