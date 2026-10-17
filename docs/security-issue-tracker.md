# Security Issue Tracker

Findings from a backend-only security audit (2026-10-17), covering authentication/authorization,
injection, secrets, file-upload/document handling, CORS, and mass assignment across
`src/main/java/com/employeehub`. Root cause behind nearly every open/fixed item: the app has
authentication (a valid JWT is required) but, before this tracker started, no authorization layer
at all — no endpoint checked the caller's role.

| # | Status | Severity | Category | Summary |
|---|--------|----------|----------|---------|
| 1 | ✅ Fixed | Critical | authz | Any authenticated user could self-assign the `ADMIN` role |
| 2 | ✅ Fixed | Critical | authz | Any authenticated user could merge/overwrite another employee's entire record via the migration endpoint |
| 3 | 🔲 Open | Critical | idor | Any authenticated user can read/delete any employee's S3 documents by guessing the numeric id |
| 4 | 🔲 Open | High | mass-assignment | Document confirm endpoint saves the full client-supplied JSON, including a client-settable `id` |
| 5 | 🔲 Open | High | authz | `getAllEmployees` returns every employee's SSN/DOB/salary to any authenticated user regardless of role |

---

## #1 — Unrestricted role self-assignment to ADMIN

- **Status:** Fixed (2026-10-17)
- **Severity:** Critical
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
