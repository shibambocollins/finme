# ADR-005: Azure SQL Serverless instead of MySQL

**Status:** Accepted — supersedes the MySQL choice in the original tech stack
**Date:** 2026-09

## Context

The project planned MySQL for production. It is what the developer knew, and the application
uses JPA, so the dialect was not expected to matter.

Deployment made the actual constraint visible: **$55 of student credit remaining, 86 days
before expiry, with roughly $37/month already committed elsewhere.** The database had to be
effectively free, not merely cheap.

On Azure:

- **Azure Database for MySQL** has a Flexible Server free trial, then charges. The cheapest
  ongoing tier is a real monthly cost.
- **Azure SQL Database Serverless** auto-pauses when idle and bills compute only while active.
  For a personal app used a few times a day, that approaches zero.
- **Azure SQL Free Limit** offers a permanently free allowance.

## Decision

**Azure SQL Database Serverless (GP_S_Gen5, 0.5 vCore minimum) for production. H2 file-based
for local development.**

Concretely:

```xml
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <scope>runtime</scope>
</dependency>
```

MySQL never ran in production. The change happened during first deployment, driven by cost.

## Consequences

### It is close to free

The intended outcome, achieved.

### Auto-pause causes a slow first request

Serverless pauses when idle and takes 30–60 seconds to resume. The first request after a quiet
period pays that.

This compounds with the App Service free tier, which unloads the application after ~20 minutes
idle. Together they are the main source of the slow first sign-in —
[Troubleshooting](/operations/troubleshooting#slow-first-request).

Raising the auto-pause delay (15 → 60 minutes) is free and removes a large part of it.

### Three settings exist solely because of this

Each traces to a specific deployment failure:

```properties
# Resume takes 30-60s; without this, context init fails and the app cold-restarts
spring.datasource.hikari.initialization-fail-timeout=60000

# Otherwise: "Unable to determine Dialect without JDBC metadata" when the DB is still resuming
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.SQLServerDialect

# validate fails on an empty database - no Flyway or Liquibase in the project
spring.jpa.hibernate.ddl-auto=update
```

Full context in
[Environments](/operations/environments#production-settings-and-why-each-exists).

### Dev and prod run different engines

H2 locally, SQL Server in production. A real risk: JPA abstracts most of it, but not all.
Accepted because the alternative — running SQL Server locally — costs more setup friction on
every machine than the risk it removes for a project of this size.

`ddl-auto=update` in both keeps schema generation consistent, and the integration tests run
against H2 with the same entities.

### Free Limit locks some settings

A database on Azure SQL's Free Offer cannot change its auto-pause delay:
`ProvisioningDisabled: Only default value for auto pause delay is allowed for Free Limit
database`. Worth knowing before planning around a setting that turns out to be fixed.

## Alternatives considered

**Azure Database for MySQL.** The original plan. Free trial then a real monthly cost, against
a credit balance that had to last 86 days.

**PostgreSQL on a free host.** Neon, Supabase and similar have genuine free tiers. Rejected to
keep the deployment inside one cloud — one bill, one identity model, one set of firewall rules.
Worth revisiting if Azure credit runs out.

**SQLite.** Free and simple; App Service's filesystem is not durable across restarts in a way
that makes this safe.

**H2 in production.** Same objection, plus it is not built for it.

## Revisit when

- Azure student credit expires — the calculation changes entirely
- Cold start becomes unacceptable and paid compute is on the table anyway
- The dev/prod engine split causes a real bug, which would justify running SQL Server locally
