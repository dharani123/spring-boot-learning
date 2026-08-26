package com.example;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Q1 - the validation half.
 *
 * A FILTER sits in front of a servlet. Every request to a URL the filter is mapped to goes
 * through the filter first, and the filter decides what happens next:
 *
 *   chain.doFilter(req, resp)  -> pass it along; the servlet runs
 *   write a response instead   -> the servlet NEVER runs
 *
 * That second option is the whole point here. This filter owns every rule about what counts
 * as valid input, which is why {@link CalculatorServlet} contains no error handling at all.
 * By the time the servlet executes, bad input is impossible - it was stopped out here.
 *
 * DIFFERENCE 4 of 6 from the embedded project: no @WebFilter annotation here. The mapping
 * moved to WEB-INF/web.xml, where <filter-mapping> also fixes the ORDER filters run in -
 * something annotations cannot express. With one filter that hardly matters; with several
 * it is the main reason teams still use the descriptor.
 *
 * THREAD SAFETY: the container creates ONE instance of this filter and runs every concurrent
 * request through it. So this class holds no mutable fields. Everything lives in local
 * variables, which are per-call and therefore per-thread.
 */
public class CalculatorValidationFilter implements Filter {

    /** The only operators we accept. Anything else is rejected. */
    private static final String VALID_OPERATORS = "+-*/";

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {

        // The Filter interface is protocol-neutral, so we get the generic Servlet types and
        // cast up to the HTTP ones. Anything reaching this filter arrived over HTTP.
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Must happen BEFORE the first getParameter() call or it has no effect: the container
        // caches the parsed parameters the first time you ask for one, encoding and all.
        request.setCharacterEncoding("UTF-8");

        String rawA = request.getParameter("a");
        String rawB = request.getParameter("b");
        String rawOp = request.getParameter("op");

        // ---- Rule 1: both numbers must be present -----------------------------------
        if (isBlank(rawA) || isBlank(rawB)) {
            reject(request, response, "MISSING_PARAMETER",
                    "Both 'a' and 'b' are required. Example: /calculate?a=6&b=3&op=*");
            return;
        }

        // ---- Rule 2: both numbers must actually parse as numbers --------------------
        Double a = parseNumber(rawA);
        Double b = parseNumber(rawB);

        if (a == null || b == null) {
            String offender = (a == null) ? rawA : rawB;
            reject(request, response, "NOT_A_NUMBER",
                    "'" + offender + "' is not a valid number.");
            return;
        }

        // ---- Rule 3: the operator must be one we support ----------------------------
        // Checked BEFORE the divide-by-zero rule below. Otherwise a request like
        // ?a=1&b=0&op=% would complain about a zero divisor, which is confusing when the
        // real problem is that '%' is not an operator we handle.
        if (isBlank(rawOp) || rawOp.length() != 1 || VALID_OPERATORS.indexOf(rawOp.charAt(0)) < 0) {
            reject(request, response, "INVALID_OPERATOR",
                    "'op' is required and must be one of + - * /"
                            + (isBlank(rawOp) ? "" : " (got '" + rawOp + "')"));
            return;
        }

        // ---- Rule 4: no dividing by zero --------------------------------------------
        // This lives in the filter, not the servlet, because it is a question about whether
        // the INPUT is acceptable. Keeping it here is what lets the servlet be pure arithmetic.
        if (rawOp.charAt(0) == '/' && b == 0.0) {
            reject(request, response, "DIVISION_BY_ZERO",
                    "Cannot divide by zero: 'b' must not be 0 when 'op' is /");
            return;
        }

        // Everything checked out. Hand the request on to CalculatorServlet.
        chain.doFilter(request, response);
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    /**
     * Writes a 400 Bad Request and returns WITHOUT calling chain.doFilter().
     * That omission is what stops the servlet from running.
     */
    private void reject(HttpServletRequest request,
                        HttpServletResponse response,
                        String code,
                        String message) throws IOException {
        Responder.sendError(request, response,
                HttpServletResponse.SC_BAD_REQUEST, code, message);
    }

    /**
     * Parses a number, returning null instead of throwing when the text is not one.
     *
     * Non-finite values are rejected too. Double.parseDouble happily accepts the literal
     * strings "NaN" and "Infinity", and neither is a number a calculator should work with.
     */
    private static Double parseNumber(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
