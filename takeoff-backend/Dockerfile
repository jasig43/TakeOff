# Backend image for the hosted demo (Render). Build context: the takeoff-backend folder.
#
#   docker build -t takeoff-backend .
#   docker run -p 8080:8080 -e DATABASE_URL=... -e DATABASE_USER=... -e DATABASE_PASSWORD=... \
#              -e REDIS_URL=... -e JWT_SECRET=... -e FRONTEND_ORIGIN=... takeoff-backend
#
# The "demo" profile needs no RabbitMQ or MySQL: PostgreSQL + a Redis list queue, and documents kept in the database.
# See DEPLOYMENT.md at the repository root for every variable.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Dependencies first, so a source-only change does not download them again.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package && cp target/takeoff-backend-*.jar app.jar

FROM eclipse-temurin:17-jre
# A free instance has about 512 MB in total, so keep the heap well inside it.
ENV SPRING_PROFILES_ACTIVE=demo \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
RUN useradd --system --uid 1001 takeoff
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER takeoff
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
