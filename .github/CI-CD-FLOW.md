# CI/CD Flow (Xrag)

This document describes the current CI/CD pipeline and the expected artifacts and summaries.

## Triggers
- push to `master` or `main`
- pull_request to `master` or `main`
- manual `workflow_dispatch`

## Jobs

### 1) code-checks
Runs on every trigger. This job validates code and publishes reports.

Steps:
1. Checkout
2. Setup JDK 17
3. Backend tests (Surefire)
4. Test summary in `GITHUB_STEP_SUMMARY`
5. Upload Surefire reports artifact
6. Checkstyle
7. Upload Checkstyle report artifact
8. Spotless check
9. Spotless summary in `GITHUB_STEP_SUMMARY`
10. Dependency-Check (only if `NVD_API_KEY` set)
11. Dependency-Check summary + report artifact
12. JaCoCo coverage summary + report artifact
13. Setup Node 20
14. Frontend build (npm ci / lint / build)
15. Frontend summary in `GITHUB_STEP_SUMMARY`

Artifacts:
- `surefire-reports`
- `checkstyle-report`
- `dependency-check-report` (only when `NVD_API_KEY` is set)
- `jacoco-report`

### 2) build-and-deploy (backend)
Runs only on non-PR events (push and workflow_dispatch) after `code-checks` passes.

Steps:
1. Checkout
2. Setup JDK 17
3. Maven package (skip tests)
4. Build and push backend Docker image
5. Deploy via SSH (key preferred, password fallback)
6. Cleanup old image + `docker image prune -f`

Outputs:
- New backend image pushed to Docker Hub
- Backend container restarted on server

### 3) frontend-build-and-deploy
Runs only on non-PR events after backend deploy completes.

Steps:
1. Checkout
2. Build and push frontend Docker image
3. Deploy via SSH (key preferred, password fallback)
4. Auto port handling (80 preferred, fallback 8081/8082/...)
5. Optional nginx proxy config if port 80 is occupied
6. Cleanup old image + `docker image prune -f`
7. Smoke test via SSH (if possible)

Outputs:
- New frontend image pushed to Docker Hub
- Frontend container restarted on server

## Secrets
Required:
- `DOCKERHUB_USERNAME`
- `DOCKERHUB_PASSWORD`
- `SSH_PRIVATE_KEY` (preferred for deploy)

Optional:
- `SERVER_PASSWORD` (fallback if SSH key is not set)
- `NVD_API_KEY` (enables Dependency-Check in `code-checks`)

## How To Verify
1. GitHub Actions latest run is `success`.
2. Frontend: `http://111.229.72.224/` returns 200.
3. Backend Swagger: `http://111.229.72.224:8080/swagger-ui/index.html` returns 200.

## Notes
- If your org enforces PR-only changes, pushes may show a rule bypass warning in the log. This is expected for admin bypass.
- Dependency-Check runs only when `NVD_API_KEY` is set as a repository secret.
