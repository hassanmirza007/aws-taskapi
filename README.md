# taskapi — Spring Boot on AWS, step by step

A small task API built only to be deployed. It has the same moving parts as a real
service: PostgreSQL (Liquibase migrations), Redis cache-aside, Actuator health checks,
graceful shutdown, config from environment variables, and a Docker image.

## Run it locally first

Needs Docker Desktop. From this folder:

```bash
docker compose up --build
```

Then try:

```bash
curl -X POST localhost:8080/api/tasks -H "Content-Type: application/json" \
     -d '{"title":"Deploy to EC2","description":"step 2"}'
curl localhost:8080/api/tasks
curl localhost:8080/api/tasks/1      # logs "Cache miss"
curl localhost:8080/api/tasks/1      # served from Redis, no log line
curl localhost:8080/api/info         # which host answered
curl localhost:8080/actuator/health  # db + redis status
```

**Check you understand before moving on:** stop Redis (`docker compose stop redis`)
and call `/actuator/health` again. It turns DOWN. On AWS, a load balancer that sees
DOWN will kill your task, so this one setting decides whether a cache outage takes
your whole service offline. Interviewers love this question.

## Tests

Three layers, fastest first. The pipeline runs all of them before it builds an image.

| Layer | Class | What it starts | What it proves |
|---|---|---|---|
| Unit | `TaskServiceTest` | Nothing. Mockito mocks the repository | Business rules: 404 on missing task, new tasks start incomplete, delete of a missing task deletes nothing |
| Web slice | `TaskControllerTest` | Only Spring MVC (`@WebMvcTest`) | HTTP contract: 201 on create, 400 on a blank title, 404 on a missing task, JSON shape |
| Integration | `TaskApiIT` | The whole app + real PostgreSQL + real Redis in containers (Testcontainers) | Liquibase migrations run, Hibernate schema validation passes, Redis caches on read and evicts on update |

```bash
mvn test     # unit + slice tests, a few seconds, no Docker needed
mvn verify   # everything, including integration tests (Docker must be running)
```

Why the Dockerfile uses `-DskipTests`: tests run once, in the pipeline, *before*
`docker build`. Testcontainers also can't start containers from inside a `docker build`.

Why `@EnableCaching` lives in `config/CacheConfig` instead of the main class: test
slices load the main class, so anything on it leaks into every slice test. Slices
skip `@Configuration` classes, which keeps `@WebMvcTest` small.

**Check you understand:** break something on purpose, e.g. remove `@CacheEvict` from
`complete()` in `TaskService`. Which test fails, and why does no unit test catch it?

## Configuration (all via environment variables)

| Variable | Default | On AWS it becomes |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | localhost / 5432 / taskdb | RDS endpoint |
| `DB_USER` / `DB_PASSWORD` | taskapp / taskapp | Secrets Manager values |
| `REDIS_HOST` / `REDIS_PORT` | localhost / 6379 | ElastiCache endpoint |
| `REDIS_SSL` | false | true if in-transit encryption is on |
| `REDIS_HEALTH_ENABLED` | true | your decision (see above) |
| `APP_VERSION` | local | git commit SHA from CI/CD |
| `PORT` | 8080 | 8080 (ALB target port) |

## Step 1 — Account setup (do this before launching anything)

1. Create the account at aws.amazon.com/free and choose the **Free plan**.
2. Turn on **MFA for the root user**, then stop using root.
3. Create an admin identity for daily use (IAM Identity Center user, or an IAM user
   with MFA) and sign in with that.
4. **Billing → Budgets:** create a small monthly budget (e.g. $5) with an email alert.
5. Pick one region and stay in it: **Asia Pacific (Mumbai) `ap-south-1`**.
6. Install AWS CLI v2 and confirm it works:
   ```bash
   aws configure sso   # or: aws configure
   aws sts get-caller-identity
   ```

When `get-caller-identity` prints your account and user, step 1 is done.

## The rest of the path

| Step | You build | Interview topics it gives you |
|---|---|---|
| 1b | Push to a GitHub repo; `.github/workflows/ci.yml` runs all tests and builds the image on every push | CI basics, test pyramid, Testcontainers, why a red build blocks a deploy |
| 2a | EC2: plain jar + systemd (one evening) | Security groups, SSH, key pairs, public vs private IP, running Java as a service |
| 2b | Same EC2, now with Docker running this compose stack | Why containers: same image everywhere, no "works on my machine" |
| 3 | Move Postgres to RDS (private subnet) | VPC, subnets, SG-to-SG rules, Secrets Manager |
| 4 | Image to ECR → ECS Fargate behind an ALB | Task definitions, health checks, CloudWatch logs, IAM task vs execution role |
| 5 | Redis to ElastiCache, add SQS | Managed services, caching, async messaging |
| 6 | Extend the same workflow: after tests pass on main → push image to ECR → deploy to ECS | CI/CD, image tagging by commit SHA, OIDC roles (no access keys), rolling deploys |
| 7 | Break-and-fix drills, including a failed deploy and a rollback | Debugging under pressure |
| 8 | Jenkins: the same pipeline as a Jenkinsfile | Declarative pipelines, stages, credentials, agents |
| 9 | Kubernetes: Deployment, Service, probes, ConfigMap/Secret (kind locally, EKS optional if credits allow) | Pods vs deployments, liveness vs readiness, rolling updates, how K8s compares to ECS |
| 10 | Tear everything down | Cost awareness |

When something fails, paste the exact error and what you ran. That's the learning.
