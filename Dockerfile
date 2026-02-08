# syntax=docker/dockerfile:1.4
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
COPY src src
RUN chmod +x mvnw
# Build sem BuildKit (compatível com ambientes sem suporte a --mount)
RUN ./mvnw -B -DskipTests clean package && \
	./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version > /workspace/app.version

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV JAVA_OPTS=""
ENV APP_VERSION_FILE="/app/app.version"
# Copia qualquer JAR gerado pelo build (evita quebrar quando a versão do artefato muda)
COPY --from=builder /workspace/target/*.jar /app/app.jar
COPY --from=builder /workspace/app.version /app/app.version
# Copia script de espera
COPY scripts/wait-for-db.sh /app/wait-for-db.sh
RUN chmod +x /app/wait-for-db.sh
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "APP_VERSION=$(cat ${APP_VERSION_FILE} 2>/dev/null || true) && export APP_VERSION && /app/wait-for-db.sh ${DATABASE_HOST:-mariadb} ${DATABASE_PORT:-3306} && java $JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} -jar /app/app.jar"]
