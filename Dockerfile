# Stage-1 Compiling executable jar
FROM jimschubert/8-jdk-alpine-mvn as build
WORKDIR /app
COPY src ./src
COPY pom.xml ./
RUN mvn -DskipTests -f pom.xml clean install

# Stage-2 dependencies
FROM openjdk:8-jdk-alpine as jdk8
# Port EXPOSE overridden in docker-compose
#EXPOSE 8081
COPY --from=build /app/target/DockerUserImage.jar DockerUserImage.jar
ENTRYPOINT java -jar DockerUserImage.jar
