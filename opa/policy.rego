# ============================================================================
# OPA Policy - Canary Deployment Routing Decision
# ============================================================================
# This policy evaluates incoming requests and determines routing behavior
# based on user identity. It is called by APISIX via HTTP API.
#
# Policy Package: apisix.route
# Endpoint: /v1/data/apisix/route
# ============================================================================

package apisix.route

# Import the request object from APISIX input
# This allows us to access request.headers directly
import input.request

# ============================================================================
# AUTHORIZATION DECISION
# ============================================================================
# Default policy: Allow all requests
# All users (both normal and beta) are authorized to access the service
# This rule can be customized to implement more complex authorization logic
default allow = true

# ============================================================================
# ROUTING HEADER INJECTION
# ============================================================================
# These rules inject custom headers that APISIX uses for routing decisions
# The headers are returned in the OPA response under result.headers

# Rule 1: Inject X-Target-Upstream header for beta users
# Purpose: Signal APISIX to route beta users to V2 (Canary) backend
# Condition: x-user-id header equals "beta_user"
# Result: Adds {"X-Target-Upstream": "v2"} to the response headers
headers["X-Target-Upstream"] = "v2" if {
    request.headers["x-user-id"] == "beta_user"
}

# Rule 2: Inject X-User-Type header for beta users
# Purpose: Additional metadata for logging and debugging
# This header can be used by backend services to identify user type
headers["X-User-Type"] = "beta" if {
    request.headers["x-user-id"] == "beta_user"
}

# ============================================================================
# HOW IT WORKS
# ============================================================================
# 1. APISIX calls this policy via POST /v1/data/apisix/route
# 2. APISIX sends request context: {"input": {"request": {"headers": {...}}}}
# 3. OPA evaluates the rules above
# 4. OPA returns decision:
#    {
#      "result": {
#        "allow": true,
#        "headers": {
#          "X-Target-Upstream": "v2",  // Only for beta_user
#          "X-User-Type": "beta"        // Only for beta_user
#        }
#      }
#    }
# 5. APISIX reads the headers from OPA response
# 6. APISIX injects X-Target-Upstream into the request
# 7. traffic-split plugin routes based on X-Target-Upstream value
#
# ROUTING OUTCOMES:
# - Normal users: No headers injected → Default to upstream_id: 1 (V1 Stable)
# - Beta users: X-Target-Upstream: v2 → Route to upstream_id: 2 (V2 Canary)
# ============================================================================
