package com.example;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import java.io.File;
import java.nio.file.Files;

/**
 * Starts an embedded Tomcat and wires up our servlets.
 *
 * In a "real" deployment you would NOT write this class. You would package a .war
 * file and drop it into an installed Tomcat, and the URL mappings below would come
 * from either @WebServlet annotations or a WEB-INF/web.xml file.
 *
 * We do it in code here so that everything is visible in one place: you can literally
 * read which URL maps to which class.
 */
public class Main {

    public static void main(String[] args) throws Exception {

        int port = 8080;

        // ---- 1. Create the container -------------------------------------------------
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.setBaseDir(Files.createTempDirectory("tomcat-work").toString());

        // Tomcat is lazy: it only creates the HTTP connector (the thing that opens the
        // TCP socket and speaks HTTP) when you ask for it. This call forces it.
        tomcat.getConnector();

        // ---- 2. Create a web application context -------------------------------------
        // "" means the app is served at the root, so URLs are /hello rather than /myapp/hello.
        Context ctx = tomcat.addContext("", new File(".").getAbsolutePath());

        // ---- 3. Register servlets and map them to URLs -------------------------------
        // addServlet(context, internalName, instance) then map internalName -> url pattern.
        // NOTE: we pass an *instance*. Tomcat keeps that ONE instance and runs every
        // request through it, on many threads at once. That is the key servlet fact.
        register(ctx, "home",      "/",          new HomeServlet());
        register(ctx, "hello",     "/hello",     new HelloServlet());
        register(ctx, "greet",     "/greet",     new GreetServlet());
        register(ctx, "counter",   "/counter",   new CounterServlet());
        register(ctx, "lifecycle", "/lifecycle", new LifecycleServlet());

        // ---- 4. Start and block ------------------------------------------------------
        tomcat.start();

        // Embedded Tomcat does NOT install a shutdown hook for you (a standalone Tomcat
        // install does). Without this, Ctrl+C kills the JVM outright and destroy() never
        // runs - so you would never see the end of the lifecycle.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tomcat.stop();      // stops connectors, then calls destroy() on each servlet
                tomcat.destroy();   // releases the container itself
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        // tomcat.start() does NOT throw if the port was already taken - it logs a
        // BindException and carries on with a dead connector, which looks like success.
        // Check explicitly so the failure is obvious.
        if (!tomcat.getConnector().getState().isAvailable()) {
            System.err.println("""

                    !! Could not bind to port %d - something else is already using it.
                    !! Find it with:  ss -ltnp | grep %d
                    """.formatted(port, port));
            System.exit(1);
        }

        System.out.println("""

                ==========================================================
                  Servlet demo running:  http://localhost:%d/
                  Press Ctrl+C to stop.
                ==========================================================
                """.formatted(port));

        // Without this the main thread would end. Tomcat's threads are daemons.
        tomcat.getServer().await();
    }

    private static void register(Context ctx, String name, String urlPattern,
                                 jakarta.servlet.Servlet servlet) {
        Tomcat.addServlet(ctx, name, servlet);
        ctx.addServletMappingDecoded(urlPattern, name);
    }
}
