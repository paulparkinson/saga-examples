
# 1st stage, build the app
FROM maven:3.6-jdk-11 as build

WORKDIR /app

# Create a first layer to cache the "Maven World" in the local repository.
# Incremental docker builds will always resume after that, unless you update
# the pom
ADD pom.xml .

# Do the Maven build!
# Incremental docker builds will resume here when you change sources
ADD src src
RUN mvn package -DskipTests

# 2nd stage, build the runtime image
FROM openjdk:11-jre-slim
WORKDIR /app

# Copy the binary built in the 1st stage
COPY --from=build /app/target/account.jar ./

CMD ["java", "-Dorg.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH=true", "-Dorg.apache.catalina.connector.CoyoteAdapter.ALLOW_BACKSLASH=true", "-jar", "account.jar"]

#EXPOSE 8083