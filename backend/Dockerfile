FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy source file
COPY Backend.java .

# Compile
RUN javac Backend.java

# Expose port
EXPOSE 8080

# Run the server
CMD ["java", "Backend"]
