package com.example;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DEMO 4 - the servlet lifecycle. Watch your TERMINAL while you hit this page.
 *
 * Try: http://localhost:8080/lifecycle   (reload several times, then Ctrl+C the server)
 *
 * The container - not you - controls the lifecycle:
 *
 *   init()     ONCE, when the servlet is first loaded.
 *              Open DB pools / read config here. Throwing here disables the servlet.
 *
 *   service()  ONCE PER REQUEST, on a thread from the pool.
 *              HttpServlet's implementation reads the HTTP method and calls
 *              doGet / doPost / doPut / doDelete. You normally override those,
 *              not service() itself.
 *
 *   destroy()  ONCE, at shutdown. Release resources here.
 *
 * You never call any of these yourself, and you never call `new` on a servlet in
 * application code. That inversion is the whole point of a container.
 */
public class LifecycleServlet extends HttpServlet {

    private Instant startedAt;
    private final AtomicInteger requestCount = new AtomicInteger();

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);   // if you override this form, ALWAYS call super - it stores the config
        startedAt = Instant.now();
        System.out.println(">>> [lifecycle] init()    called once, on thread "
                + Thread.currentThread().getName());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        int n = requestCount.incrementAndGet();
        System.out.println(">>> [lifecycle] doGet()   request #" + n + " on thread "
                + Thread.currentThread().getName());

        resp.setContentType("text/html;charset=UTF-8");
        resp.getWriter().println("""
                <!doctype html>
                <html><body style="font-family: sans-serif">
                  <h1>Lifecycle</h1>
                  <p>init() ran at <b>%s</b> - that was <b>%d seconds</b> ago,
                     and it has NOT run again.</p>
                  <p>doGet() has now run <b>%d</b> times.</p>
                  <p>This request is on thread <code>%s</code>.</p>
                  <h3>Look at your terminal</h3>
                  <p>You will see one <code>init()</code> line and one
                     <code>doGet()</code> line per reload. Press Ctrl+C on the server
                     and you will see <code>destroy()</code>.</p>
                  <p><a href="/lifecycle">reload</a> | <a href="/">back</a></p>
                </body></html>
                """.formatted(
                        startedAt,
                        Duration.between(startedAt, Instant.now()).toSeconds(),
                        n,
                        Thread.currentThread().getName()));
    }

    @Override
    public void destroy() {
        System.out.println(">>> [lifecycle] destroy() called once. Served "
                + requestCount.get() + " requests.");
    }
}
