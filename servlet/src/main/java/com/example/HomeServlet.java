package com.example;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * The index page. Mapped to "/".
 *
 * URL PATTERN RULES worth knowing (the container matches in this order):
 *   1. exact match          "/hello"      only /hello
 *   2. longest path prefix  "/api/*"      /api/users, /api/users/5, ...
 *   3. extension            "*.pdf"       anything ending in .pdf
 *   4. default              "/"           everything that nothing else matched
 *
 * Because this servlet uses pattern 4, it receives ANY unmatched URL - try
 * http://localhost:8080/does-not-exist and see the 404 produced below.
 */
public class HomeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        resp.setContentType("text/html;charset=UTF-8");

        // "/" catches everything unmatched, so we have to 404 by hand.
        if (!"/".equals(req.getRequestURI())) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);   // 404
            resp.getWriter().println("""
                    <!doctype html>
                    <html><body style="font-family: sans-serif">
                      <h1>404 - nothing mapped to %s</h1>
                      <p>This response came from HomeServlet, because it is mapped to
                         <code>/</code>, the default pattern that catches every URL no
                         other servlet claimed.</p>
                      <p><a href="/">back</a></p>
                    </body></html>
                    """.formatted(HelloServlet.escape(req.getRequestURI())));
            return;
        }

        resp.getWriter().println("""
                <!doctype html>
                <html><body style="font-family: sans-serif; max-width: 46em; line-height:1.5">
                  <h1>Servlet basics - no Spring, no framework</h1>
                  <p>Plain <code>jakarta.servlet</code> classes running inside an embedded
                     Tomcat that was started from <code>Main.java</code>.</p>

                  <h3>Demos - open each and read the matching source file</h3>
                  <ol>
                    <li><a href="/hello?name=Ravi">/hello?name=Ravi</a>
                        &mdash; <code>HelloServlet</code>: request in, response out</li>
                    <li><a href="/greet">/greet</a>
                        &mdash; <code>GreetServlet</code>: same URL, GET shows a form, POST handles it</li>
                    <li><a href="/counter">/counter</a>
                        &mdash; <code>CounterServlet</code>: one instance, many threads, and sessions</li>
                    <li><a href="/lifecycle">/lifecycle</a>
                        &mdash; <code>LifecycleServlet</code>: init / service / destroy (watch the terminal)</li>
                    <li><a href="/does-not-exist">/does-not-exist</a>
                        &mdash; how the <code>/</code> default mapping works</li>
                  </ol>

                  <h3>The whole idea in one line</h3>
                  <p>Tomcat speaks HTTP so your code doesn't have to. It parses the bytes,
                     manages the thread pool, and hands you a parsed
                     <code>HttpServletRequest</code>. You fill in an
                     <code>HttpServletResponse</code>. Everything else is detail.</p>

                  <p style="color:#666">Served by %s on thread %s</p>
                </body></html>
                """.formatted(
                        getClass().getSimpleName(),
                        Thread.currentThread().getName()));
    }
}
