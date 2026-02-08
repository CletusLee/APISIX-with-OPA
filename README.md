# APISIX + OPA Canary PoC

This project demonstrates a Canary Deployment routing strategy using APISIX and Open Policy Agent (OPA).
APISIX is running in Standalone mode, using a local `apisix.yaml`.

## Architecture

1.  **Backend V1**: Stable version.
2.  **Backend V2**: Canary version.
3.  **OPA**: Policy engine checking `x-user-id` header.
    - If `x-user-id` is `beta_user`, acts as a Beta user.
4.  **APISIX**:
    - **OPA Plugin**: Checks policy. Policy injects header `X-Target-Upstream: v2` for beta users.
    - **Traffic-Split Plugin**: Checks for `X-Target-Upstream` header. If `v2`, routes to Backend V2.

## How to Run

1.  **Build and Start**:
    ```powershell
    docker-compose up -d --build
    ```

    Ensure you are in the directory containing `docker-compose.yml`.

2.  **Wait for services**: ensure APISIX and others are healthy (wait ~10-20 seconds).
    You can check status with:
    ```powershell
    docker-compose ps
    ```

## Testing

**Scenario 1: Normal User**
Should go to V1.
```bash
curl -H "x-user-id: regular_user" http://localhost:9080/
# Expected Output: Response from V1 (Stable)
```

**Scenario 2: Beta User (Canary)**
Should go to V2.
```bash
curl -H "x-user-id: beta_user" http://localhost:9080/
# Expected Output: Response from V2 (Canary)
```

## Troubleshooting
- Check OPA logs: `docker-compose logs opa`
- Check APISIX logs: `docker-compose logs apisix`
