package com.example;

import org.apache.catalina.Context;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;

import java.io.File;
import java.nio.file.Files;

/**
 * Starts an embedded Tomcat so the assignment runs with a single `mvn exec:java`.
 *
 * NOTICE WHAT IS NOT HERE: no URL mappings. /hello/* lives in the @WebServlet annotation on
 * HelloServlet. This file only makes sure Tomcat looks in the right place to find it.
 */
public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {

        // ---- 1. Create the container -------------------------------------------------
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(PORT);
        tomcat.setBaseDir(Files.createTempDirectory("tomcat-work").toString());

        // Tomcat is lazy: it only creates the HTTP connector (the part that opens the TCP
        // socket and speaks HTTP) when asked. This forces it.
        tomcat.getConnector();

        // ---- 1b. Point the webapp classloader at OUR classpath ------------------------
        //
        // Tomcat gives each webapp its own classloader, but jakarta.servlet.* is never
        // loaded from the webapp - it must come from the PARENT classloader so container
        // and application share one copy of the servlet interfaces. With no parent set,
        // Tomcat falls back to the system classloader, which under `mvn exec:java` cannot
        // see our dependencies. The result is a ClassNotFoundException for a jakarta.servlet
        // class and a context that refuses to start.
        tomcat.getServer().setParentClassLoader(Main.class.getClassLoader());

        // ---- 2. Create a web application context -------------------------------------
        //
        // addWebapp(), NOT addContext(). This is the important line in the file.
        //
        // addContext()  - a bare context. Fast, but nothing scans for annotations, so
        //                 @WebServlet would be silently ignored and /hello/World would 404.
        // addWebapp()   - runs Tomcat's full webapp startup, including the ANNOTATION
        //                 SCANNER that discovers HelloServlet.
        //
        // "" as the context path serves the app at the root, so the URL reads
        // /hello/World rather than /myapp/hello/World.
        File docBase = new File("src/main/webapp").getAbsoluteFile();
        if (!docBase.isDirectory()) {
            System.err.println("Cannot find " + docBase
                    + " - run this from the project root (where pom.xml lives).");
            System.exit(1);
        }

        Context ctx = tomcat.addWebapp("", docBase.getAbsolutePath());

        // ---- 3. Tell the scanner where our compiled classes are ----------------------
        //
        // The scanner looks inside the webapp's /WEB-INF/classes. In a packaged .war our
        // classes would already be there; in this Maven layout they are in target/classes,
        // so we map that directory in as if it were /WEB-INF/classes.
        //
        // Without this the app starts cleanly and every URL returns 404, because the
        // scanner searched an empty directory and found no annotations.
        File classes = new File("target/classes").getAbsoluteFile();
        if (!classes.isDirectory()) {
            System.err.println("Cannot find " + classes + " - run `mvn compile` first.");
            System.exit(1);
        }

        WebResourceRoot resources = new StandardRoot(ctx);
        resources.addPreResources(new DirResourceSet(
                resources, "/WEB-INF/classes", classes.getAbsolutePath(), "/"));
        ctx.setResources(resources);

        // ---- 4. Start ----------------------------------------------------------------
        tomcat.start();

        // Embedded Tomcat installs no shutdown hook of its own (a standalone install does).
        // Without this, Ctrl+C kills the JVM outright and destroy() never runs.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        // tomcat.start() does NOT throw when the port is already taken - it logs a
        // BindException and carries on with a dead connector, which looks like success.
        // Check explicitly so the failure is obvious.
        if (!tomcat.getConnector().getState().isAvailable()) {
            System.err.printf("""

                    !! Could not bind to port %d - something else is already using it.
                    !! Find it with:  ss -ltnp | grep %d
                    %n""", PORT, PORT);
            System.exit(1);
        }

        System.out.printf("""

                ==========================================================
                  Hello web service running at http://localhost:%d/

                  GET /hello/World   ->  Hello "World"
                  GET /hello         ->  Hello "World"   (name defaults)

                  Press Ctrl+C to stop.
                ==========================================================
                %n""", PORT);

        // Without this main() would return. Tomcat's own threads are daemons, so the JVM
        // would exit immediately and take the server with it.
        tomcat.getServer().await();
    }
}
