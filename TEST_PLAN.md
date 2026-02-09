# Comprehensive System Verification Plan

## 1. Objectives
- Validate end-to-end routing logic (APISIX -> OPA -> Backend).
- Verify dynamic configuration updates (Bundle Builder -> Bundle Server -> OPA).
- Confirm system observability via log correlation across all components.

## 2. Test Cases

### TC-01: Default Routing (Baseline)
- **Description**: Verify that users not in the allowlist and outside the rollout percentage allowlist are routed to the stable backend.
- **Input**: HTTP Request with `x-user-id: alice` (or any random ID resolving >10%).
- **Expected Result**: 
  - Trace: APISIX -> OPA (Normal) -> **Backend V1**
  - Response: "Response from V1 (Stable)"
- **Logs to Verify**:
  - APISIX: "No X-Target-Upstream header" or "upstream_id: 1"

### TC-02: Whitelist Enforcement
- **Description**: Verify that users explicitly allowed in `data.json` are routed to the canary backend.
- **Input**: HTTP Request with `x-user-id: beta_user`.
- **Expected Result**: 
  - Trace: APISIX -> OPA (Canary allowed) -> **Backend V2**
  - Response: "Response from V2 (Canary)"
- **Logs to Verify**:
  - OPA: "decision_id", result "v2"
  - APISIX: "OPA Target Upstream: v2"

### TC-03: Percentage Rollout Accuracy
- **Description**: Verify that approximately 10% of users are effectively routed to V2 based on the MD5 hashing logic.
- **Input**: Batch of 100 requests with unique, sequential User IDs (`user_0` to `user_99`).
- **Expected Result**: 
  - ~10 requests routed to V2.
  - ~90 requests routed to V1.
- **Logs to Verify**:
  - Backend V1 & V2 access logs counting unique IDs.

### TC-04: Dynamic Bundle Update (End-to-End Latency)
- **Description**: measure the time taken for a configuration change to propagate to the edge.
- **Steps**:
  1. **T=0s**: `gamma_user` routes to V1.
  2. **Action**: Append `gamma_user` to `policies/backend/data.json`.
  3. **Observation**: Monitor Bundle Builder output for build completion.
  4. **Observation**: Monitor OPA logs for bundle reload.
  5. **Result**: `gamma_user` routes to V2.
- **Metrics**: Total propagation time (Target: < 30s).

## 3. Log Capture & Timeline Strategy
A specialized test runner script will be used to:
1. **Mark Time**: Print precise timestamps before and after each test case.
2. **Isolate Logs**: Capture docker logs for `apisix`, `opa`, `bundle-builder`, and `backend` specifically for the duration of the test.
3. **Correlate**: Match request IDs (where available) or timestamps across components to demonstrate the flow.

## 4. Execution Plan
1. Reset `data.json` to initial state.
2. Flush/Rotate docker logs to ensure clean capture.
3. Execute Test Runner.
4. Compile `comprehensive_test_report.md`.
