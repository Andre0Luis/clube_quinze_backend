# syntax=docker/dockerfile:1.4
FROM eclipse-temurin:21-jdk AS builder
ARG REPO_URL=https://github.com/Andre0Luis/clube_quinze_backend.git
ARG REPO_REF=main
WORKDIR /workspace
RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*
COPY . .
RUN if [ ! -f pom.xml ] || [ ! -d src ]; then \
			if [ -z "${REPO_URL}" ]; then \
				echo "ERRO: pom.xml/src não encontrados no build context. Defina REPO_URL para clonar o repositório." >&2; \
				exit 1; \
			fi; \
			git clone --depth 1 --branch "${REPO_REF}" "${REPO_URL}" /tmp/repo; \
			rm -rf /workspace/*; \
			cp -R /tmp/repo/. /workspace/.; \
		fi
RUN chmod +x mvnw
# Build sem BuildKit (compatível com ambientes sem suporte a --mount)
RUN ./mvnw -B -DskipTests clean package && \
		./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version > /workspace/app.version

FROM eclipse-temurin:21-jre
WORKDIR /app
# Flags otimizadas para container em VPS com pouca RAM/CPU:
# - UseContainerSupport: respeita os cgroups do container (evita over-commit de memória)
# - MaxRAMPercentage: usa até 75% da RAM disponível para heap
# - TieredCompilation + nível 4: mantém JIT completo mas prioriza startup
# - G1GC: coleta de lixo equilibrada entre latência e throughput
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=30.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENV APP_VERSION_FILE="/app/app.version"
# Copia qualquer JAR gerado pelo build (evita quebrar quando a versão do artefato muda)
COPY --from=builder /workspace/target/*.jar /app/app.jar
COPY --from=builder /workspace/app.version /app/app.version
# Copia script de espera
COPY --from=builder /workspace/scripts/wait-for-db.sh /app/wait-for-db.sh
RUN chmod +x /app/wait-for-db.sh
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "APP_VERSION=$(cat ${APP_VERSION_FILE} 2>/dev/null || true) && export APP_VERSION && /app/wait-for-db.sh ${DATABASE_HOST:-mariadb} ${DATABASE_PORT:-3306} && java $JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} -jar /app/app.jar"]
