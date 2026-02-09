FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy source file
COPY BundleServer.java .

# Compile
RUN javac BundleServer.java

# Create bundles directory
RUN mkdir -p /bundles

# Expose port
EXPOSE 8888

# Run the server
CMD ["java", "BundleServer"]
