FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -q

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/servidor-pedidos-1.0.jar app.jar
EXPOSE 8888
CMD ["java", "-jar", "app.jar"]