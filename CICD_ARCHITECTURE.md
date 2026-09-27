# CI/CD Architecture and Scaling Plan

Status: accepted architectural direction; implementation has not started  
Updated: 2026-09-27

## 1. Purpose and document boundary

This document owns the CI/CD architecture for the API automation framework. It records the accepted Jenkins
topology, GitHub integration, test-distribution strategy, scaling stages, and the temporary AWS learning
environment. Framework design remains in `API_AUTOMATION_ARCHITECTURE.md`.

The first implementation will run locally, but the design deliberately follows patterns that can grow into a
large test platform. Local convenience must not force the later architecture into one permanent machine, one
large executor pool, or statically assigned test suites.

## 2. Locked decisions

- Use Jenkins Pipeline as code with a repository-owned Declarative `Jenkinsfile`.
- Use a Multibranch Pipeline so Jenkins can discover branches and pull requests.
- Access the public GitHub repository through a read-only credential stored in Jenkins Credentials.
- Run the Jenkins controller in a container and set its built-in executor count to zero.
- Begin with three containerized Jenkins agents, represented as three Jenkins nodes.
- Give each agent node one executor.
- Keep the parent Pipeline focused on orchestration; it must not hold a worker executor merely while waiting.
- Do not permanently assign packages or test classes to fixed child jobs.
- Collect individual test durations and calculate balanced shards dynamically.
- Use one generic shard-worker definition for every shard invocation.
- Treat TestNG thread parallelism and Jenkins shard parallelism as separate, explicitly bounded controls.
- Move the stable local installation to the ASUS home server before adding automatic GitHub webhooks.
- Introduce dynamic Docker agents on the ASUS server before moving to Kubernetes.
- Use Terraform for the AWS infrastructure learning stage.
- Use Amazon EKS and Karpenter-managed Spot capacity for ephemeral test agents in the AWS stage.
- Keep the AWS environment for only two or three runs over approximately two or three days, then destroy it.
- Preserve the ASUS-based home lab as the long-lived demonstration environment after the AWS lab is destroyed.
- Extend the home lab with one constrained Raspberry Pi agent and several isolated agent containers on the HP
  laptop and ASUS server, subject to measured CPU and memory limits.
- Use capability labels so heterogeneous machines do not receive work they cannot run reliably.
- Make AWS worker capacity explicitly opt-in from Jenkins. Framework Java code must not provision infrastructure.
- Preserve all Stage 5 Terraform, Helm/Kubernetes, image, validation, and runbook source after AWS resources are
  destroyed so the disposable environment can be recreated for a later demonstration.

## 3. Jenkins execution model

The Jenkins controller, nodes, agents, executors, and TestNG threads have different responsibilities.

| Concept | Responsibility |
| --- | --- |
| Controller | Hosts the Jenkins UI, credentials, job definitions, queue, scheduling, and build metadata. |
| Node | A logical execution environment registered with Jenkins. It may represent a computer, VM, or container. |
| Agent | The process that connects a node to the controller and performs assigned work. |
| Executor | One Jenkins scheduling slot on a node. One running child build or agent-backed Pipeline branch normally uses one executor. |
| TestNG thread | Concurrent test work inside one Gradle/TestNG process. It does not request another Jenkins executor. |

For this plan, one agent container represents one node and exposes one executor:

```text
Physical host
├── Jenkins controller container (zero build executors)
├── Agent container 1 = node 1 = one executor
├── Agent container 2 = node 2 = one executor
└── Agent container 3 = node 3 = one executor
```

Several executors on one node would share CPU, memory, disk, network, caches, and failure impact. Separate
one-executor agent containers provide clearer resource boundaries and follow the model later used by ephemeral
Kubernetes agents. Multiple containers on one physical host still share the host's finite compute capacity.

## 4. Repository and GitHub integration

The repository is public, but Jenkins will still use a fine-grained, read-only GitHub credential. This supports
authenticated branch and pull-request discovery and avoids relying on anonymous API limits. The credential will
be stored only in Jenkins Credentials and referenced by its credential ID.

Recommended minimum permissions are:

- Repository contents: read
- Metadata: read
- Pull requests: read when pull-request discovery is enabled

The Pipeline must always identify and check out one exact Git commit. Parent and shard workers must never obtain
different revisions because a branch advanced while the build was running.

The initial local installation will use manual execution. GitHub cannot normally call a controller reachable
only on a private home network. A webhook will be added after Jenkins moves to the ASUS server and a secured HTTPS
endpoint is available. The complete Jenkins UI must not be exposed directly without authentication, TLS, and an
appropriate reverse-proxy or tunnel design.

## 5. Test-distribution model

### 5.1 Fan-out and fan-in

The accepted execution pattern is a parent fan-out followed by fan-in:

```text
Parent Pipeline
├── Generic shard worker: shard 1
├── Generic shard worker: shard 2
├── Generic shard worker: shard 3
└── ...
        ↓
Aggregate results, calculate parent status, and update timing history
```

Fan-out becomes test sharding when every worker receives a mutually exclusive portion of the same eligible test
set. There will not be twelve separately maintained job definitions. One generic worker will be invoked with
different parameters, conceptually including:

```text
GIT_COMMIT
ENVIRONMENT
PARENT_BUILD_ID
SHARD_INDEX
SHARD_TOTAL
SHARD_MANIFEST
```

The parent must propagate cancellation to workers, recognize workers that never started, distinguish aborted
and failed workers, and produce one trustworthy combined result.

### 5.2 Duration-balanced dynamic sharding

Static package or class assignment is rejected because it creates long-tail builds: a child that takes 35 minutes
sets the total duration even when every other child finishes in five or six minutes.

The dynamic planner will:

1. Discover all eligible tests for the selected suite and environment.
2. Load historical durations from persistent storage.
3. Select a shard count within the current execution-capacity limit.
4. Sort test units from slowest to fastest.
5. Assign each unit to the currently shortest predicted shard.
6. Produce immutable shard manifests for the run.
7. Fan out generic workers.
8. Aggregate results and update duration history.

This longest-processing-time-first strategy balances predicted shard duration instead of test count. New tests
without history will receive a conservative estimate derived from the suite or class median. Timing history will
use multiple recent runs so one transient delay does not permanently distort future plans.

The initial safe sharding unit is a test class. Method-level sharding may be enabled for classes whose methods are
independent. Invocation-level sharding for DataProviders is deferred until stable invocation identities and
fixture boundaries are designed.

No sharding strategy can reduce execution below the duration of the longest indivisible test. A single 35-minute
test must be decomposed or parallelized internally before a five-minute total is possible.

### 5.3 Parallelism controls

Jenkins and TestNG parallelism multiply:

```text
Approximate concurrent test flows
    = concurrently executing shards
    × TestNG threads per shard
```

Three shard workers with four TestNG threads may create approximately twelve concurrent test flows while using
only three Jenkins executors. The system under test, database, rate limits, test accounts, and cleanup mechanisms
must support that concurrency.

Dynamic sharding will begin with one TestNG thread per shard. Additional TestNG threads will be introduced only
after shard isolation and service capacity have been measured.

## 6. Delivery stages

### Stage 1 — Local Jenkins foundation

Run the first topology on the development computer:

```text
Development computer
├── Jenkins controller container
├── Three fixed agent containers, one executor each
└── Services under test running on the host
```

Stage 1 delivers:

- Multibranch Pipeline connected to GitHub with a Jenkins-managed credential
- Manual build execution
- Exact commit checkout
- Jenkins environment choice, initially useful for QA
- Pre-test service health checks
- Whole-suite Gradle/TestNG execution on one available agent
- Jenkins console capture of framework logs
- Test result and report publication
- Per-test duration collection

An agent container cannot reach a host service through the container's own `localhost`. Local builds must use a
Docker host-gateway address such as `host.docker.internal`, with services bound so the container can reach them.
Service base URLs will be supplied through the framework's supported runtime overrides rather than committed as
machine-specific catalog values.

The other two agents can serve builds from other branches during this stage, but they do not accelerate one
whole-suite Gradle invocation.

### Stage 2 — Persistent heterogeneous home lab and webhook

Move the controller and durable state to the ASUS home server, then add agents from the available physical
machines without simultaneously changing the test-distribution algorithm:

```text
Home LAN
├── ASUS home server (12 GB RAM, i3)
│   ├── Jenkins controller container, zero executors
│   ├── Persistent controller and timing-history storage
│   ├── One or more one-executor agent containers after capacity measurement
│   └── Containerized or otherwise stably reachable services under test
├── HP laptop (32 GB RAM, i5)
│   └── Several isolated one-executor agent containers
└── Raspberry Pi 3B+ (1 GB RAM)
    └── One constrained agent node for explicitly eligible work
```

A physical device is a host, while every Jenkins agent process or container is represented as a Jenkins node.
Running several one-executor agent containers on the HP or ASUS host provides workspace and process isolation,
but those nodes still share the host's CPU, memory, disk, and network. Their counts will therefore be configured
and measured rather than inferred from RAM alone.

The Raspberry Pi is intentionally not part of the unrestricted general-purpose pool. Jenkins remoting, Gradle,
and a test JVM can put significant pressure on a 1 GB device. It receives labels such as `arm64`, `low-memory`,
and `smoke`, and initially runs only health checks or small suites proven to fit. Full-suite eligibility requires
measurement with bounded JVM memory; its presence must not make ordinary builds less reliable.

Stage 2 adds:

- Persistent controller storage and backup
- Secure HTTPS access
- Secure GitHub webhook endpoint
- Automatic branch and pull-request builds
- Stable container networking and service names
- Build and artifact retention policies
- Several timing-history runs for the dynamic planner
- Persistent, labelled agents across the ASUS, HP, and Raspberry Pi hosts
- Reproducible Compose, agent-image, plugin, and configuration files retained in Git for demonstrations

Services left on the development computer would make the home-server build depend on that computer, its firewall,
and its current network address. The preferred design is to run the services on the ASUS server or another stable,
reachable test environment.

### Stage 3 — Dynamic duration-balanced sharding

Introduce dynamic sharding across the eligible fixed local agents:

```text
Parent calculates balanced manifests for one capability pool
├── Shard 1 → eligible local x86 agent
├── Shard 2 → eligible local x86 agent
├── Shard N → eligible local x86 agent
└── Small smoke work → Raspberry Pi only when explicitly selected
```

Initial duration balancing assumes workers in one pool have broadly comparable capabilities. Historical duration
from the HP cannot be treated as an accurate prediction for the Raspberry Pi. Hardware-specific timing factors
or separate histories may be added later, but the first planner will keep materially different capability pools
separate.

Stage 3 adds:

- Test discovery
- Persistent historical timing input
- Duration-balanced shard calculation
- Generic parameterized shard workers
- Fan-out cancellation and failure semantics
- Result fan-in and combined parent status
- Timing-history updates
- Optional shard-level infrastructure retry

Assertion failures remain test failures. Automatic retries will be limited to classified infrastructure loss,
such as an agent disappearing before reporting results, and must not hide deterministic product failures.

### Stage 4 — Elastic Docker agents on the home-lab x86 hosts

Replace selected permanently running worker containers on the ASUS and HP hosts with agent containers created
on demand. The controller will
calculate a shard count from historical serial duration and the target completion time, subject to configured
limits and each host's actual CPU, memory, disk, and network capacity. The Raspberry Pi remains a separately
labelled fixed agent unless a later measurement justifies a different lifecycle.

Stage 4 adds:

- On-demand Docker agent creation and removal
- Configurable shard count
- Measured TestNG thread parallelism
- More shards than available agents when useful for reducing tail imbalance
- Warm-image and dependency-cache strategy
- Queue, provisioning, execution, and aggregation metrics
- Hard concurrency limits protecting the services under test

Containers improve isolation but do not create compute capacity. This stage teaches elastic scheduling within a
fixed physical limit before introducing cloud capacity.

### Stage 5 — Temporary AWS/EKS learning environment

Stage 5 creates a separate AWS sandbox through Terraform. It will be used for approximately two or three test
runs over two or three days and then destroyed. The stable ASUS Jenkins installation will remain available; the
AWS lab will not replace it permanently.

The Jenkins test Pipeline will expose an explicit capacity choice such as `local` or `local-plus-aws`. Selecting
AWS capacity allows eligible shards to use ephemeral AWS agents only when the AWS platform has already been
provisioned by a separately controlled Terraform workflow. The framework itself never creates cloud resources.
The initial demonstration may cap AWS execution at one Jenkins agent/shard; later runs may raise that limit to
exercise pod scheduling and Karpenter scaling without changing the Pipeline contract.

The target topology is:

```text
Amazon EKS
├── Managed Kubernetes control plane
├── Small On-Demand system capacity
│   ├── Essential cluster services
│   ├── Karpenter controller
│   └── Temporary Jenkins controller if hosted in the cluster
└── Karpenter-managed Spot capacity
    ├── Ephemeral Jenkins agent pod: shard 1
    ├── Ephemeral Jenkins agent pod: shard 2
    └── Ephemeral Jenkins agent pod: shard N
```

One dynamically balanced shard will normally run in one ephemeral agent pod with one Jenkins executor. Pending
agent pods cause Karpenter to provision suitable Spot instances. Results are persisted externally, agent pods are
deleted, and empty Spot nodes are removed.

Stage 5 includes:

- A dedicated or strongly isolated AWS learning account/environment
- Terraform-managed networking, EKS, IAM, storage, registries, ingress, budgets, and limits
- ECR-hosted immutable Jenkins-agent images
- Karpenter-managed, diversified Spot capacity for test workers
- A small On-Demand system pool for components that must remain available
- EKS Pod Identity or the appropriate least-privilege workload identity mechanism
- S3 storage for results and timing history
- Ephemeral namespaces or environments for the services under test
- Provisioning, shard, service, and cost observability
- AWS Budget alerts, service-quota review, and maximum node/vCPU limits
- Verified teardown after the learning sessions

EKS is selected to learn Kubernetes scheduling, ephemeral Jenkins agents, node autoscaling, Spot interruption,
workload identity, and namespace isolation. If minimum operational complexity or price were the only objective,
AWS Batch or ECS/Fargate could run transient test containers more simply.

### Stage 5 preservation and later reactivation

Destroying the AWS resources does not remove the infrastructure design. The repository will retain:

- Terraform root modules and reusable modules
- The committed Terraform dependency lock file
- Explicitly constrained Terraform provider and module versions
- Helm values, Kubernetes manifests, and pinned chart versions
- Jenkins agent and service image Dockerfiles, build metadata, and ECR publication scripts
- Required secret names and credential setup instructions, never secret values
- Provision, validate, demonstrate, and destroy runbooks
- Automated smoke checks that prove the recreated platform is ready
- Sanitized architecture diagrams, sample outputs, and cost/teardown evidence useful for interviews

The preferred design retains only the inexpensive Terraform-state bootstrap when useful; the EKS stack, worker
nodes, load balancers, NAT gateways, public addresses, and other material-cost resources are destroyed. A fully
fresh state may also be used when recreating an intentionally disposable lab, provided the bootstrap and teardown
procedures clearly distinguish retained resources from disposable ones.

Recreation after several dormant months is expected, but it is not assumed to be maintenance-free. Before an
interview demonstration, run a compatibility review for AWS EKS versions, Terraform providers/modules, Helm
charts, Kubernetes APIs, Jenkins plugins, and external container base images. Apply upgrades deliberately, review
the plan, provision through the guarded infrastructure workflow, run readiness checks, and only then enable the
Pipeline's `local-plus-aws` capacity choice. Infrastructure source remains reproducible; live cloud resources and
test data are not expected to survive teardown.

## 7. Terraform and Kubernetes ownership boundary

Terraform will manage AWS infrastructure whose lifecycle exists outside an individual test run:

- VPC, subnets, routes, and security groups
- EKS cluster
- Managed system node group
- IAM roles and policies
- Karpenter prerequisites
- ECR repositories
- S3 buckets
- Ingress, DNS, and certificate-related infrastructure
- Budget alerts, resource tags, and capacity limits

Helm or Kubernetes manifests will manage cluster workloads:

- Jenkins controller when hosted in EKS
- Jenkins Kubernetes-agent templates
- Karpenter cluster resources
- Observability components
- Services under test
- Per-run namespaces

Jenkins will request agent pods and test environments through those established interfaces. Jenkins will not hold
broad AWS credentials or provision the base AWS platform directly.

Terraform state requires an explicit bootstrap choice. The preferred learning design is a small, inexpensive
state backend retained independently of the ephemeral EKS stack. This avoids trying to destroy the storage that
contains the state needed to complete destruction. The bootstrap resources may be removed after the final lab.

## 8. AWS cost and scaling realities

An EKS pod is a scheduling unit, not an independently cheap billing unit. AWS cost includes:

- EKS control-plane hours
- EC2 On-Demand and Spot instances or Fargate resources
- EBS or EFS storage
- Load balancers
- NAT Gateway hours and data processing
- Public IPv4 addresses
- Cross-Availability-Zone traffic
- CloudWatch log and metric ingestion
- ECR and S3 storage
- Secrets and data transfer

Spot workers suit test shards because lost infrastructure can be retried, but tests must not store their only
result copy on the agent. Karpenter must be allowed to choose from a sufficiently diverse set of instance types
and Availability Zones. Maximum NodePool resources and Pipeline concurrency will cap accidental spend.

Cold start is part of the five-minute objective:

```text
Jenkins queue
→ pod pending
→ Spot node provisioning
→ node bootstrap
→ image pull
→ agent connection
→ checkout
→ Gradle startup
→ tests
→ fan-in
```

Measurements must separate these phases. Warm system capacity, small agent images, same-region ECR, dependency
caching, and carefully chosen shard size will reduce startup overhead.

## 9. Test-environment scalability

Runner capacity alone cannot make a suite fast. Increased parallelism must also be supported by:

- Service replicas and request capacity
- Database connection pools
- Authentication and rate-limit capacity
- Unique test data and accounts
- Order-independent tests
- Parallel-safe cleanup
- Idempotent infrastructure retry
- Environment and namespace isolation

At AWS scale, the preferred model is an ephemeral namespace or environment per build when practical. Shared QA
environments may remain useful for smoke coverage, but they impose a lower concurrency ceiling and a larger risk
of cross-build interference.

## 10. Results, history, and observability

Ephemeral agent files disappear when a pod or container is removed. Before termination, each shard must publish:

- TestNG/JUnit-compatible result XML
- Framework logs
- Shard manifest
- Selected environment and redacted configuration summary
- Timing records
- Failure diagnostics
- Future reporting attachments

The combined build must report missing workers and incomplete result sets rather than presenting partial success.
At higher volume, artifacts and logs will be stored outside the Jenkins controller, with Jenkins retaining summary
and navigation information.

The platform will measure:

- Total build duration
- Queue delay
- Agent and node provisioning time
- Image-pull and checkout time
- Test execution and result aggregation time
- Predicted versus actual shard duration
- Slowest test and shard
- Flake and infrastructure-retry rates
- Service response time under test load
- Agent utilization
- Cost per run and cost per successful test

## 11. Security boundaries

Pull requests can change Java tests, Gradle logic, shell commands, dependencies, and the `Jenkinsfile`; they are
executable code. Untrusted pull requests must not automatically receive production credentials or broad AWS
access. Trusted and untrusted builds may require separate credentials, IAM roles, namespaces, agent pools, and
network policies.

AWS workloads will use short-lived, least-privilege identities. A test-agent role may read its ECR image, retrieve
specifically authorized test secrets, and write to one result prefix. It must not administer EKS or access the
Jenkins controller's persistent storage.

## 12. AWS teardown procedure

The AWS environment is temporary, so destruction is a required stage rather than optional cleanup:

1. Stop new Jenkins builds.
2. Wait for active shards or abort them deliberately.
3. Persist required reports and learning notes outside the disposable stack.
4. Delete per-run test namespaces.
5. Delete Kubernetes ingress and load-balancer resources.
6. Remove Karpenter worker NodePools and confirm Spot instances terminate.
7. Run `terraform destroy` for the ephemeral stack.
8. Verify that no load balancers, NAT gateways, EC2 instances, EBS volumes, snapshots, public IPs, or unexpected
   log groups remain.
9. Review billing and Cost Explorer after AWS usage data becomes available.
10. Remove the Terraform-state bootstrap after the final lab if it is no longer needed.

## 13. Reference documentation

- [Jenkins Pipeline as Code](https://www.jenkins.io/doc/book/pipeline/pipeline-as-code/)
- [Jenkins nodes, agents, and executors](https://www.jenkins.io/doc/book/managing/nodes/)
- [Jenkins Kubernetes plugin](https://plugins.jenkins.io/kubernetes/)
- [Scaling Jenkins on Kubernetes](https://www.jenkins.io/doc/book/scaling/scaling-jenkins-on-kubernetes/)
- [Amazon EKS pricing](https://aws.amazon.com/eks/pricing/)
- [Amazon EKS compute autoscaling](https://docs.aws.amazon.com/eks/latest/userguide/autoscaling.html)
- [Karpenter best practices](https://docs.aws.amazon.com/eks/latest/best-practices/karpenter.html)
- [Amazon EKS workload identity](https://docs.aws.amazon.com/eks/latest/userguide/service-accounts.html)

## 14. Outstanding implementation decisions

The architecture is locked, but these implementation details will be decided immediately before their stages:

- Exact controller and agent container images and pinned versions
- Docker Compose topology for Stages 1 and 2
- Secure ASUS webhook exposure mechanism
- Timing-history storage format during Stages 1–4
- Stable test-discovery identity and method-level sharding rules
- Parent-versus-generic-child Pipeline implementation details
- Infrastructure-failure classification and retry limits
- Dynamic Docker-agent mechanism for Stage 4
- AWS region, VPC cost design, and EKS Kubernetes version
- Terraform state backend and module/version policy
- Karpenter versus EKS Auto Mode implementation choice; the current recommendation is Karpenter for deeper learning
- Spot instance diversification, system-node sizing, and maximum budget
- Whether the temporary Jenkins controller runs inside EKS or separately in AWS
- Test-environment deployment and database-isolation strategy in EKS

These choices must preserve the accepted responsibility boundaries and staged progression recorded above.
