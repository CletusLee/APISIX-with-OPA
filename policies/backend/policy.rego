# ============================================================================
# Backend Service Policy - Canary Deployment Routing
# ============================================================================
# This policy implements canary routing logic for the backend service.
# It uses dynamic data from the bundle to determine:
# 1. Which users are in the canary allowlist
# 2. What percentage of traffic should go to canary
#
# Data source: /data/data.json in the bundle
# ============================================================================

package backend.authz

import input.request
import data.data.canary

# ============================================================================
# AUTHORIZATION DECISION
# ============================================================================

# Default: Allow all requests
# This policy focuses on routing, not access control
default allow = true

# ============================================================================
# CANARY ROUTING LOGIC
# ============================================================================

# Inject V2 routing header if user is in allowlist OR falls within percentage
headers["X-Target-Upstream"] = "v2" if {
    is_canary_user
}

headers["X-Target-Upstream"] = "v2" if {
    is_canary_percentage
    # Ensure allowlist users aren't counted twice logically (though idempotent)
    not is_canary_user
}

# Inject user type header for observability
headers["X-User-Type"] = "canary" if {
    is_canary_user
}

headers["X-User-Type"] = "canary_percentage" if {
    is_canary_percentage
    not is_canary_user
}

# Inject user type for normal users
headers["X-User-Type"] = "normal" if {
    not is_canary_user
    not is_canary_percentage
}

# ============================================================================
# HELPER RULES
# ============================================================================

# Check if user is in the canary allowlist
is_canary_user if {
    user_id := request.headers["x-user-id"]
    user_id == canary.allowlist_users[_]
}

# Percentage-based rollout logic
is_canary_percentage if {
    user_id := request.headers["x-user-id"]
    
    # Hash the user ID to get a deterministic hex string
    hash := crypto.md5(user_id)
    
    # Take the first 2 characters (1 byte) of the hex hash
    # This gives us a uniform distribution from 00 to ff (0-255)
    first_char := substring(hash, 0, 1)
    second_char := substring(hash, 1, 1)
    
    # Map hex chars to integers
    hex_map := {
        "0": 0, "1": 1, "2": 2, "3": 3, "4": 4, "5": 5, "6": 6, "7": 7, 
        "8": 8, "9": 9, "a": 10, "b": 11, "c": 12, "d": 13, "e": 14, "f": 15
    }
    
    val1 := hex_map[first_char]
    val2 := hex_map[second_char]
    
    # Calculate integer value (0-255)
    int_val := (val1 * 16) + val2
    
    # Normalize to 0-99 percentage
    # (int_val * 100) / 256
    percentage := (int_val * 100) / 256
    
    # Check if within rollout threshold
    percentage < canary.rollout_percentage
}

# ============================================================================
# DATA STRUCTURE EXPECTED
# ============================================================================
# This policy expects the following data structure in /data/data.json:
# {
#   "canary": {
#     "allowlist_users": ["beta_user", "alpha_tester"],
#     "rollout_percentage": 10
#   }
# }
#
# PRODUCTION NOTE:
# In production, this data should come from a Backend Service API
# rather than a static file. The Bundle Builder should fetch:
#   GET /internal/canary-config
# This allows runtime updates without bundle rebuilds.
# ============================================================================
