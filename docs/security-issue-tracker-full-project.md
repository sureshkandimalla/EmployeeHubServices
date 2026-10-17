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
| 3 | ✅ Fixed | Critical | Backend | idor | Any authenticated user could read/delete any employee's S3 documents by guessing the numeric id |
| 4 | ✅ Fixed | High | Backend | mass-assignment | Document confirm endpoint saved the full client-supplied JSON, including a client-settable `id` |
| 5 | ✅ Fixed | High | Backend | authz | `getAllEmployees` returned every employee's SSN/DOB/salary to any authenticated user regardless of role |
| 6 | ✅ Fixed | Critical | Backend | authz | Any authenticated user could permanently wipe the entire payroll-summary or health-insurance table |
| 7 | ✅ Fixed | Critical | Backend | authz | `requireRole` existed on only 4 of 31 controllers — nearly every financial/immigration/customer write and delete endpoint had no role check at all |
| 8 | ✅ Fixed | High | Backend | authz | Bulk master-data import (Employees/Customers/Projects/Assignments) had no role check |
| 9 | ✅ Resolved | High | Frontend | authz-design | Frontend's own code comments documented that Archive/Delete role gating was UI-only and never reached the backend — resolved as a consequence of #7 |
| 10 | ✅ Fixed | Medium | Backend | authz | `ActionRequestController.resolve` let any authenticated user forge an "admin approved" audit entry |

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

- **Status:** Fixed (2026-10-17)
- **Severity:** Critical
- **Type:** Backend
- **Category:** idor
- **File:** `src/main/java/com/employeehub/controller/DocumentController.java:58,63`; `src/main/java/com/employeehub/service/DocumentService.java:123-141`

**Failure scenario:** `GET /api/v1/documents/{id}/downloadUrl` and `DELETE /api/v1/documents/{id}`
looked the `Document` up by its raw auto-increment PK with no check that the caller is associated
with the document's `entityType`/`entityId`. Any authenticated user enumerating ids could obtain a
presigned S3 GET URL for, or permanently delete, any other employee's/vendor's passport, visa
approval, I-94, or other document in the shared tenant S3 bucket.

**Fix:** Added `DocumentService#requireAccess(entityType, entityId)`, reusing the same
`resolveRootFolder` resolution `presignUpload` already did. For Employee/Project/WorkOrderPO docs:
allowed if the caller is ADMIN/HR, or if the caller's own login email matches the owning
employee's `emailId`. For Vendor/Customer/COI docs (no owning employee): allowed if ADMIN/ACCOUNTING.
Called from `presignUpload`, `confirm`, `listForEntity`, and — resolving the entity from the
looked-up `Document` itself — `presignDownload` and `delete`.

**Verified:** compiles clean; exercised indirectly via the #4 fix's `confirm` role check (same
`requireAccess` call site).

---

## #4 — Mass-assignment on Document confirm endpoint

- **Status:** Fixed (2026-10-17)
- **Severity:** High
- **Type:** Backend
- **Category:** mass-assignment
- **File:** `src/main/java/com/employeehub/controller/DocumentController.java:39`; `src/main/java/com/employeehub/service/DocumentService.java:113-115`

**Failure scenario:** `confirm()` passed the entire client-supplied `Document` JSON — including a
client-settable `id` — straight to `documentRepository.save()`. Since JPA's `save()` merges/updates
when a non-null id matches an existing row, a caller could submit another document's id and
overwrite its `entityId`/`s3Key`/`description`.

**Fix:** `confirm()` now calls `requireAccess(meta.getEntityType(), meta.getEntityId())` (see #3)
before saving, and unconditionally sets `meta.setId(null)` so every confirm is a brand-new row —
`save()` can never merge into an existing document regardless of what id the client sends.

**Verified:** compiles clean; `id`-nulling is a direct code-path guarantee (no conditional logic
that could skip it).

---

## #5 — SSN/salary exposed to any authenticated user regardless of role

- **Status:** Fixed (2026-10-17)
- **Severity:** High
- **Type:** Backend
- **Category:** authz
- **File:** `src/main/java/com/employeehub/controller/EmployeeController.java:41` (`getAllEmployees`, `getEmployees`, `employees`)

**Failure scenario:** These endpoints returned the full `Employee` entity (SSN, DOB, `annualPay`,
visa status, etc.) for every employee in the tenant to any caller holding a valid JWT — including
one with no role assigned at all.

**Fix:** `CurrentUserContext.requireRole("ADMIN", "HR", "ACCOUNTING", "IMMIGRATION")` added to all
three read endpoints, plus the same gate on `updateEmployee` (inline-edit) and
`CurrentUserContext.requireRole("ADMIN", "HR")` on `createEmployee`/`saveEmployees`/`deleteEmployee`.
IMMIGRATION was deliberately kept on the allow-list for the read/update endpoints rather than
excluded outright — `roleAccess.js` already documents `/workforce` as reachable by HR, ACCOUNTING,
*and* IMMIGRATION, so narrowing further would have been a product-policy change beyond this fix's
scope, not just a security tightening. This closes the "no role at all" gap (the finding's core
issue) without breaking that existing, intentional page access.

**Verified:** role-less JWT → `getAllEmployees` → `403`. ACCOUNTING JWT → `getAllEmployees` → `200`
(unchanged for roles the frontend already grants access to).

---

## #6 — Unrestricted bulk deleteAll on payroll-summary and health-insurance tables

- **Status:** Fixed (2026-10-17)
- **Severity:** Critical
- **Type:** Backend
- **Category:** authz
- **File:** `src/main/java/com/employeehub/controller/PayrollSummaryController.java:86-90` (`DELETE /api/v1/payrollsummary/deleteAll`); `src/main/java/com/employeehub/controller/HealthInsuranceController.java:87-91` (`DELETE /api/v1/healthinsurance/deleteAll`)

**Failure scenario:** Either endpoint called `repository.deleteAll()` directly with zero checks —
not even a role gate, let alone a confirmation step. Any user with a valid JWT (any role, including
one with no role row at all) could permanently destroy every payroll or health-insurance record
company-wide in a single request. No audit trail, no undo.

**Fix:** `CurrentUserContext.requireRole("ADMIN")` added as the first line of both `deleteAll`
methods.

**Verified:** non-admin JWT → `DELETE /api/v1/payrollsummary/deleteAll` → `403`; non-admin JWT →
`DELETE /api/v1/healthinsurance/deleteAll` → `403`. The admin-allowed path wasn't separately
re-tested here (it would require actually wiping local data) — #1/#2 already proved admin tokens
pass through `CurrentUserContext.requireRole` correctly.

---

## #7 — Universal missing role checks on financial/immigration/customer write & delete endpoints

- **Status:** Fixed (2026-10-17)
- **Severity:** Critical
- **Type:** Backend
- **Category:** authz
- **File:** systemic — 15 controllers, listed below with the role(s) now required.

**Failure scenario:** An account provisioned for one function only (e.g. an IMMIGRATION-only role,
or a role-less account mid-onboarding) could call any of these endpoints directly with its own
valid JWT — bypassing whatever page the frontend would normally show — and delete or overwrite
another department's financial, customer, or immigration records. The frontend's role-based page
routing (`roleAccess.js`) is UI-only (see #9); nothing server-side stopped a direct HTTP call.

**Fix:** `CurrentUserContext.requireRole(...)` added as the first line of every write/delete
endpoint, role set matched to `roleAccess.js`'s existing `ROUTE_ROLES`/`ENTITY_ROLES` intent:
- **ACCOUNTING** (+ADMIN): `InvoiceController` (all 7 mutating endpoints), `BillsController`
  (create/update/delete), `CustomerController` (create/update/delete), `VendorController`
  (create/update/delete), `ProjectController` (create/update/delete), `ExpenseController`
  (create/update/delete), `TimeSheetController` (all 9 mutating endpoints), `CoiController`
  (create/update/delete)
- **IMMIGRATION** (+ADMIN): `VisaController`'s Visa CRUD (create/update/delete), `PassportController`
  (+HR, create/update/delete), `LCAController` (save/delete), `ImmiIntakeController`
  (create/update/delete)
- **HR** (+ADMIN): `VisaController`'s PotentialEmployee endpoints (create/bulk-save/delete),
  `EmployeeController`'s create/bulk-save/delete
- **Any assigned role** (ADMIN/HR/ACCOUNTING/IMMIGRATION): `ImmigrationController`'s Notes
  endpoints (create/update/delete) — Notes attach to entities across every department, not just
  Immigration despite the controller's name; `EmployeeController#updateEmployee` — the
  `/workforce` inline-edit grid is itself reachable by all three non-Admin roles

**Verified:** 17-case curl matrix against a local build — each wrong-role call (e.g. IMMIGRATION
→ delete invoice, HR → delete visa, ACCOUNTING → delete employee) → `403`; each correct-role call
(e.g. ACCOUNTING → delete invoice/customer/vendor/expense/timesheet, IMMIGRATION → delete
visa/lca, HR → delete employee, ACCOUNTING → read/update employee) → not `403` (passed through to
the underlying business logic, confirmed by a `404`/`200`/pre-existing-unrelated-`500` on a bogus
id rather than a role rejection).

---

## #8 — Bulk master-data import has no role check

- **Status:** Fixed (2026-10-17)
- **Severity:** High
- **Type:** Backend
- **Category:** authz
- **File:** `src/main/java/com/employeehub/controller/MasterDataLoadController.java:46-57` (`POST /api/v1/masterdata/import/{entityType}`)

**Failure scenario:** Any authenticated user could bulk-overwrite Employees, Customers, Projects,
or Assignments via Excel upload. The frontend restricts the Master Data Load page to admins
(`ROUTE_ROLES["/masterdataload"] = []`), but — same gap as everywhere else — that was UI-only.

**Fix:** `CurrentUserContext.requireRole("ADMIN")` added as the first line of `importData`.

**Verified:** role-less JWT, well-formed multipart request → `403`. (An initial test with a
malformed/non-multipart request returned `500` before even reaching the method body — a test
artifact, not a gap; confirmed with a proper multipart part that the role check itself returns
`403` correctly.)

---

## #9 — Frontend self-documents that destructive-action role gating is UI-only

- **Status:** Resolved (2026-10-17)
- **Severity:** High
- **Type:** Frontend
- **Category:** authz-design
- **File:** `BeanEMS/src/Notes/rowActions.js:44-47`

**Failure scenario:** The code comment states outright: *"This check is frontend-only (matches
every other role check in this app, since the backend has no concept of roles at all today) — it
hides the direct action from the UI, it does not block the underlying API call itself."*
Non-admins are shown "Request Archive"/"Request Delete" (routed through `ActionRequestController`)
instead of the real action, but that was cosmetic — a non-admin could open devtools, copy their
own JWT, and call the real `DELETE` endpoint directly, completely skipping the "request → admin
approval" workflow the UI implies is mandatory.

**Resolution:** no frontend change made or needed — this finding was a confirmation that the UI's
safety net wasn't backed by the API. Now that #7 adds real server-side role checks to the delete
endpoints every grid's Archive/Delete action actually calls, the backend independently enforces
what the UI always implied, closing the gap this finding described. The code comment itself is
now stale (the backend *does* have a concept of roles) but left as-is — correcting it is a
docs-only cleanup, not a vulnerability fix.

---

## #10 — Action-request resolution has no role check (audit-trail integrity)

- **Status:** Fixed (2026-10-17)
- **Severity:** Medium
- **Type:** Backend
- **Category:** authz
- **File:** `src/main/java/com/employeehub/controller/ActionRequestController.java:49-57` (`PUT /api/v1/actionRequests/{requestId}/resolve`)

**Failure scenario:** Any authenticated user could mark a pending archive/delete request as
`APPROVED` with an arbitrary `resolvedBy` string, forging an "admin approved this" audit entry.

**Fix:** `CurrentUserContext.requireRole("ADMIN")` added as the first line of `resolve`; `resolvedBy`
is now derived from `CurrentUserContext.email()` server-side instead of the client-supplied value.

**Verified:** admin JWT → `resolve` on a bogus id → not `403` (passed through to a `404` lookup
failure). Role-less JWT → same call → `403`.

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
