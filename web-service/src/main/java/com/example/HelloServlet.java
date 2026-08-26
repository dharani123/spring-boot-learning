package com.example;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * GET /hello/{name}  ->  Hello "name"
 *
 * THE IMPORTANT PART IS THE '/*' IN THE MAPPING.
 *
 * "/hello" matches exactly one URL. "/hello/*" is a PREFIX mapping: it matches /hello and
 * everything beneath it. That is what lets a path carry data. Tomcat then splits the URL
 * into two halves and hands us both:
 *
 *     GET /hello/World
 *       getServletPath()  ->  "/hello"   the part that matched the mapping
 *       getPathInfo()     ->  "/World"   everything after it, already URL-DECODED
 *
 * getPathInfo() is the path parameter. A REST framework such as JAX-RS would declare
 * @Path("/hello/{name}") and inject the value as a @PathParam - this class does that job
 * by hand, which is the whole point of the exercise.
 */
@WebServlet("/hello/*")
public class HelloServlet extends HttpServlet {

    /** Used when the URL carries no name at all: /hello and /hello/ both greet the world. */
    static final String DEFAULT_NAME = "World";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        String name = extractName(req.getPathInfo());

        // text/plain, not text/html: the assignment's output contains literal quote
        // characters, and in plain text a quote is just a quote - nothing to escape.
        //
        // The charset is set explicitly. Without it the response falls back to a platform
        // default that may not be UTF-8, and /hello/Jose with an accent comes back mangled.
        resp.setContentType("text/plain;charset=UTF-8");

        resp.getWriter().write("Hello \"" + name + "\"");
    }

    /**
     * The name is the first non-empty segment of the path info; if there is none, DEFAULT_NAME.
     *
     * One rule, rather than a branch per case, covers everything the container can hand us:
     *
     *     "/World"  ->  "World"
     *     "/"       ->  "World"   no segment
     *     null      ->  "World"   what /hello with no trailing slash gives us
     *     "/ "      ->  " "       a blank name is still a name (from /hello/%20)
     *     "/a/b"    ->  "a"       first segment wins, the rest is ignored
     *
     * Note the null case: pathInfo is null - not "" - for a request to /hello itself.
     * Forgetting that is the classic NullPointerException in a prefix-mapped servlet.
     *
     * Kept static and free of servlet types so it can be unit-tested without a container.
     */
    static String extractName(String pathInfo) {
        if (pathInfo == null) {
            return DEFAULT_NAME;
        }
        // split() drops trailing empty strings, so "/" yields an empty array rather than [""].
        // The leading empty string (from the path's own leading slash) is skipped by the loop.
        for (String segment : pathInfo.split("/")) {
            if (!segment.isEmpty()) {
                return segment;
            }
        }
        return DEFAULT_NAME;
    }
}
