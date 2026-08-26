package com.example;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * DEMO 2 - GET vs POST in the same servlet.
 *
 * Try: http://localhost:8080/greet
 *
 * The SAME URL behaves differently depending on the HTTP method:
 *   GET  /greet  -> show the form
 *   POST /greet  -> process the form
 *
 * That is service() doing its job: it inspects req.getMethod() and dispatches
 * to doGet or doPost for you.
 *
 * Notice that req.getParameter() reads from the query string for GET and from the
 * form-encoded request BODY for POST. Same method, different source. The container
 * hides that difference from you.
 */
public class GreetServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.getWriter().println("""
                <!doctype html>
                <html><body style="font-family: sans-serif">
                  <h1>Tell me your name</h1>
                  <form method="post" action="/greet">
                    <input name="name" placeholder="your name" autofocus>
                    <input name="language" value="en" size="4">
                    <button type="submit">Submit (POST)</button>
                  </form>
                  <p>The form posts back to this same URL. doPost below handles it.</p>
                  <p><a href="/">back</a></p>
                </body></html>
                """);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        // For a POST body, tell the container how to decode the bytes.
        // Must be called BEFORE the first getParameter() call, or it has no effect.
        req.setCharacterEncoding("UTF-8");

        String name = req.getParameter("name");
        String language = req.getParameter("language");

        String greeting = switch (language == null ? "en" : language) {
            case "hi" -> "Namaste";
            case "fr" -> "Bonjour";
            case "es" -> "Hola";
            default   -> "Hello";
        };

        resp.setContentType("text/html;charset=UTF-8");
        resp.getWriter().println("""
                <!doctype html>
                <html><body style="font-family: sans-serif">
                  <h1>%s, %s!</h1>
                  <p>Method was <code>%s</code>, content type <code>%s</code>.</p>
                  <p>Try language codes: en, hi, fr, es</p>
                  <p><a href="/greet">again</a> | <a href="/">back</a></p>
                </body></html>
                """.formatted(
                        greeting,
                        HelloServlet.escape(name == null ? "stranger" : name),
                        req.getMethod(),
                        req.getContentType()));
    }
}
