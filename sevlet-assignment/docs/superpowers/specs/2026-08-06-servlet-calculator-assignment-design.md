# Servlet Assignment — Calculator with Filter, and Character Count

**Date:** 2026-08-06
**Status:** Approved

## Problem

Two servlets, per the assignment:

1. **Q1** — a calculator servlet taking two numbers and an arithmetic operator (`+`, `-`, `*`, `/`)
   as request parameters, returning the result. A **filter** validates the inputs and the
   operator, rejecting invalid input with an error message.
2. **Q2** — a servlet taking a string and returning its number of characters.

Both must be testable from **Postman** and from a **browser**.

## Approach

Match the conventions already established in the sibling `moodle_learning/servlet/` project:
Maven, Java 21, `jakarta.servlet` on embedded Tomcat 10.1, single `com.example` package,
heavily commented in a teaching style.

Two deviations from that project, both deliberate:

- **Registration uses `@WebServlet` / `@WebFilter` annotations** rather than programmatic
  `Tomcat.addServlet(...)` calls. Mappings live next to the code they describe, which is what
  a servlet assignment is normally demonstrating. This requires `Tomcat.addWebapp(...)` instead
  of `addContext(...)`, since only the former runs the annotation scanner.
- **Responses are content-negotiated** so one endpoint serves both test clients well.

## Components

Four classes, each with one responsibility:

| Class | Responsibility |
|---|---|
| `CalculatorServlet` | `@WebServlet("/calculate")`. Assumes input is already valid. Parses, computes, renders. |
| `CalculatorValidationFilter` | `@WebFilter("/calculate")`. Rejects invalid input with 400 before the servlet runs. |
| `CharacterCountServlet` | `@WebServlet("/count")`. Reads `text`, returns its length. |
| `Responder` | Shared rendering helper: format negotiation, JSON building, HTML escaping, error pages. |

`Main` starts embedded Tomcat and points it at `target/classes` so the annotation scanner finds
the servlets. It contains no mappings.

## Data Flow

```
request
  -> CalculatorValidationFilter   (valid? pass through : write 400 and stop)
  -> CalculatorServlet            (compute)
  -> Responder                    (render HTML or JSON per Accept)
```

The filter never computes and the servlet never validates. That separation is the point of the
exercise: `CalculatorServlet.doGet` contains no error handling at all, because by the time it
runs, invalid input cannot reach it.

## Content Negotiation

Resolved by `Responder.negotiate(request)`, in priority order:

1. `?format=json` or `?format=html` — explicit override, so either shape can be forced from a
   browser address bar.
2. `Accept` header containing `text/html` — browsers send this, so they get an HTML page with a
   pre-filled form.
3. Otherwise — JSON. Postman's default `Accept: */*` lands here.

Applies identically to success and error responses, including those written by the filter.

## Validation Rules

All enforced by `CalculatorValidationFilter`, all returning HTTP 400 with a distinct `error` code:

| Condition | `error` code |
|---|---|
| `a` or `b` missing or blank | `MISSING_PARAMETER` |
| `a` or `b` not parseable as a number | `NOT_A_NUMBER` |
| `op` missing, or not one of `+` `-` `*` `/` | `INVALID_OPERATOR` |
| `op` is `/` and `b` is zero | `DIVISION_BY_ZERO` |

Division by zero is validated in the filter, not the servlet. It is a rule about whether the
inputs are acceptable, and keeping it in the filter is what leaves the servlet free of error
handling entirely.

The filter checks the operator before division-by-zero, so `a=1&b=0&op=%` reports
`INVALID_OPERATOR` rather than a confusing zero-divisor error.

## Error Response Shape

JSON:

```json
{ "error": "INVALID_OPERATOR", "message": "op must be one of + - * /" }
```

HTML: a styled error page carrying the same code and message, plus a link back to the form.

## HTTP Methods

Both servlets implement `doGet` and delegate `doPost` to it. `getParameter()` reads from the
query string on GET and from a form-encoded body on POST, so Postman can exercise either without
a code change. `request.setCharacterEncoding("UTF-8")` is called before the first parameter read
so non-ASCII input counts correctly in Q2.

## Assumptions

- **The filter covers `/calculate` only.** Q2 states no filter requirement, so
  `CharacterCountServlet` performs its own minimal check: missing `text` returns 400.
- **"Number of characters" means `text.length()`** — the raw count, including spaces.

## Testing

`README.md` carries ready-to-paste `curl` commands and matching browser URLs covering every
success case and every validation rule above. `src/main/webapp/index.html` provides a browser
landing page with forms for both servlets.

## Out of Scope

- Persistence, sessions, authentication.
- A JSON parsing library. The responses are small and fixed-shape, so `Responder` builds them
  directly and keeps the dependency count down.
- Integer vs. floating-point result distinction. All arithmetic is `double`.
