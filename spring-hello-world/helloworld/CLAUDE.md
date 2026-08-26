# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

All commands use the bundled Maven wrapper (`./mvnw`); no local Maven install is needed.

```bash
./mvnw spring-boot:run                 # run the app (http://localhost:8080)
./mvnw test                            # run all tests
./mvnw test -Dtest=HelloworldApplicationTests            # single test class
./mvnw test -Dtest=HelloworldApplicationTests#contextLoads   # single test method
./mvnw package                         # build executable jar into target/
java -jar target/helloworld-0.0.1-SNAPSHOT.jar           # run the packaged jar
```

Smoke check the one endpoint: `curl 'http://localhost:8080/greeting?name=Foo'`

There is no linter or formatter configured.

## Project shape

A Spring Boot learning/scratch project — a single `@RestController` with one `GET /greeting` endpoint that takes an optional `name` query param (defaults to `World`) and returns a plain string.

- `HelloworldApplication` — `@SpringBootApplication` entry point; component scanning is rooted at `com.example.helloworld`, so new classes must live under that package to be picked up.
- `controller/HelloController` — the only endpoint.
- `model/Greeting` — a POJO currently annotated `@Component` (registered as a bean) but not injected or returned anywhere. It's a placeholder toward returning JSON instead of a string; if it becomes a response/DTO type, the `@Component` annotation should come off.

## Version notes

- **Spring Boot 4.1.0.** Starter artifacts were renamed in Boot 4: this project uses `spring-boot-starter-webmvc` and `spring-boot-starter-webmvc-test`, not the pre-4.x `spring-boot-starter-web` / `spring-boot-starter-test`. Don't "fix" these to the older names, and check that any Boot snippets you copy in are 4.x-era.
- `pom.xml` targets Java 17 while the machine's default JDK is 21. Builds work, but code must stay Java 17-compatible.
- `pom.xml` carries deliberately empty `<licenses>`, `<developers>`, and `<scm>` blocks — these suppress unwanted inheritance from the Spring Boot parent POM (see `HELP.md`). Leave them.

## Environment

Not a git repository — there is no version history to consult, and commit/branch workflows don't apply unless the user initializes one.
