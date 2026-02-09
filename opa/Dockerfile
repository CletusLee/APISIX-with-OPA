FROM openpolicyagent/opa:latest

# Copy OPA configuration for bundle polling
COPY config.yaml /config/config.yaml

# No policies copied - they come from the bundle!

# Run OPA server with config, binding to all interfaces
CMD ["run", "--server", "--addr", "0.0.0.0:8181", "--config-file", "/config/config.yaml"]
