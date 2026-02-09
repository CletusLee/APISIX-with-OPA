FROM eclipse-temurin:17-jdk

WORKDIR /app

# Install Apache Commons Compress for tar.gz handling
# Download the JAR directly
RUN curl -L https://repo1.maven.org/maven2/org/apache/commons/commons-compress/1.21/commons-compress-1.21.jar -o commons-compress.jar

# Copy source file
COPY BuilderService.java .

# Compile with classpath
RUN javac -cp commons-compress.jar BuilderService.java

# The /app/repo directory will be mounted from docker-compose
# No need to COPY policies here - they come from the volume mount

# Run the builder with classpath
CMD ["java", "-cp", "./:commons-compress.jar", "BuilderService"]
