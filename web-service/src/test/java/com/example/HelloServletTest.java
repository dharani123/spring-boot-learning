package com.example;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.example.HelloServlet.extractName;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * extractName() is static and takes a plain String, so the parsing rule can be tested
 * without starting a container, binding a port or sending a request.
 *
 * The values here are what Tomcat actually passes to getPathInfo() - already URL-decoded,
 * and null (not "") when the request is /hello with no trailing slash.
 */
class HelloServletTest {

    @Test
    @DisplayName("/hello/World -> World")
    void normalName() {
        assertEquals("World", extractName("/World"));
    }

    @Test
    @DisplayName("/hello -> pathInfo is null, name defaults")
    void nullPathInfo() {
        assertEquals("World", extractName(null));
    }

    @Test
    @DisplayName("/hello/ -> trailing slash only, name defaults")
    void trailingSlashOnly() {
        assertEquals("World", extractName("/"));
    }

    @Test
    @DisplayName("empty path info -> name defaults")
    void emptyPathInfo() {
        assertEquals("World", extractName(""));
    }

    @Test
    @DisplayName("/hello/%20 -> a blank name is still a name")
    void blankNameIsKept() {
        assertEquals(" ", extractName("/ "));
    }

    @Test
    @DisplayName("/hello/a/b -> first segment wins")
    void firstSegmentWins() {
        assertEquals("a", extractName("/a/b"));
    }

    @Test
    @DisplayName("repeated slashes are skipped")
    void repeatedSlashes() {
        assertEquals("a", extractName("///a"));
    }

    @Test
    @DisplayName("non-ASCII names survive")
    void nonAscii() {
        assertEquals("José", extractName("/José"));
    }
}
