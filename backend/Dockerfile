# ── Stage 1: Build com Maven ───────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

COPY pom.xml .
# Baixa dependências primeiro (cache de camada)
RUN mvn -B dependency:go-offline -f pom.xml 2>/dev/null || true

COPY src ./src
RUN mvn -B package -DskipTests

# ── Stage 2: Imagem de execução (só JRE, menor) ────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S acougue && adduser -S acougue -G acougue
USER acougue

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=America/Sao_Paulo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
