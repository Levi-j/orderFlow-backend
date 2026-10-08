FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -ntp dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-noble
RUN groupadd --gid 10001 orderflow \
    && useradd --uid 10001 --gid orderflow --no-create-home --shell /usr/sbin/nologin orderflow
WORKDIR /app
COPY --from=build /workspace/target/orderflow-0.0.1-SNAPSHOT.jar app.jar
USER orderflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
