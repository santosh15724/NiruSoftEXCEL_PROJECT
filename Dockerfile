FROM maven:3.8.3-openjdk-17 AS build
WORKDIR /app
COPY . /app/
RUN mvn clean package


FROM openjdk:17-jdk-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app/app.jar
EXPOSE 8089
ENTRYPOINT ["java","-jar","/app.jar"]
