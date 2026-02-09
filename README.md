# APISIX + OPA Canary Deployment PoC

This project demonstrates a robust **Canary Deployment** routing strategy using **APISIX** and **OPA (Open Policy Agent)**. It implements a **File-Based Hybrid Bundle Architecture**, where policy logic is decoupled from configuration data.

## Architecture Diagram

The system uses a sidecar pattern where APISIX offloads routing decisions to OPA. OPA dynamically fetches policy bundles from a central server.

```mermaid
flowchart TB
    subgraph "Client Layer"
        Client1["👤 Normal User<br/>(x-user-id: alice)"]
        Client2["👤 Beta User<br/>(x-user-id: beta_user)"]
    end

    subgraph "Deployment Pipeline"
        Repo["� Repository<br/>/policies/backend/<br/>• policy.rego<br/>• data.json"]
        Builder["🔧 Bundle Builder<br/>Service<br/>(Polls SCM/File System)"]
        Server["� Bundle Server<br/>Artifact Repository<br/>Port: 8888"]
    end
    
    subgraph "Edge Gateway (APISIX)"
        APISIX["� APISIX<br/>Gateway :9080"]
        
        subgraph "Routing Logic"
            Lua["📝 Serverless Lua<br/>1. Extract Header<br/>2. Call OPA<br/>3. Enforce Route"]
        end
    end
    
    subgraph "Policy Engine"
        OPA["🔐 OPA Sidecar<br/>Port: 8181"]
    end
    
    subgraph "Upstreams"
        V1["✅ Backend V1<br/>(Stable)<br/>:8081"]
        V2["🧪 Backend V2<br/>(Canary)<br/>:8082"]
    end

    %% Pipeline Flow
    Repo -->|1. Detect Change| Builder
    Builder -->|2. Build & Upload<br/>authz.tar.gz| Server
    Server -->|3. Poll Bundle| OPA

    %% Request Flow
    Client1 --> APISIX
    Client2 --> APISIX
    APISIX --> Lua
    Lua -->|4. Authz Check<br/>(input: ...)| OPA
    OPA -->|5. Decision<br/>(upstream: v2)| Lua
    Lua -->|6. Route| V2
    Lua -->|6. Default| V1
    
    style Client1 fill:#e1f5ff,stroke:#01579b
    style Client2 fill:#fff3e0,stroke:#e65100
    style Builder fill:#fce4ec,stroke:#880e4f
    style Server fill:#f3e5f5,stroke:#4a148c
    style OPA fill:#e8f5e9,stroke:#1b5e20
    style APISIX fill:#fff8e1,stroke:#f57f17
```

---

## Key Features

1.  **Dynamic Routing**:
    *   **Allowlist**: Specific users (e.g., `beta_user`) are routed to V2.
    *   **Percentage Rollout**: Hash-based traffic splitting (e.g., 10% of users to V2).
    
2.  **Hybrid Bundle Workflow**:
    *   **Policy (`.rego`)**: Static logic defining *how* to route.
    *   **Data (`.json`)**: Dynamic configuration defining *who* to route.
    *   **Bundle Builder**: Automatically packages and distributes updates without restarting OPA.

3.  **Governance**:
    *   Centralized policy management.
    *   Audit logs for all routing decisions.

---

## Project Structure

| Component | Path | Description |
|-----------|------|-------------|
| **Bundle Builder** | [`bundle-builder/`](bundle-builder/) | Java service that polls `policies/` and uploads bundles. |
| **Bundle Server** | [`bundle-server/`](bundle-server/) | Simple HTTP server acting as the bundle artifact repo. |
| **OPA Policies** | [`policies/backend/`](policies/backend/) | Rego logic and JSON data for canary routing. |
| **Backend** | [`backend/`](backend/) | Simple Java HTTP server enabling V1/V2 responses. |
| **APISIX Config** | [`apisix/apisix.yaml`](apisix/apisix.yaml) | Gateway configuration with embedded Lua script. |

---

## Quick Start

### 1. Prerequisites
- Docker & Docker Compose

### 2. Start Services
```bash
docker-compose up -d --build
```

### 3. Verify Routing
**Normal User (V1):**
```bash
curl -H "x-user-id: alice" http://localhost:9080/
# Output: Response from V1 (Stable)
```

**Beta User (V2):**
```bash
curl -H "x-user-id: beta_user" http://localhost:9080/
# Output: Response from V2 (Canary)
```

**Verify Bundle Update:**
1. Edit `policies/backend/data.json` and add a new user.
2. Wait ~20 seconds for propagation.
3. Verify the new user is routed to V2.

---

## Production Considerations

> [!IMPORTANT]
> **Data Source Integration**
>
> In this PoC, the Bundle Builder reads `data.json` from the file system. In a production environment, this should be replaced with an API call to a configuration service or feature flag system.
>
> **Recommended implementation**:
> ```java
> // BuilderService.java
> URL url = new URL("http://config-service/canary/rules");
> String canaryData = fetchFromApi(url);
> // ... package into bundle
> ```

---

## Documentation

- **[Test Plan](TEST_PLAN.md)**: Detailed test scenarios and strategy.
- **[Test Report](TEST_REPORT.md)**: Evidence of successful verification.

