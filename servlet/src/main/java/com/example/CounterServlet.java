package com.example;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DEMO 3 - the single most important servlet fact: ONE INSTANCE, MANY THREADS.
 *
 * Try: http://localhost:8080/counter  (reload it a few times)
 * Then open the same URL in a private/incognito window and compare the two counters.
 *
 * Tomcat creates exactly one CounterServlet object for the whole application and runs
 * every concurrent request through it. So servlet instance fields are SHARED BY ALL USERS.
 */
public class CounterServlet extends HttpServlet {

    /*
     * SAFE: AtomicInteger.incrementAndGet() is atomic, so concurrent requests cannot
     * lose an update. This is deliberately application-wide state - every visitor
     * increments the same number.
     */
    private final AtomicInteger safeTotal = new AtomicInteger();

    /*
     * UNSAFE, ON PURPOSE: a plain int. `unsafeTotal++` is really three operations
     * (read, add, write). Two threads can read the same value and both write back the
     * same result, so increments get lost under load. Run the load test in the README
     * and watch this number fall behind the safe one.
     *
     * Do not do this in real code. It is here so you can SEE the bug.
     */
    private int unsafeTotal = 0;

    /*
     * ALSO UNSAFE, and a nastier bug: per-request data parked in an instance field.
     * Under concurrency, user A can end up seeing the value user B just wrote.
     * This is the classic beginner mistake.
     */
    private String lastVisitorName;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        int safe = safeTotal.incrementAndGet();
        unsafeTotal++;                                   // <- lost updates happen here

        String name = req.getParameter("name");
        lastVisitorName = name;                          // <- leaks between users

        /*
         * The RIGHT place for per-user state: the session.
         * Tomcat sets a JSESSIONID cookie, and getSession() gives you a per-browser map.
         * A different browser (or incognito) gets a different session and its own count.
         */
        HttpSession session = req.getSession();
        Integer myVisits = (Integer) session.getAttribute("visits");
        myVisits = (myVisits == null) ? 1 : myVisits + 1;
        session.setAttribute("visits", myVisits);

        /*
         * And the RIGHT place for per-request state: a local variable, like `safe`
         * and `name` above. Locals live on the calling thread's stack, so they are
         * never shared. Locals are your default; anything else needs justification.
         */

        resp.setContentType("text/html;charset=UTF-8");
        resp.getWriter().println("""
                <!doctype html>
                <html><body style="font-family: sans-serif">
                  <h1>Counters</h1>
                  <table border="1" cellpadding="8" style="border-collapse:collapse">
                    <tr><th align="left">Application-wide (AtomicInteger, safe)</th><td><b>%d</b></td></tr>
                    <tr><th align="left">Application-wide (plain int, UNSAFE)</th><td><b>%d</b></td></tr>
                    <tr><th align="left">This browser only (HttpSession)</th><td><b>%d</b></td></tr>
                  </table>
                  <p>Session id: <code>%s</code> (new session: %s)</p>
                  <p>Last visitor name seen by the shared field: <code>%s</code></p>
                  <p>Handled by thread: <code>%s</code> &larr; reload and watch this change</p>
                  <p>Reload a few times. Then open this page in an incognito window:
                     the top two numbers keep climbing, the session one restarts at 1.</p>
                  <p><a href="/counter">reload</a> | <a href="/">back</a></p>
                </body></html>
                """.formatted(
                        safe,
                        unsafeTotal,
                        myVisits,
                        session.getId(),
                        session.isNew(),
                        HelloServlet.escape(String.valueOf(lastVisitorName)),
                        Thread.currentThread().getName()));
    }
}
