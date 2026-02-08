FROM openpolicyagent/opa:latest
COPY policy.rego /policy.rego
CMD ["run", "--server", "--addr=:8181", "/policy.rego"]
EXPOSE 8181
