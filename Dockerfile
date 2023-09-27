FROM maven:3.8.3-openjdk-17 AS build
WORKDIR /app
COPY . /app/
RUN mvn clean package

# Create a directory for images and copy the image
RUN mkdir -p app/resources/Image
COPY resources/Image/SKTRADER.jpg app/resources/Image/



FROM openjdk:17-jdk-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app/app.jar
EXPOSE 8089
ENTRYPOINT ["java","-jar","/app.jar"]
