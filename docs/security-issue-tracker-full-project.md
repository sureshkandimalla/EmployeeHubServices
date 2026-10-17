# Security Issue Tracker — Full Project

Findings from security audits of the complete project: both the backend (Spring Boot + MySQL,
`EmployeeHubServices`) and the frontend (React/CRA + Ant Design, `BeanEMS`). Started as a
backend-only audit (2026-10-17, findings #1-#5) covering authentication/authorization, injection,
secrets, file-upload/document handling, CORS, and mass assignment; extended the same day to a
full-project pass covering the frontend and every remaining backend controller. Root cause behind
nearly every open/fixed backend item: the app has authentication (a valid JWT is required) but,
before this tracker started, no authorization layer at all — no endpoint checked the caller's role.

| # | Status | Severity | Type | Category | Summary |
|---|--------|----------|------|----------|---------|
| 1 | ✅ Fixed | Critical | Backend | authz | Any authenticated user could self-assign the `ADMIN` role |
| 2 | ✅ Fixed | Critical | Backend | authz | Any authenticated user could merge/overwrite another employee's entire record via the migration endpoint |
| 3 | 🔲 Open | Critical | Backend | idor | Any authenticated user can read/delete any employee's S3 documents by guessing the numeric id |
| 4 | 🔲 Open | High | Backend | mass-assignment | Document confirm endpoint saves the full client-supplied JSON, including a client-settable `id` |
| 5 | 🔲 Open | High | Backend | authz | `getAllEmployees` returns every employee's SSN/DOB/salary to any authenticated user regardless of role |
| 6 | 🔲 Open | Critical | Backend | authz | Any authenticated user can permanently wipe the entire payroll-summary or health-insurance table |
| 7 | 🔲 Open | Critical | Backend | authz | `requireRole` exists on only 2 of 31 controllers — nearly every financial/immigration/customer write and delete endpoint has no role check at all |
| 8 | 🔲 Open | High | Backend | authz | Bulk master-data import (Employees/Customers/Projects/Assignments) has no role check |
| 9 | 🔲 Open | High | Frontend | authz-design | Frontend explicitly documents (in its own code comments) that Archive/Delete role gating is UI-only and never reaches the backend |
| 10 | 🔲 Open | Medium | Backend | authz | `ActionRequestController.resolve` lets any authenticated user forge an "admin approved" audit entry |

---

## #1 — Unrestricted role self-assignment to ADMIN

- **Status:** Fixed (2026-10-17)
- **Severity:** Critical
- **Type:** Backend
- **Category:** authz
- **File:** `src/main/java/com/employeehub/controller/UserRoleController.java:39` (`PUT /api/v1/userRoles`)

**Failure scenario:** Any user with a valid login JWT sends `PUT /api/v1/userRoles` with body
`{"email":"<their own email>","role":"ADMIN"}`. `upsertUserRole()` did a plain lookup-or-create and
`save()` with no check that the caller already held admin rights.

**Fix:** Added `CurrentUserContext` (`src/main/java/com/employeehub/config/CurrentUserContext.java`),
a request-scoped holder for the caller's email + role, populated by `AuthFilter` on every request
(role resolved fresh per-request from `UserRoleRepository`, since the JWT itself carries no role
claim). `upsertUserRole` and `deleteUserRole` now call `CurrentUserContext.requireRole("ADMIN")`
as their first line.

**Verified:** non-admin JWT → `PUT /api/v1/userRoles` self-escalation → `403`. Legitimate admin JWT
→ same call for a different user → `200`, unchanged behavior.

---

## #2 — Unrestricted cross-employee record-merge endpoint

- **Status:** Fixed (2026-10-17)
- **Severity:** Critical
- **Type:** Backend
- **Category:** authz
- **File:** `src/main/java/com/employeehub/controller/MigrationController.java:29` (`POST /api/v1/migration/reassignEmployeeId`)

**Failure scenario:** Any logged-in user (any role, including one with no role assigned at all)
could call `reassignEmployeeId?oldId=X&newId=Y` and rewrite `employee_id` across 15 tables (HR,
payroll, visa, immigration, financial), merging or corrupting another employee's entire record
company-wide in one request. SQL itself was parameterized (not injectable) — the bug was that
nothing restricted who could call it.

**Fix:** `CurrentUserContext.requireRole("ADMIN")` added as the first line of
`reassignEmployeeId`.

**Verified:** non-admin JWT → call with dummy ids → `403`, no FK-check toggle or UPDATE statements
executed.

---

## #3 — IDOR on document download/delete by raw id

- **Status:** Open
- **Severity:** Critical
- **Type:** Backend
- **Category:** idor
- **File:** `src/main/java/com/employeehub/controller/DocumentController.java:58,63`; `src/main/java/com/employeehub/service/DocumentService.java:123-141`

**Failure scenario:** `GET /api/v1/documents/{id}/downloadUrl` and `DELETE /api/v1/documents/{id}`
look the `Document` up by its raw auto-increment PK with no check that the caller is associated
with the document's `entityType`/`entityId`. Any authenticated user enumerating ids can obtain a
presigned S3 GET URL for, or permanently delete, any other employee's/vendor's passport, visa
approval, I-94, or other document in the shared tenant S3 bucket.

**Proposed fix (not yet implemented):** resolve the document's owning employee (same
`resolveRootFolder`-style walk `DocumentService.presignUpload` already does for the upload path)
and require the caller to either be HR/ADMIN or be that employee themselves, before issuing a
download URL or allowing delete.

---

## #4 — Mass-assignment on Document confirm endpoint

- **Status:** Open
- **Severity:** High
- **Type:** Backend
- **Category:** mass-assignment
- **File:** `src/main/java/com/employeehub/controller/DocumentController.java:39`; `src/main/java/com/employeehub/service/DocumentService.java:113-115`

**Failure scenario:** `confirm()` passes the entire client-supplied `Document` JSON — including a
client-settable `id` — straight to `documentRepository.save()`. Since JPA's `save()` merges/updates
when a non-null id matches an existing row, a caller can submit another document's id and overwrite
its `entityId`/`s3Key`/`description`, or submit `entityType`/`entityId` that doesn't match what was
actually presigned/uploaded.

**Proposed fix (not yet implemented):** build the saved `Document` server-side from the presign
response (`s3Key` returned by `/presign`) plus only the client-supplied `documentType`/`description`
fields; never accept a client-supplied `id`, `entityType`, or `entityId` at confirm time — derive
them from the presign call that preceded it (e.g. a short-lived server-side mapping from the
presign response to the entity it was issued for).

---

## #5 — SSN/salary exposed to any authenticated user regardless of role

- **Status:** Open
- **Severity:** High
- **Type:** Backend
- **Category:** authz
- **File:** `src/main/java/com/employeehub/controller/EmployeeController.java:41` (`getAllEmployees`, `getEmployees`, `employees`)

**Failure scenario:** These endpoints return the full `Employee` entity (SSN, DOB, `annualPay`,
visa status, etc.) for every employee in the tenant to any caller holding a valid JWT — no
HR/ACCOUNTING/ADMIN-only gate exists, so a caller with no assigned role (or an IMMIGRATION-only
role that should never see payroll data) sees every employee's SSN and compensation.

**Proposed fix (not yet implemented):** gate `getAllEmployees`/`employees` behind
`CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING")`, or return a reduced DTO (no SSN,
no `annualPay`) for roles that don't need financial/PII fields (e.g. IMMIGRATION).

---

## #6 — Unrestricted bulk deleteAll on payroll-summary and health-insurance tables

- **Status:** Open
- **Severity:** Critical
- **Type:** Backend
- **Category:** authz
- **File:** `src/main/java/com/employeehub/controller/PayrollSummaryController.java:86-90` (`DELETE /api/v1/payrollsummary/deleteAll`); `src/main/java/com/employeehub/controller/HealthInsuranceController.java:87-91` (`DELETE /api/v1/healthinsurance/deleteAll`)

**Failure scenario:** Either endpoint calls `repository.deleteAll()` directly with zero checks —
not even a role gate, let alone a confirmation step. Any user with a valid JWT (any role, including
one with no role row at all) can permanently destroy every payroll or health-insurance record
company-wide in a single request. No audit trail, no undo.

**Proposed fix (not yet implemented):** add `CurrentUserContext.requireRole("ADMIN")` as the first
line of both; consider removing `deleteAll` entirely in favor of a scoped/soft delete if nothing
actually depends on a full wipe.

---

## #7 — Universal missing role checks on financial/immigration/customer write & delete endpoints

- **Status:** Open
- **Severity:** Critical
- **Type:** Backend
- **Category:** authz
- **File:** systemic — `CurrentUserContext.requireRole` (added for #1/#2) is used in only
  `UserRoleController.java` and `MigrationController.java`, out of 31 controllers in
  `src/main/java/com/employeehub/controller`. Representative examples, all confirmed to have no
  role check:
  - `InvoiceController.java` — `PUT /invoices/{id}`, `DELETE /invoices/{id}`
  - `BillsController.java:81` — `DELETE /{id}`
  - `CustomerController.java` — `DELETE /customers/{id}`
  - `VendorController.java:90` — `DELETE /vendors/{id}`
  - `ProjectController.java:265` — `DELETE /projects/{id}`
  - `VisaController.java:66`, `PassportController.java:51`, `LCAController.java:46` — `DELETE /{id}`
  - `ImmigrationController.java:69` — `DELETE /notes/{id}`
  - `ImmiIntakeController.java:39` — `DELETE /{intakeId}`
  - `ExpenseController.java:63`, `TimeSheetController.java:113`, `CoiController.java:38` — `DELETE /{id}`

**Failure scenario:** An account provisioned for one function only (e.g. an IMMIGRATION-only role,
or a role-less account mid-onboarding) can call any of these endpoints directly with its own valid
JWT — bypassing whatever page the frontend would normally show — and delete or overwrite another
department's financial, customer, or immigration records. The frontend's role-based page routing
(`roleAccess.js`) is UI-only (see #9); nothing server-side stops a direct HTTP call.

**Proposed fix (not yet implemented):** this is the real scope of the "no authorization layer"
root cause identified in #1/#2 — needs a systematic pass adding `CurrentUserContext.requireRole(...)`
(with the appropriate role set per entity: ACCOUNTING for invoices/bills/expenses, HR for
employees/onboarding, IMMIGRATION+HR+ADMIN for visa/passport/LCA, etc.) across every write/delete
endpoint in every controller listed above, not just the two already fixed.

---

## #8 — Bulk master-data import has no role check

- **Status:** Open
- **Severity:** High
- **Type:** Backend
- **Category:** authz
- **File:** `src/main/java/com/employeehub/controller/MasterDataLoadController.java:46-57` (`POST /api/v1/masterdata/import/{entityType}`)

**Failure scenario:** Any authenticated user can bulk-overwrite Employees, Customers, Projects, or
Assignments via Excel upload. The frontend restricts the Master Data Load page to admins
(`ROUTE_ROLES["/masterdataload"] = []`), but — same gap as everywhere else — that's UI-only.

**Proposed fix (not yet implemented):** `CurrentUserContext.requireRole("ADMIN")` as the first line
of `importData`.

---

## #9 — Frontend self-documents that destructive-action role gating is UI-only

- **Status:** Open
- **Severity:** High
- **Type:** Frontend
- **Category:** authz-design
- **File:** `BeanEMS/src/Notes/rowActions.js:44-47`

**Failure scenario:** The code comment states outright: *"This check is frontend-only (matches
every other role check in this app, since the backend has no concept of roles at all today) — it
hides the direct action from the UI, it does not block the underlying API call itself."*
Non-admins are shown "Request Archive"/"Request Delete" (routed through `ActionRequestController`)
instead of the real action, but that's cosmetic — a non-admin can open devtools, copy their own
JWT, and call the real `DELETE` endpoint directly, completely skipping the "request → admin
approval" workflow the UI implies is mandatory. This is the frontend-side confirmation of #7: the
backend has no enforcement for the role check the UI is built around.

**Proposed fix (not yet implemented):** no frontend change needed once #7 is fixed server-side —
this finding exists to record that the UI's apparent safety net was never backed by the API, so
"the admin-approval workflow works" should not be assumed true until #7 ships.

---

## #10 — Action-request resolution has no role check (audit-trail integrity)

- **Status:** Open
- **Severity:** Medium
- **Type:** Backend
- **Category:** authz
- **File:** `src/main/java/com/employeehub/controller/ActionRequestController.java:49-57` (`PUT /api/v1/actionRequests/{requestId}/resolve`)

**Failure scenario:** Any authenticated user can mark a pending archive/delete request as
`APPROVED` with an arbitrary `resolvedBy` string, forging an "admin approved this" audit entry.
Standalone impact is limited — resolving a request doesn't itself perform the delete (the caller,
`PendingRequests.jsx`, is expected to do that separately, and that underlying delete is only
blocked by #7 being fixed) — but it corrupts the integrity of the one audit mechanism the app has
for non-admin-initiated actions.

**Proposed fix (not yet implemented):** `CurrentUserContext.requireRole("ADMIN")` as the first line
of `resolve`, and derive `resolvedBy` from `CurrentUserContext.email()` server-side rather than
trusting the client-supplied value.

---

## Not included as findings (checked, no exploitable issue)

- **SQL injection:** all `@Query(nativeQuery = true)` usages use positional/named parameter
  binding; no string-concatenated user input found in any native query.
- **Command/path injection:** no `Runtime.exec`/`ProcessBuilder`/raw `new File(...)` usage found.
- **CORS:** `CorsConfig.java` uses an explicit `allowedOrigins` list (env-overridable via
  `cors.allowed-origins`), not a wildcard with credentials.
- **Secrets:** `application.properties` uses env-var placeholders with local-dev-only defaults and
  comments stating they must be overridden in production — not independently verified that
  production's env actually overrides them (ops check, not a confirmed code vulnerability).
- **S3 key path traversal via unsanitized `fileName`:** technically present in
  `DocumentService.presignUpload` (the raw `fileName` is concatenated into the S3 key), but since
  finding #4 lets a caller submit any `s3Key` directly at `confirm` time anyway, this is folded
  into #4 rather than tracked separately — fixing #4 should also sanitize `fileName` at the
  presign step.
- **Frontend secrets:** no AWS keys, Google client secret, or other hardcoded credentials found in
  `BeanEMS/src` (only the expected public Google OAuth Client ID, backend-side, which is correct).
- **XSS:** no `dangerouslySetInnerHTML` usage, no unescaped `innerHTML` assignment, anywhere in
  `BeanEMS/src`.
- **Credentials in URLs:** no JWT or secret ever appended as a query parameter; the axios/fetch
  interceptor (`BeanEMS/src/Utils/apiAuth.js`) attaches the token via the `Authorization` header
  only. The presigned-download flow (`BeanEMS/src/Documents/openDocument.js`) carries only an
  S3-presigned URL, never the app's own JWT.
- **Cross-tenant isolation** (`CompanyIdRangeConfig`, `TenantRoutingDataSource`, `TenantContext`,
  `AuthFilter`, `JwtService`): tenant is a server-signed JWT claim, resolved at login from a
  server-side email/domain→tenant table and verified on every request before `TenantContext` is
  set; the datasource is picked purely from that verified claim. No way found for a request to
  supply or override its own tenant. `CompanyIdRangeConfig` is employee-ID numbering per company
  within a tenant, not a tenant boundary — not exploitable as cross-tenant access.
- **Dependencies:** nothing alarmingly obsolete/abandoned among auth-relevant frontend packages
  (`@react-oauth/google`, `jwt-decode`) — the latter appears unused anywhere in `src`, so not even
  a live attack surface.
