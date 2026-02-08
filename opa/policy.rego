package apisix.route

import input.request

# Default allow to true for all requests
default allow = true

# Inject headers based on user ID for canary routing
headers["X-Target-Upstream"] = "v2" if {
    request.headers["x-user-id"] == "beta_user"
}

headers["X-User-Type"] = "beta" if {
    request.headers["x-user-id"] == "beta_user"
}
