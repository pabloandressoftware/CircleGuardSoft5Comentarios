# CircleGuard — Executive Summary (Part 2)

*Status as of 24 November 2026. Defense: 25 November 2026.*

**What it is.** CircleGuard controls campus access based on health status. People
report symptoms, the system updates each person's status and that of their
contacts, and the campus gate validates a QR code against that status. The system has
eight Spring Boot microservices, a web client, PostgreSQL, Redis, Neo4j, Kafka and LDAP.

**Where it runs.** Release **v1.1.2** runs on AWS (us-east-2). Terraform provisions
one CI server (Jenkins, Nexus, SonarQube) and three two-node k3s clusters for dev,
stage and prod. Each environment has its own VPC, fixed IP and public HTTPS
(Let's Encrypt). The release passed the full pipeline:
- quality gates and security scans;
- automated deployment to dev, then stage, then prod;
- end-to-end tests (5/5) and an OWASP ZAP baseline (0 failures);
- manual production approval by a restricted user.

All three environments report 9/9 services healthy on the same immutable images.

**How it was stabilized.** Automated controls stopped every defect before production:
- the image scanner blocked a vulnerable first release (v1.1.0);
- end-to-end tests caught a broken login between two services;
- a plan guard blocked an unintended server replacement.

Each fix shipped through reviewed pull requests, giving patch releases 1.1.1 and 1.1.2.

**Security and observability.**
- **Pipeline security:** secret scanning and an image scan that blocks critical
  vulnerabilities. One documented, digest-bound exception expires on 27 November.
- **Application security:** closed by default, with role-based permissions and an
  internal key between services.
- **Credential incident:** a Neo4j password leaked into a build log. It was rotated
  the same day in stage and prod, verified by hash, and the cause was fixed.
- **Alerting:** Prometheus, Grafana and Alertmanager run in every cluster. Three
  critical alerts were fired and resolved on purpose in dev and stage.
- **Rollback:** a real Helm rollback (v1.1.2 → v1.1.1) was measured in dev, and dev
  was then returned to v1.1.2.

**Cost and shutdown.**
- **Plan:** AWS Free plan with USD 160 in credits. Actual billed spend shows USD 0.00
  so far, since billing data lags.
- **Run rate:** about USD 0.76/hour with everything on and USD 0.33/hour with stage
  and prod stopped. Staging and production run only for tests, keeping the estimate
  near **USD 23** against a USD 25 budget by the end of 25 November.
- **Shutdown:** after 23:59 Colombia time, Terraform tears down prod, stage, dev, CI
  and finally the state backend. The AWS project itself is not closed.

**Open items (honest gaps).**
1. Discord alert notifications. The webhook was never provided, so alerts stay in
   Alertmanager. Pipeline email notices also fail (the SMTP server rejects the login).
2. Two services did not expose metrics because of a configuration override. The fix
   and a regression test are in PR #64 and need the next release.
3. The feature-toggle story (HU-23) is only partially met. Parameters change at runtime
   through the database, not a Kubernetes ConfigMap, and no Bulkhead or rate-limit
   pattern was added.
4. Distributed tracing (Jaeger/Loki) is not installed on AWS. ZAP security-header
   warnings are not addressed yet.
5. Story HU-22 was removed from the board for external reasons and is not counted.

**Sprint 2.** 54 of 62 committed points were integrated and deployed (10 of 12
stories). The remaining two are this documentation and the defense itself. The main
lesson: most work was merged in the last two days, which turned integration into
three releases in 48 hours. Next time, small daily merges and an up-to-date board.

**Next steps.**
1. Ship PR #64.
2. Configure Discord.
3. Finish HU-23 (ConfigMap toggle plus Bulkhead).
4. Install tracing on AWS and add security headers at the ingress.
5. Retire all AWS resources in order after the defense.
