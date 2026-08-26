# Servlet Assignment

Two servlets on embedded Tomcat 10 (`jakarta.servlet`, Java 21):

| # | Endpoint | What it does |
|---|---|---|
| Q1 | `/calculate` | Two numbers + an operator (`+ - * /`) &rarr; the result. Inputs validated by a **filter**. |
| Q2 | `/count` | A string &rarr; its number of characters. |

## Run it

```bash
mvn compile
mvn exec:java
```

Then open <http://localhost:8080/> for forms covering both servlets. Ctrl+C to stop.

> Run from the project root (where `pom.xml` is). `Main` resolves `src/main/webapp` and
> `target/classes` relative to the working directory and will tell you if either is missing.

## Browser or Postman: one endpoint, two shapes

Every endpoint answers with **HTML** or **JSON** depending on who is asking:

- a **browser** sends `Accept: text/html` &rarr; gets a page with a pre-filled form
- **Postman** / `curl` send `Accept: */*` &rarr; get JSON
- append **`?format=json`** or **`?format=html`** to force either shape

That last one is the easy way to see the JSON without leaving the browser.

## Q1 — Calculator

`GET` or `POST` to `/calculate` with `a`, `b`, `op`.

```bash
curl "http://localhost:8080/calculate?a=6&b=3&op=%2B"     # {"a":6.0,"b":3.0,"op":"+","result":9.0}
curl "http://localhost:8080/calculate?a=6&b=3&op=-"       # result -> 3.0
curl "http://localhost:8080/calculate?a=6&b=3&op=*"       # result -> 18.0
curl "http://localhost:8080/calculate?a=6&b=3&op=%2F"     # result -> 2.0

# POST with a form-encoded body works identically
curl -X POST -d "a=2.5&b=4&op=*" http://localhost:8080/calculate
```

**URL-encode `+` as `%2B` and `/` as `%2F`.** A raw `+` in a query string means "space", so
`op=+` arrives as a blank operator and is rejected. This bites everyone once. In Postman, put
the values in the params table and it encodes them for you; in a browser form it is automatic.

### The filter

`CalculatorValidationFilter` is mapped to `/calculate` and runs **before** the servlet. If input
is invalid it writes a `400` and never calls `chain.doFilter(...)`, so the servlet does not run.
That is why `CalculatorServlet` contains no null checks, no try/catch and no divide-by-zero
guard — bad input cannot reach it.

| Request | Status | `error` |
|---|---|---|
| `?a=6&op=*` | 400 | `MISSING_PARAMETER` |
| `?a=abc&b=3&op=*` | 400 | `NOT_A_NUMBER` |
| `?a=6&b=3&op=%25` | 400 | `INVALID_OPERATOR` |
| `?a=6&b=3` | 400 | `INVALID_OPERATOR` |
| `?a=6&b=0&op=%2F` | 400 | `DIVISION_BY_ZERO` |

```bash
curl -i "http://localhost:8080/calculate?a=6&b=0&op=%2F"
# HTTP/1.1 400
# {"error":"DIVISION_BY_ZERO","message":"Cannot divide by zero: 'b' must not be 0 when 'op' is /"}
```

Two details worth noticing:

- `b=0` is only an error for division. `?a=6&b=0&op=*` returns `0.0` quite happily.
- The operator is checked **before** the zero divisor, so `?a=1&b=0&op=%25` reports
  `INVALID_OPERATOR` rather than blaming the zero.
- `NaN` and `Infinity` are rejected as `NOT_A_NUMBER`, even though `Double.parseDouble` accepts
  them.

## Q2 — Character count

`GET` or `POST` to `/count` with `text`.

```bash
curl "http://localhost:8080/count?text=hello"             # {"text":"hello","count":5}
curl "http://localhost:8080/count?text=hello%20world"     # count -> 11 (spaces included)
curl "http://localhost:8080/count?text="                  # count -> 0  (empty is valid)
curl "http://localhost:8080/count"                        # 400 MISSING_PARAMETER
curl -X POST -d "text=hello" http://localhost:8080/count
```

Counting is `text.length()` — the raw character count, spaces included. `setCharacterEncoding("UTF-8")`
runs before the first parameter read, so multi-byte input counts correctly rather than by bytes.

## Testing from Postman

1. **New Request** &rarr; `GET` &rarr; `http://localhost:8080/calculate`
2. Open the **Params** tab and add `a` = `6`, `b` = `3`, `op` = `*`. Postman encodes the values.
3. **Send.** You get JSON, because Postman's default `Accept` is `*/*`.
4. To test `POST`: switch the method, then **Body** &rarr; **x-www-form-urlencoded**, same three keys.
5. To see what a browser sees, add a header `Accept: text/html` and Send again.

## Project layout

```
pom.xml
src/main/webapp/index.html                    landing page with forms for both servlets
src/main/java/com/example/
  Main.java                                   starts embedded Tomcat (contains NO mappings)
  CalculatorValidationFilter.java   Q1  @WebFilter("/calculate")   - the validation rules
  CalculatorServlet.java            Q1  @WebServlet("/calculate")  - pure arithmetic
  CharacterCountServlet.java        Q2  @WebServlet("/count")
  Responder.java                        shared: negotiation, JSON building, escaping
docs/superpowers/specs/                       design document
```

URL mappings live in `@WebServlet` / `@WebFilter` annotations, not in `web.xml` and not in
`Main`. For the container to find them, `Main` uses `Tomcat.addWebapp(...)` rather than
`addContext(...)` — only the former runs the annotation scanner — and maps `target/classes` in
as the webapp's `/WEB-INF/classes` so the scanner has somewhere to look. Both points are
commented in `Main.java`, along with the classloader line that `mvn exec:java` requires.
