package com.example;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Shared rendering helper.
 *
 * The assignment says these servlets must be testable from BOTH Postman and a browser.
 * Those two clients want different things:
 *
 *   - a browser wants an HTML page it can display, ideally with a form to try again
 *   - Postman wants JSON it can show you in a structured viewer
 *
 * Rather than build two sets of endpoints, we build one and let the client say what it
 * wants. That is "content negotiation", and it is what the Accept header is for.
 *
 * Both servlets AND the validation filter render through this class, so a success and a
 * rejection come back in the same shape. That matters: a Postman user who gets HTML back
 * on errors but JSON on success has to write two parsers.
 *
 * All methods are static and the class cannot be instantiated - it holds no state, which
 * is important because servlets and filters are shared across threads.
 */
public final class Responder {

    private Responder() {
        // utility class - never instantiated
    }

    /** The two shapes a response can take. */
    public enum Format {
        HTML,
        JSON
    }

    // ---------------------------------------------------------------------------------
    // Negotiation
    // ---------------------------------------------------------------------------------

    /**
     * Decides whether this request should be answered with HTML or JSON.
     *
     * Priority order:
     *   1. an explicit ?format=json / ?format=html parameter, so you can force either shape
     *      straight from a browser address bar (very handy when demonstrating the JSON path
     *      without opening Postman)
     *   2. the Accept header - browsers send something containing "text/html"
     *   3. JSON as the default, which is where Postman's "Accept: * / *" lands
     */
    public static Format negotiate(HttpServletRequest request) {

        String override = request.getParameter("format");
        if ("json".equalsIgnoreCase(override)) {
            return Format.JSON;
        }
        if ("html".equalsIgnoreCase(override)) {
            return Format.HTML;
        }

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html")) {
            return Format.HTML;
        }

        return Format.JSON;
    }

    // ---------------------------------------------------------------------------------
    // Writing responses
    // ---------------------------------------------------------------------------------

    /**
     * Sends a JSON body with the given HTTP status code.
     *
     * setStatus() and setContentType() must both be called BEFORE anything is written to
     * the writer. Once the first bytes go out the headers are already on the wire and any
     * later change is silently ignored.
     */
    public static void sendJson(HttpServletResponse response, int status, String json)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().println(json);
    }

    /** Sends an HTML body with the given HTTP status code. */
    public static void sendHtml(HttpServletResponse response, int status, String html)
            throws IOException {
        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().println(html);
    }

    /**
     * Sends a validation failure in whichever format the client asked for.
     *
     * The filter calls this to reject bad input. The character-count servlet calls it too,
     * so every error in the application looks the same.
     *
     * @param code    a stable machine-readable identifier, e.g. INVALID_OPERATOR
     * @param message a human-readable explanation of what to fix
     */
    public static void sendError(HttpServletRequest request,
                                 HttpServletResponse response,
                                 int status,
                                 String code,
                                 String message) throws IOException {

        if (negotiate(request) == Format.JSON) {
            sendJson(response, status, json(
                    "error", code,
                    "message", message));
            return;
        }

        sendHtml(response, status, page("Invalid input", """
                  <h1 class="bad">Invalid input</h1>
                  <p class="code">%s</p>
                  <p>%s</p>
                  <p><a href=".">Back to the forms</a></p>
                """.formatted(escapeHtml(code), escapeHtml(message))));
    }

    // ---------------------------------------------------------------------------------
    // Building JSON
    // ---------------------------------------------------------------------------------

    /**
     * Builds a flat JSON object from alternating key/value arguments:
     *
     *   json("result", 18.0, "op", "*")  ->  {"result":18.0,"op":"*"}
     *
     * Values are rendered by type: Strings get quoted and escaped, Numbers and Booleans are
     * written bare, null becomes null.
     *
     * A real application would use Jackson or Gson here. Our responses are small and of a
     * fixed shape, so hand-building them keeps the project to its Tomcat dependency and
     * keeps the JSON visible rather than hidden behind a mapper.
     */
    public static String json(Object... keysAndValues) {

        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "json() needs an even number of arguments (key, value, key, value, ...)");
        }

        StringBuilder out = new StringBuilder("{");

        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            out.append('"').append(escapeJson(String.valueOf(keysAndValues[i]))).append("\":");
            out.append(jsonValue(keysAndValues[i + 1]));
        }

        return out.append('}').toString();
    }

    private static String jsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return '"' + escapeJson(String.valueOf(value)) + '"';
    }

    // ---------------------------------------------------------------------------------
    // Escaping
    // ---------------------------------------------------------------------------------

    /**
     * Escapes text for safe inclusion in a JSON string literal.
     *
     * Control characters below 0x20 are illegal raw inside JSON strings and must be sent as
     * \\u00XX escapes. Q2 echoes back whatever text you send it, so a tab or newline in the
     * input would otherwise produce a malformed response.
     */
    public static String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }

        StringBuilder out = new StringBuilder(raw.length() + 16);

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"'  -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }

        return out.toString();
    }

    /**
     * Escapes text for safe inclusion in HTML.
     *
     * Both servlets echo user input back into the page. Without this, sending
     * text=&lt;script&gt;alert(1)&lt;/script&gt; would run that script in the browser - a
     * cross-site scripting hole. Escaping turns it into visible text instead.
     */
    public static String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }

    // ---------------------------------------------------------------------------------
    // HTML page shell
    // ---------------------------------------------------------------------------------

    /**
     * Wraps a body fragment in a complete HTML document with shared styling, so every page
     * in the application looks the same and no servlet has to repeat the boilerplate.
     */
    public static String page(String title, String bodyHtml) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    body   { font-family: system-ui, sans-serif; max-width: 42rem;
                             margin: 2rem auto; padding: 0 1rem; line-height: 1.5; }
                    h1     { margin-bottom: .25rem; }
                    h1.bad { color: #b00020; }
                    .code  { font-family: ui-monospace, monospace; font-weight: 600;
                             color: #b00020; }
                    .result { font-size: 2rem; font-weight: 700; }
                    form   { margin: 1.5rem 0; padding: 1rem; border: 1px solid #ddd;
                             border-radius: .5rem; }
                    label  { display: inline-block; margin-right: .75rem; }
                    input, select, button { font: inherit; padding: .35rem .5rem; }
                    button { cursor: pointer; }
                    table  { border-collapse: collapse; margin: 1rem 0; }
                    td, th { border: 1px solid #ddd; padding: .35rem .75rem;
                             text-align: left; }
                  </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(escapeHtml(title), bodyHtml);
    }
}
