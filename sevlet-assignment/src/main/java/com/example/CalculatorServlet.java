package com.example;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Q1 - the calculation half.
 *
 * Takes two numbers and an arithmetic operator as request parameters and returns the result:
 *
 *   GET  /calculate?a=6&b=3&op=*
 *   POST /calculate     with a=6&b=3&op=* as a form-encoded body
 *
 * NOTICE WHAT IS NOT HERE: no null checks, no try/catch around the parsing, no divide-by-zero
 * guard. None of it is needed, because {@link CalculatorValidationFilter} runs first and
 * refuses to pass anything invalid down the chain. If this servlet is executing at all, then
 * a and b parse as finite numbers and op is one of + - * / - guaranteed.
 *
 * That is what a filter buys you: the servlet gets to be about arithmetic and nothing else.
 *
 * THREAD SAFETY: one instance serves all requests concurrently, so there are no fields here.
 * Per-request state lives in local variables.
 */
@WebServlet(urlPatterns = "/calculate", name = "calculatorServlet")
public class CalculatorServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // Safe to parse without guards - the filter already proved these are valid.
        double a = Double.parseDouble(request.getParameter("a").trim());
        double b = Double.parseDouble(request.getParameter("b").trim());
        char op = request.getParameter("op").charAt(0);

        double result = switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            case '/' -> a / b;
            // Unreachable: the filter rejects every other operator. Java still requires
            // the switch to be exhaustive, and a loud failure beats a silent wrong answer
            // if the filter mapping is ever removed by mistake.
            default -> throw new IllegalStateException(
                    "Operator '" + op + "' reached the servlet - is the filter still mapped?");
        };

        if (Responder.negotiate(request) == Responder.Format.JSON) {
            Responder.sendJson(response, HttpServletResponse.SC_OK, Responder.json(
                    "a", a,
                    "b", b,
                    "op", String.valueOf(op),
                    "result", result));
            return;
        }

        Responder.sendHtml(response, HttpServletResponse.SC_OK,
                Responder.page("Calculator result", """
                          <h1>Calculator</h1>
                          <p class="result">%s %s %s = %s</p>
                        %s
                          <p><a href="/">Back to the forms</a></p>
                        """.formatted(
                                format(a),
                                Responder.escapeHtml(String.valueOf(op)),
                                format(b),
                                format(result),
                                form(request.getParameter("a"),
                                     request.getParameter("b"),
                                     String.valueOf(op)))));
    }

    /**
     * POST behaves exactly like GET.
     *
     * getParameter() reads from the query string on a GET and from a form-encoded body on a
     * POST, and hides the difference from us. So both HTTP methods can share one implementation,
     * and you can test either from Postman without a code change.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        doGet(request, response);
    }

    // ---------------------------------------------------------------------------------
    // HTML rendering
    // ---------------------------------------------------------------------------------

    /**
     * Trims the pointless ".0" off whole-number results so 6 * 3 reads as "18" rather than
     * "18.0". Only affects the HTML view - the JSON keeps the raw double, because a machine
     * consumer is better served by a consistent numeric type.
     */
    private static String format(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /** A form pre-filled with the values just submitted, so you can tweak and resubmit. */
    static String form(String a, String b, String op) {
        return """
                  <form method="get" action="/calculate">
                    <label>a <input name="a" value="%s" size="8" required></label>
                    <label>
                      <select name="op">
                        <option%s>+</option>
                        <option%s>-</option>
                        <option%s>*</option>
                        <option%s>/</option>
                      </select>
                    </label>
                    <label>b <input name="b" value="%s" size="8" required></label>
                    <button type="submit">Calculate</button>
                  </form>
                """.formatted(
                        Responder.escapeHtml(a),
                        selectedIf("+".equals(op)),
                        selectedIf("-".equals(op)),
                        selectedIf("*".equals(op)),
                        selectedIf("/".equals(op)),
                        Responder.escapeHtml(b));
    }

    private static String selectedIf(boolean condition) {
        return condition ? " selected" : "";
    }
}
