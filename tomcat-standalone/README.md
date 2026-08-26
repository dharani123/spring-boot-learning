# Standalone Tomcat — the same assignment, deployed the traditional way

The companion to `../sevlet-assignment/`. **Same Java logic**, but instead of your program
starting a server, a server that already exists starts your program.

| | `../sevlet-assignment` (embedded) | this project (standalone WAR) |
|---|---|---|
| Who starts whom | your `main()` starts Tomcat | Tomcat starts your app |
| Build output | a jar of classes | `servlet-assignment.war` |
| `<packaging>` | `jar` | `war` |
| Servlet API | `tomcat-embed-core`, compile scope | `jakarta.servlet-api`, **`provided`** |
| Mappings | `@WebServlet` / `@WebFilter` | `WEB-INF/web.xml` |
| Run it | `mvn exec:java` | `mvn package`, copy, `startup.sh` |
| Port | 8080 | **8081** (so both can run at once) |
| URL | `/calculate` | `/servlet-assignment/calculate` |

## Layout

```
tomcat-standalone/
├── apache-tomcat-10.1.57/          a real Tomcat install
│   ├── bin/      startup.sh, shutdown.sh, catalina.sh
│   ├── conf/     server.xml  <- port changed to 8081 here
│   ├── webapps/  <- WARs go here; Tomcat auto-deploys them
│   ├── logs/     catalina.out
│   └── lib/      the servlet API lives here (this is what `provided` means)
└── webapp/                          your application source
    ├── pom.xml
    └── src/main/
        ├── java/com/example/        4 classes, NO Main.java
        └── webapp/
            ├── index.html
            └── WEB-INF/web.xml      all mappings
```

## Run it

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

cd webapp && mvn clean package && cd ..                     # build the WAR
cp webapp/target/servlet-assignment.war apache-tomcat-10.1.57/webapps/
apache-tomcat-10.1.57/bin/startup.sh                        # start
```

Open <http://localhost:8081/servlet-assignment/>.

```bash
apache-tomcat-10.1.57/bin/shutdown.sh                       # stop
tail -f apache-tomcat-10.1.57/logs/catalina.out             # watch the log
```

`startup.sh` returns immediately — it launches Tomcat in the background and that's all. It
prints "Tomcat started" whether or not your app deployed successfully. **The log is the only
place the truth lives.** Get used to reading `catalina.out`; there is no console to watch.

## The redeploy loop

```bash
cd webapp && mvn package && cd ..
cp webapp/target/servlet-assignment.war apache-tomcat-10.1.57/webapps/
```

No restart needed. Tomcat watches `webapps/` and redeploys within a few seconds of the file
changing. Watch it happen in `catalina.out`.

## Six things that are genuinely different

### 1. `<packaging>war</packaging>`

`mvn package` now produces a zip in a layout the container understands, not a runnable jar.
Look inside it:

```bash
unzip -l webapp/target/servlet-assignment.war
```

```
index.html
WEB-INF/web.xml
WEB-INF/classes/com/example/*.class
```

**There is no `WEB-INF/lib/`.** That directory holds bundled dependencies, and this WAR has
none — which is point 2.

### 2. `<scope>provided</scope>` — the one that bites people

The WAR is 16 KB. The embedded project's dependencies are megabytes. The difference is that
here the servlet API is *not packaged*: it already exists in `apache-tomcat-10.1.57/lib/`.

`provided` means "compile against this, but the runtime supplies it, so leave it out."

Drop that scope and the WAR ships its own copy of `jakarta.servlet.*`. Tomcat then has two
copies loaded by two different classloaders, and the JVM treats them as unrelated types. The
error is a `ClassCastException` claiming `HttpServlet` cannot be cast to `HttpServlet`, which
looks insane until you know why.

### 3. No `Main.java`

Deleted, not moved. Nothing in this project starts a server, opens a port, or configures a
connector — `conf/server.xml` does that now, outside your application entirely.

This is also why the WAR is portable. Nothing here imports `org.apache.catalina`. Drop this
same WAR into Jetty or WildFly and it runs.

### 4. `WEB-INF/web.xml` instead of annotations

Every `@WebServlet` and `@WebFilter` is gone from the Java. The mapping is now data, editable
without recompiling.

The descriptor also does something annotations cannot: **it fixes filter order.** When several
filters match a URL, they run in the order their `<filter-mapping>` elements appear. With
annotations the order is unspecified. That is the main reason large projects still use it.

Note that `WEB-INF` is unreachable over HTTP — the container blocks it absolutely. That is why
config and classes live there and `index.html` does not.

### 5. The context path

The WAR's **filename** becomes the URL prefix. `servlet-assignment.war` → `/servlet-assignment`.
Rename the file and every URL moves. The special name `ROOT.war` means no prefix.

This is why every link and form in this version uses a **relative** path:

```html
<form action="calculate">     <!-- correct: resolves inside the context -->
<form action="/calculate">    <!-- wrong: escapes to localhost:8081/calculate, 404 -->
```

Hardcoded absolute paths are the single most common thing that breaks when an app moves from
embedded to standalone. Relative paths work under any prefix.

### 6. `conf/server.xml`

Server-wide settings now live in XML outside your app. The only edit made to this install is
the `<Connector>` port, 8080 → 8081, and it is commented in place. `server.xml.original` is
the untouched file if you want to diff it.

## Prove they're equivalent

With the embedded project running on 8080 and this one on 8081:

```bash
curl "http://localhost:8080/calculate?a=6&b=3&op=*"
curl "http://localhost:8081/servlet-assignment/calculate?a=6&b=3&op=*"
```

Both return `{"a":6.0,"b":3.0,"op":"*","result":18.0}`, byte for byte. Verified across all
39 checks: same operators, same four validation rules, same JSON and HTML negotiation, same
POST handling, same unicode counting.

## Testing from Postman

Identical to the embedded version, with the longer base URL:

```
http://localhost:8081/servlet-assignment/calculate?a=6&b=3&op=*
http://localhost:8081/servlet-assignment/count?text=hello
```

Remember to URL-encode `+` as `%2B` and `/` as `%2F`.

## Tomcat's own bundled apps

A stock install ships with examples worth a look, all on 8081:

- <http://localhost:8081/> — Tomcat's landing page (`webapps/ROOT`)
- <http://localhost:8081/examples/servlets/> — official servlet examples with source
- <http://localhost:8081/docs/> — the full Tomcat manual, offline

Delete those directories on a real server. They are for learning, not production.
