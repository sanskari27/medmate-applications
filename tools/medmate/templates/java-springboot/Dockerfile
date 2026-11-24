FROM maven:3.9-eclipse-temurin-21 AS dev
WORKDIR /app
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src

FROM dev AS format
RUN mvn -B spotless:check

FROM dev AS lint
RUN mvn -B -DskipTests compile

FROM dev AS typecheck
RUN mvn -B -DskipTests compile

FROM dev AS unit
RUN mvn -B -Dtest=MessageTest test

FROM dev AS integration
RUN mvn -B -Dtest=MessageIT test

FROM dev AS build
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /app/target/__NAME__-0.0.1.jar /app/app.jar
ENV PORT=__PORT__
EXPOSE __PORT__
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
