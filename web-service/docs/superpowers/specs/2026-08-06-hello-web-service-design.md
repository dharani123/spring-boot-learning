# Hello Web Service — Design

**Date:** 2026-08-06

## Assignment

> Create a simple RESTful web service that takes a string as a path parameter and prints
> `Hello "string"` as the output.

## Goal

Serve `GET /hello/{name}` and respond with `Hello "name"` — the quotes literal, as the
assignment shows.

```
GET /hello/World  ->  200  Hello "World"
```

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Stack | Plain servlet, manual path parsing | Chosen deliberately over JAX-RS. Shows what a REST framework's `@PathParam` does for you by doing it by hand. |
| Missing name | Default to `World` | No input can fail, so the servlet needs no validation branch. |
| Response type | `text/plain; charset=UTF-8` | Quotes stay quotes — no HTML escaping. `curl` shows exactly what was sent. |
| Container | Embedded Tomcat 10 | Same runner as the servlet assignment, so the new idea stands out against a familiar background. |

The stack choice is worth stating plainly: hand-parsing a path is not what "RESTful web
service" usually means in Java — JAX-RS (`@Path("/hello/{name}")` + `@PathParam`) is the
standard, and it is the shorter, more idiomatic answer. This project takes the manual route
on purpose, as the last step before the framework does it for you.

## Architecture

```
pom.xml
src/main/webapp/index.html          landing page with a try-it form
src/main/java/com/example/
  Main.java                         embedded Tomcat bootstrap (no URL mappings)
  HelloServlet.java                 @WebServlet("/hello/*") — the assignment
src/test/java/com/example/
  HelloServletTest.java             unit tests for name extraction
```

Two units, one job each:

- **`Main`** starts a container and knows nothing about URLs. Every mapping lives in an
  annotation on the class it describes.
- **`HelloServlet`** answers one route. Its parsing rule is a `static` pure function, so the
  logic is testable without starting a container.

## The one new concept: prefix mapping

`@WebServlet("/hello")` matches exactly one URL. The `/*` suffix makes it a *prefix* mapping,
which is what lets a path carry data. Tomcat then splits the URL:

| For `GET /hello/World` | |
|---|---|
| `getServletPath()` | `/hello` — the part that matched the mapping |
| `getPathInfo()` | `/World` — everything after it, **URL-decoded** |

`getPathInfo()` is the path parameter. Manual parsing means one explicit rule:

> **The name is the first non-empty segment of `getPathInfo()`. If there is none, it is `World`.**

One rule covers every case, rather than a branch per case:

| Request | `getPathInfo()` | Name | Response |
|---|---|---|---|
| `/hello/World` | `/World` | `World` | `Hello "World"` |
| `/hello/` | `/` | none | `Hello "World"` |
| `/hello` | `null` | none | `Hello "World"` |
| `/hello/%20` | `/ ` (decoded) | `" "` | `Hello " "` |
| `/hello/a/b` | `/a/b` | `a` | `Hello "a"` |
| `/hello/José` | `/José` | `José` | `Hello "José"` |

Taking the first segment and ignoring the rest is what keeps the rule to one sentence.

## Error handling

There is none, by design. The default-to-`World` rule means no input can fail, so
`HelloServlet` contains no validation, no null checks and no try/catch. This is the
deliberate contrast with `CalculatorServlet` in the servlet assignment, where a filter
rejected bad input before the servlet ran.

The one thing that can still fail is outside our code: Tomcat rejects URL-encoded slashes
(`%2F`) in a path with `400` before the servlet is reached. That is container policy, left
at its secure default.

## Encoding

`getPathInfo()` is URL-decoded by the container using Tomcat 10's default URI encoding,
UTF-8. The response sets `charset=UTF-8` explicitly so a non-ASCII name survives the round
trip and is not mangled to `?` by a platform default.

## Testing

`extractName(String pathInfo)` is a `static` pure function, so it is tested directly with
JUnit 5 — no container, no HTTP, no ports. Cases: a normal name, `null`, `/`, empty, a blank
segment, multiple segments, and a non-ASCII name.

Manual verification with `curl` against the running server then confirms the wiring that
unit tests cannot see: the annotation was scanned, the mapping matched, the content type and
charset arrived intact.

Note that JUnit 5 requires Surefire 3.x pinned in `pom.xml`. Maven 3.8.7 bundles Surefire
2.12.4, which predates JUnit 5 and silently runs zero tests.
