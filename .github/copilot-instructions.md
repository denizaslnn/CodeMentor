Build, test, and lint commands

- Build a single module (Windows):
  - code-service: code-service\mvnw.cmd -DskipTests package
  - ai-service: ai-service\mvnw.cmd -DskipTests package
  - api-gateway: api-gateway\mvnw.cmd -DskipTests package
- Build from module directory (Unix/Windows): cd code-service && ./mvnw -DskipTests package
- Run tests for a module: cd code-service && ./mvnw test
- Run a single test class or method:
  - cd code-service && ./mvnw -Dtest=CodeServiceApplicationTests test
  - or specify method: -Dtest=MyTestClass#testMethod
- Full verification: run mvn verify in a module
- Linting: no project-wide linter configured; run static analysis plugins if added (check HELP.md in each module for module-specific tools)

High-level architecture

- Monorepo with independent Spring Boot services in top-level folders: api-gateway, code-service, ai-service.
- api-gateway: Spring Cloud Gateway, reactive Redis caching, routes requests to services.
- code-service: REST entrypoints for creating code-analysis tasks, persists AnalysisTask via JPA (Postgres) and uses Redis for status caching. Publishes tasks to RabbitMQ (RabbitMqTaskPublisher / CodeTaskProducer).
- ai-service: Consumes RabbitMQ tasks, performs analysis, persists results (JPA) and updates Redis status. Rabbit topology and converters are configured in ai-service config classes.
- Inter-service comms: RabbitMQ (AMQP) for asynchronous tasks; services expect compatible message converters and content types.

Key conventions & repo-specific patterns

- Module layout: each service is a standalone Maven module with its own mvnw wrapper and pom.xml; build modules individually.
- Application config: application.yml or application.properties per module under src/main/resources.
- Messaging converters: RabbitMQ MessageConverter beans are declared in each service (RabbitMQConfig). Ensure producer and consumer use the same converter and contentType (JSON recommended).
- Java / Spring Boot versions vary between modules: check pom.xml java.version and Spring Boot parent version before running with a local JDK.
- Package vs file-path: watch for package declarations matching filesystem (some files use microservices.* packages). IDEs/CI will fail if package doesn't match path.
- Lombok is used; ensure annotation processing is enabled in IDE and build.

Important gotchas found while analyzing repository

- Message content-type mismatch: some messages are arriving with contentType=application/x-java-serialized-object causing ListenerExecutionFailedException in ai-service when the consumer expects JSON. Either:
  - Make the producer send JSON (rabbitTemplate.convertAndSend(object) with Jackson converter), or
  - Use a ContentTypeDelegatingMessageConverter to handle both java-serialized and JSON payloads on the consumer side.
  Java serialization is a security risk; prefer JSON.

- Deprecated vs newer AMQP converters: project currently uses Jackson2JsonMessageConverter (deprecated in newer spring-amqp). If upgrading Spring AMQP/Boot, replace with the new converter class and update imports.

- JDK mismatch in module poms: code-service targets Java 17; ai-service and api-gateway target Java 21. Use the correct JDK for the module being built.

Files to check first when debugging builds or runtime issues

- module HELP.md files: code-service/HELP.md and ai-service/HELP.md
- application.yml / application.properties in each module
- src/main/java/*/config/RabbitMQConfig.java and RabbitTopologyConfig.java in ai-service
- publisher/publisher-related classes in code-service (RabbitMqTaskPublisher, TaskEventPublisher)

AI assistant config files discovered

- None found (.github/copilot-instructions.md created). Also searched for CLAUDE.md, .cursorrules, AGENTS.md, .windsurfrules, CONVENTIONS.md — none present.

If you want

- Examples: a short checklist to run local end-to-end (start RabbitMQ, Postgres, Redis via docker-compose then build & run services). I can add that as a follow-up.

---
Created by Copilot assistant: concise, practical guidance for future Copilot sessions. Please tell me if you want an end-to-end runbook added or to configure MCP servers (e.g., Playwright) for this repo.
