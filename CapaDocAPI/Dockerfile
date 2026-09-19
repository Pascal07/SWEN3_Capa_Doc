# Stage 1: Build-Stufe mit Java 25 JDK
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /app

# Maven Wrapper und Konfiguration kopieren
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw

# Abhängigkeiten vorab herunterladen (Caching-Optimierung)
RUN ./mvnw dependency:go-offline -B

# Quellcode kopieren und JAR-Datei bauen
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime-Stufe mit Java 25 JRE
FROM eclipse-temurin:25-jre-noble AS runtime
WORKDIR /app

# Erstellte JAR-Datei aus der Build-Stufe übernehmen
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]