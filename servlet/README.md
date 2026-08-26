# Servlet basics — plain Java, no Spring

A minimal servlet app so you can see the machinery directly. No framework, no
application server to install, no `.war` to deploy.

## Run it

```bash
mvn compile exec:java
```

Then open <http://localhost:8080/> and work through the links. Stop with `Ctrl+C` —
watch the terminal on the way out, `destroy()` prints there.

If port 8080 is taken the app exits with a clear message; free it with
`ss -ltnp | grep 8080`, or change `int port` in `Main.java`.

## What the pieces are

| Thing | Role |
|---|---|
| **Servlet** (`HelloServlet`, …) | Your code. A Java class that takes a request and fills in a response. |
| **Servlet container** (Tomcat) | Speaks HTTP so you don't have to. Parses bytes, manages threads, calls your servlet. |
| **`jakarta.servlet` API** | The interface between the two. `HttpServlet`, `HttpServletRequest`, `HttpServletResponse`. |
| **`Main.java`** | Starts an embedded Tomcat and maps URLs to servlets. |

```
Browser ──HTTP──► Tomcat ──Java call──► YourServlet.doGet(req, resp)
                    │                            │
              parses raw HTTP              you write only this
              picks a thread               resp.getWriter().print(...)
              routes by URL                        │
              writes raw HTTP  ◄───────────────────┘
```

## The demos

| URL | File | Teaches |
|---|---|---|
| `/` | `HomeServlet.java` | URL pattern matching; why `/` catches everything unmatched |
| `/hello?name=Ravi` | `HelloServlet.java` | Query params, status, headers, body |
| `/greet` | `GreetServlet.java` | One URL, two methods: GET renders a form, POST processes it |
| `/counter` | `CounterServlet.java` | **One instance, many threads.** Plus `HttpSession` for per-user state |
| `/lifecycle` | `LifecycleServlet.java` | `init` → `service` → `destroy` (watch your terminal) |

## The four things actually worth internalising

**1. You never instantiate a servlet.** The container does, and it calls
`init()` / `service()` / `destroy()` on its own schedule. That inversion is the
whole point of a container.

**2. One instance serves every request, concurrently.** Tomcat creates a single
`CounterServlet` object and runs many threads through it. So:

```java
private String name;                         // ❌ shared by ALL users
protected void doGet(req, resp) {
    name = req.getParameter("name");         // user B can overwrite user A's value
}
```

Per-request state goes in **local variables**. Per-user state goes in the
**session**. Instance fields are only for things that are immutable or
thread-safe.

**3. `service()` is the dispatcher.** It reads the HTTP method and calls
`doGet` / `doPost` / `doPut` / `doDelete`. You override those, not `service()`.
Don't override one and you get `405 Method Not Allowed`.

**4. Headers before body.** Once you write to `getWriter()`, the response is
committed — `setStatus()` and `setHeader()` silently stop working.

## Seeing the race condition

`/counter` keeps two application-wide counters: a safe `AtomicInteger` and a
deliberately unsafe plain `int`. Hammer it concurrently:

```bash
# 6 parallel clients, 400 requests each
for i in 1 2 3 4 5 6; do
  (for n in $(seq 400); do curl -s -o /dev/null localhost:8080/counter; done) &
done; wait

curl -s localhost:8080/counter | grep -oE "(safe|UNSAFE)\)</th><td><b>[0-9]+"
```

The unsafe counter comes out **lower**. A verified run on this machine gave:

```
AtomicInteger, safe)   2405
int, UNSAFE)           2154     ← 251 increments silently lost
```

Those are lost updates: `unsafeTotal++` is really read-add-write, so two threads
read the same value and both write back the same result. Exactly the bug that
bites people who put mutable state in servlet fields. Your numbers will differ —
the point is that the second one is smaller.

## Two notes on real deployments

**`jakarta` vs `javax`.** The package was renamed at Jakarta EE 9. Tomcat 10+ and
Spring Boot 3+ use `jakarta.servlet`; anything older uses `javax.servlet`. Most
tutorials online still say `javax` and will not compile here.

**Mapping URLs.** `Main.java` maps them programmatically, which keeps everything
visible in one file. A real WAR does it with an annotation on the class:

```java
@WebServlet("/hello")
public class HelloServlet extends HttpServlet { ... }
```

or in `WEB-INF/web.xml`. Same result; the container scans for them at startup.

## Where this connects to Spring

Spring MVC is one servlet — `DispatcherServlet` — mapped to `/`. Every
`@RestController` method you write is eventually invoked from inside that single
servlet's `service()` call. Spring's job is routing and binding *after* the
container has already done everything on the left side of the diagram above.
