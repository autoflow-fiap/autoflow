# Build
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /autoflow

COPY pom.xml .
RUN mvn dependency:go-offline
RUN mvn dependency:get \
    -Dartifact=com.newrelic.agent.java:newrelic-agent:9.5.0 \
    -Dtransitive=false \
    && echo "411587286dbb83b06fcdd042ad1c9c1818655831366717aef3611962ea2c1b73  /root/.m2/repository/com/newrelic/agent/java/newrelic-agent/9.5.0/newrelic-agent-9.5.0.jar" \
       | sha256sum --check --status

COPY src ./src
RUN mvn clean package -DskipTests

# Runtime
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S autoflowuser -G appgroup

WORKDIR /autoflow

COPY --from=build --chown=autoflowuser:appgroup /autoflow/target/*.jar app.jar
RUN mkdir -p /opt/newrelic && chown autoflowuser:appgroup /opt/newrelic
COPY --from=build --chown=autoflowuser:appgroup \
    /root/.m2/repository/com/newrelic/agent/java/newrelic-agent/9.5.0/newrelic-agent-9.5.0.jar \
    /opt/newrelic/newrelic.jar
COPY --chown=autoflowuser:appgroup newrelic.yml /opt/newrelic/newrelic.yml
COPY --chown=autoflowuser:appgroup docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod 0555 /usr/local/bin/docker-entrypoint.sh

USER autoflowuser

EXPOSE 8081

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["-jar", "/autoflow/app.jar"]
