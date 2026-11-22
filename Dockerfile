# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Baixa dependencias primeiro para aproveitar o cache de camadas do Docker.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- run ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# BRAPI_TOKEN e opcional (PETR4, VALE3, ITUB4 e MGLU3 funcionam sem token).
ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
