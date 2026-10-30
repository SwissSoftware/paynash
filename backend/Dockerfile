# Production image of the backend (task 8.1). Build context: backend/
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp package -DskipTests && cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S pos && adduser -S -G pos pos \
	&& mkdir -p /data/images && chown -R pos:pos /data
COPY --from=build /app.jar /app/app.jar
USER pos
ENV POS_PORT=8081 POS_IMAGE_DIR=/data/images TZ=Europe/Zurich
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
	CMD wget -q -O /dev/null http://127.0.0.1:8081/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
