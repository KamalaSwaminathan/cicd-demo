# Design and Implementation of an End-to-End Cloud-Based CI/CD Pipeline

**Name:** Kamala Swaminathan  **Reg. No.:** 23011102037  **Section:** IoT A
**Course:** DevOps — CIA 2  **Repository:** https://github.com/KamalaSwaminathan/cicd-demo

---

## 1. Problem Definition

The team currently builds, tests, containerises and deploys its Java web application by hand. Each release depends on one developer's machine and memory of the steps, so releases are slow, steps get skipped (tests are the first casualty under deadline pressure), and a failure cannot be reproduced because nobody knows exactly what was run.

**Goal:** a pipeline in which a `git push` is the only manual action. Everything after it — compile, test, package, build image, publish, deploy, verify — runs automatically, identically every time, and stops at the first failure so broken code never reaches users.

**Success criteria**
1. Push to `main` triggers the pipeline with no human involvement.
2. A failing test blocks the image build and deployment.
3. Every deployed image is traceable to the exact Git commit that produced it.
4. Updates roll out with zero downtime.
5. The pipeline itself verifies that the new version is live.

## 2. Architecture

*[Insert architecture diagram — screenshot the Mermaid diagram rendered in the repository README]*

**Flow.** Developer pushes to GitHub → GitHub Actions runs three dependent jobs → (1) *build-test*: Maven compiles, runs JUnit tests, packages `app.jar`; (2) *docker*: builds an image from that JAR, tags it with the 7-character commit SHA and `latest`, pushes to Azure Container Registry (ACR); (3) *deploy*: Ansible creates the registry pull secret, applies the Kubernetes manifests with the new image tag, waits for the rollout, and smoke-tests the public endpoint. Users reach the app through an Azure Load Balancer in front of two pod replicas on Azure Kubernetes Service (AKS).

## 3. Tools Selected and Justification

| Concern | Tool | Why this one |
|---|---|---|
| Source control | Git + GitHub | Industry standard; push events drive the pipeline; full history for traceability |
| CI/CD engine | GitHub Actions | Hosted runners — no Jenkins server to provision, secure and patch; pipeline-as-code in the same repo; free for public repositories |
| Build & test | Maven 3 + JUnit 5 | Declarative `pom.xml`; one command (`mvn verify`) compiles, tests and packages; dependency cache cuts build time |
| Application | Spring Boot 3 (Java 17) | Embedded server → single runnable JAR; Actuator gives health endpoints used by Kubernetes probes |
| Containers | Docker | Immutable artifact identical across laptop, CI and cluster |
| Registry | Azure Container Registry (Basic) | Same cloud as the cluster (low latency pulls), cheapest tier sufficient |
| Orchestration | AKS (free tier, 1 × Standard_B2s_v2) | Managed control plane at no cost; rolling updates, self-healing, load balancing |
| Deployment automation | Ansible (`kubernetes.core`) | Idempotent, declarative; built-in wait and HTTP verification; same playbook runs from CI or a laptop |
| Cloud | Microsoft Azure | Azure for Students provides credit without a credit card |

**Alternatives considered.** Jenkins was rejected because it needs a dedicated always-on VM (extra cost and maintenance) for a single small project. A multi-stage Dockerfile that runs Maven inside Docker was rejected because it rebuilds and re-tests code that CI has already built and tested (see §8).

## 4. CI/CD Pipeline Stages

File: `.github/workflows/ci-cd.yml`. Triggers: push to `main` (full pipeline), pull request (build and test only — never deploys unreviewed code), manual dispatch.

| # | Stage | Implementation | Purpose |
|---|---|---|---|
| 1 | Checkout | `actions/checkout@v4` | Fetch the exact commit that triggered the run |
| 2 | Compile & package | `mvn -B clean verify` (JDK 17, Maven cache) | Fail fast on compile errors; produce `target/app.jar` |
| 3 | Automated tests | Surefire runs 7 JUnit tests inside `verify`; `dorny/test-reporter` publishes results | Any failure stops the pipeline before an image exists |
| 4 | Build & tag image | `docker/build-push-action`, tags `:<sha>` and `:latest` | Immutable, traceable artifact |
| 5 | Push to registry | Login to ACR with repository secrets, push both tags | Make the image available to the cluster |
| 6 | Deploy / update | `ansible-playbook ansible/deploy.yml -e image=…:<sha>` | Apply namespace, pull secret, Deployment, Service |
| 7 | Verify | Ansible waits for `Available`, waits until all pods run the new image, calls `/actuator/health` and checks `/api/info` returns the new SHA; `kubectl get` output written to the run summary | Prove the new version is actually serving traffic |

**Job dependencies:** `build-test → docker → deploy`. The tested JAR is passed between jobs as an artifact, so the deployed binary is byte-for-byte the one that passed the tests. A `concurrency` group cancels an older run if a newer commit is pushed, preventing two deployments racing.

*[Screenshot: successful pipeline run — job graph, all green]*
*[Screenshot: JUnit Tests check — 7 passed]*

## 5. Cloud Resources and Deployment Approach

| Resource | Name | Configuration |
|---|---|---|
| Resource group | `cicd-demo-rg` | East Asia (Central India blocked by Azure for Students region policy); deleting it removes everything |
| Container registry | `cicddemo<nnnn>` | Basic SKU |
| Kubernetes cluster | `cicd-demo-aks` | Free tier control plane, 1 node, Standard_B2s_v2 (2 vCPU, 8 GB) |
| Load balancer + public IP | auto-created | By the `LoadBalancer` Service |

Provisioning is scripted in `infra/setup-azure.sh` (Azure CLI, run once in Cloud Shell) so the environment is reproducible.

**Kubernetes manifests (`k8s/`).**
- *Deployment* — 2 replicas; `RollingUpdate` with `maxUnavailable: 0, maxSurge: 1` so capacity never drops during an update; readiness probe (`/actuator/health/readiness`) keeps traffic away from pods that are still starting; liveness probe restarts hung containers; CPU/memory requests and limits so one pod cannot starve the node.
- *Service* — type `LoadBalancer`, port 80 → container 8080.
- The image field holds a placeholder that Ansible replaces with the SHA-tagged image; the commit SHA is also injected as `APP_VERSION`, which the web page displays so the running version is visible in the browser.

**Ansible (`ansible/`).** The inventory contains only `localhost`, because Ansible manages the cluster through the Kubernetes API rather than over SSH. The playbook is idempotent: re-running it with the same tag reports no changes.

*[Screenshot: Azure resource group showing AKS and ACR]*
*[Screenshot: ACR repository `cicd-demo` with SHA tags]*
*[Screenshot: `kubectl get nodes` and `kubectl get deploy,pods,svc -n cicd-demo -o wide` — pods Running, deployment 2/2]*
*[Screenshot: application in the browser at http://EXTERNAL-IP]*

## 6. Testing and Results

| Test class | Type | Tests | What it checks |
|---|---|---|---|
| `GreetingServiceTest` | Unit (no Spring) | 4 | Greeting with name, whitespace trimming, null/blank default, addition |
| `HomeControllerTest` | Web-layer integration (MockMvc) | 3 | `/api/info` returns the message, `/api/greet` uses the parameter, `/actuator/health` is `UP` |
| Ansible smoke tests | Post-deployment | 2 | Live health endpoint `UP`; live `/api/info` reports the deployed commit SHA |

**Result:** 7/7 unit/integration tests passed; both post-deployment checks passed. *[Fill in run time from the Actions page, e.g. "total pipeline time 4 m 12 s".]*

**Negative test (recommended for the demo).** Change an expected value in a test, push, and show the pipeline stopping at *Build & Test* with the Docker and Deploy jobs skipped — proof that broken code cannot reach production.

### Change and redeployment demonstration

1. Edited `HomeController.MESSAGE` from "v1" to "v2" and pushed commit `<sha>`. *[Screenshot: commit on GitHub]*
2. The push triggered run #N automatically. *[Screenshot: Actions run list]*
3. New image `cicd-demo:<sha>` appeared in ACR. *[Screenshot]*
4. `kubectl get pods -w` showed new pods becoming Ready before old pods terminated. *[Screenshot]*
5. The browser shows the v2 message and the new SHA. *[Screenshot]*

## 7. Challenges Encountered and Solutions

*(Edit to match what actually happened to you — examiners value real issues.)*

| Challenge | Solution |
|---|---|
| Docker job could not find the JAR because each GitHub Actions job runs on a fresh machine | Upload `app.jar` as an artifact in *build-test* and download it in *docker* |
| Kubernetes could not pull from the private registry (`ImagePullBackOff`) | Ansible creates a `dockerconfigjson` pull secret referenced by the Deployment |
| An all-digit commit SHA was parsed as a number, which Kubernetes rejects for env values | Quote the value when substituting the placeholder so it stays a string |
| The pipeline reported success while old pods were still serving | Added verification that every pod runs the new image and that `/api/info` returns the new SHA |
| Azure for Students blocked Central India and the Standard_B2s / B2ms VM sizes (`RequestDisallowedByAzure`, `BadRequest`) | Listed allowed regions with `az policy assignment list`, moved to East Asia, and picked `Standard_B2s_v2` from the allowed-sizes list in the error |
| Cloud credit draining while idle | `infra/cost-control.sh stop` between sessions; single small node; free control-plane tier |

## 8. Waste Eliminated (Lean view of the pipeline)

| Manual-process waste | How the pipeline removes it |
|---|---|
| Waiting — developer idle during build, test, deploy | Runs unattended on hosted runners |
| Defects — skipped tests, wrong image deployed | Tests are mandatory gates; image tag = commit SHA |
| Re-work — "works on my machine" debugging | One immutable Docker image for every environment |
| Over-processing — building twice | JAR built once and reused; Docker does not rebuild it; dependency cache |
| Inventory — idle infrastructure | Hosted CI instead of an always-on Jenkins VM; cluster stopped when unused |
| Motion — copying commands, SSH-ing into servers | Declarative manifests + Ansible; one `git push` |

## 9. Security Considerations

- **Secrets** (registry password, kubeconfig) are stored as encrypted GitHub secrets, masked in logs; the Ansible task creating the pull secret uses `no_log: true`; nothing secret is committed (`.gitignore` excludes kubeconfig files).
- **Least exposure:** pull requests run build/test only — the Docker and deploy jobs, which use the credentials, are skipped; deployment is gated to `main` via the `production` environment, which can be configured to require manual approval.
- **Container hardening:** JRE-only Alpine base (smaller attack surface than a full JDK), runs as a non-root user, resource limits prevent noisy-neighbour or runaway memory.
- **Traceability:** every running pod's image tag maps to an auditable Git commit.

## 10. Limitations and Future Work

- ACR admin credentials and a static kubeconfig are simple but long-lived. Production should use **OIDC federated identity** (GitHub ↔ Azure AD) for short-lived tokens and `az aks update --attach-acr` (managed identity) instead of a pull secret.
- Plain HTTP on a public IP — add an Ingress controller with TLS (cert-manager + Let's Encrypt).
- Single node = no node-level high availability.
- No image vulnerability scanning — add Trivy as a pipeline stage that fails on critical CVEs.
- No staging environment — add a staging namespace with automatic deploy and a manual approval before production.
- No monitoring — Azure Monitor / Prometheus + Grafana for metrics and alerts.

## 11. Conclusion

The pipeline turns a slow, error-prone manual release into a single `git push`. Each change is compiled, tested, containerised, published and rolled out to AKS with zero downtime, and the pipeline itself proves the new version is live. The change-and-redeploy demonstration confirms a code edit reaches the browser automatically within minutes.
