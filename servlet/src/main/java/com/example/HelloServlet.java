package com.example;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * DEMO 1 - the smallest useful servlet.
 *
 * Try:
 *   http://localhost:8080/hello
 *   http://localhost:8080/hello?name=Ravi
 *
 * What Tomcat did before calling this method:
 *   - accepted a TCP connection
 *   - read the raw bytes:  GET /hello?name=Ravi HTTP/1.1 ...
 *   - parsed the request line, headers and query string
 *   - picked a thread from its pool
 *   - looked up which servlet is mapped to /hello
 *   - called service(), which saw method=GET and dispatched here
 *
 * You write only the last step.
 */
public class HelloServlet extends HttpServlet {

    /**
     * doGet handles HTTP GET. There are siblings for the other verbs:
     * doPost, doPut, doDelete, doHead, doOptions, doTrace.
     * If you don't override one, the default implementation returns 405 Method Not Allowed.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        // Read a query parameter. Returns null if absent - always guard for that.
        String name = req.getParameter("name");
        if (name == null || name.isBlank()) {
            name = "World";
        }

        // Set the status and headers BEFORE writing the body.
        // Once bytes go to the writer, headers are committed and can no longer change.
        resp.setStatus(HttpServletResponse.SC_OK);          // 200 (this is the default anyway)
        resp.setContentType("text/html");
        resp.setCharacterEncoding("UTF-8");

        // getWriter() is for text. For binary (images, PDFs) use getOutputStream().
        // You may call one or the other, never both on the same response.
        resp.getWriter().println("""
                <!doctype html>
                <html><body style="font-family: sans-serif">
                  <h1>Hello, %s!</h1>
                  <p>You asked for: <code>%s %s</code></p>
                  <p>Your query string was: <code>%s</code></p>
                  <p><a href="/">back</a></p>
                </body></html>
                """.formatted(
                        escape(name),
                        req.getMethod(),           // "GET"
                        req.getRequestURI(),       // "/hello"
                        req.getQueryString()));    // "name=Ravi" or null
    }

    /** Never write user input into HTML unescaped - that is an XSS hole. */
    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
