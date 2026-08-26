package com.example;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Q2 - takes a string and returns the number of characters in it.
 *
 *   GET  /count?text=hello world
 *   POST /count     with text=hello world as a form-encoded body
 *
 * No filter here. Q1 asked for one; Q2 did not, so this servlet does its own single check
 * (is 'text' present?) inline. Compare the top of doGet with {@link CalculatorServlet#doGet},
 * which has no checks at all - that difference is exactly what the filter in Q1 removed.
 *
 * THREAD SAFETY: one instance, many concurrent requests, therefore no fields.
 */
public class CharacterCountServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // Before the first getParameter(), or non-ASCII input decodes wrongly and the count
        // comes out wrong - which for a servlet whose entire job is counting would be fatal.
        request.setCharacterEncoding("UTF-8");

        String text = request.getParameter("text");

        if (text == null) {
            Responder.sendError(request, response, HttpServletResponse.SC_BAD_REQUEST,
                    "MISSING_PARAMETER",
                    "'text' is required. Example: /count?text=hello");
            return;
        }

        // An empty string is a legitimate input, not an error - its length is simply 0.
        // Only a completely absent parameter is rejected above.
        int count = text.length();

        if (Responder.negotiate(request) == Responder.Format.JSON) {
            Responder.sendJson(response, HttpServletResponse.SC_OK, Responder.json(
                    "text", text,
                    "count", count));
            return;
        }

        Responder.sendHtml(response, HttpServletResponse.SC_OK,
                Responder.page("Character count", """
                          <h1>Character count</h1>
                          <p class="result">%d</p>
                          <p>characters in &ldquo;%s&rdquo;</p>
                        %s
                          <p><a href=".">Back to the forms</a></p>
                        """.formatted(count, Responder.escapeHtml(text), form(text))));
    }

    /** POST behaves exactly like GET - see the note in {@link CalculatorServlet#doPost}. */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        doGet(request, response);
    }

    /** A form pre-filled with the text just submitted. */
    static String form(String text) {
        return """
                  <form method="get" action="count">
                    <label>text
                      <input name="text" value="%s" size="30" autofocus>
                    </label>
                    <button type="submit">Count</button>
                  </form>
                """.formatted(Responder.escapeHtml(text));
    }
}
