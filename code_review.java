

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class GenericWebServer {

    private static final String DEFAULT_USER = "user";
    private static final String DEFAULT_KEY = "key123";

    private static final Random rnd = new Random();

    public static void main(String[] args) throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress(8080), 0);
        srv.createContext("/auth", new AuthHandler());
        srv.createContext("/submit", new SubmitHandler());
        srv.createContext("/records", new RecordListHandler());
        srv.createContext("/file", new FileFetchHandler());
        srv.createContext("/blob", new BlobHandler());
        srv.setExecutor(null);
        System.out.println("Server running at http://localhost:8080");
        srv.start();
    }

    static class AuthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    Map<String, String> data = readForm(ex.getRequestBody());
                    String u = data.getOrDefault("u", "");
                    String p = data.getOrDefault("p", "");

                    if (DEFAULT_USER.equals(u) && DEFAULT_KEY.equals(p)) {
                        String tok = "S" + rnd.nextInt(999999);
                        ex.getResponseHeaders().add("Set-Cookie", "TK=" + tok);
                        respond(ex, 200, "Authenticated token=" + tok);
                    } else {
                        respond(ex, 401, "Bad credentials");
                    }
                    return;
                }

                String html = "<html><body>"
                        + "<form method='POST' action='/auth'>"
                        + "User:<input name='u'><br>"
                        + "Key:<input name='p' type='password'><br>"
                        + "<input type='submit'></form>"
                        + "</body></html>";
                respond(ex, 200, html);
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                respond(ex, 500, sw.toString());
            }
        }
    }

    static class SubmitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                respond(ex, 405, "POST only");
                return;
            }

            Map<String, String> data = readForm(ex.getRequestBody());
            String a = data.getOrDefault("a", "note");
            String b = data.getOrDefault("b", "");
            String c = data.getOrDefault("c", "unknown");

            String html = "<html><body>"
                    + "<h2>Submission: " + a + "</h2>"
                    + "<p>Author: " + c + "</p>"
                    + "<p>Data: " + b + "</p>"
                    + "</body></html>";

            Path dir = Paths.get("./store");
            Files.createDirectories(dir);
            Path out = dir.resolve(a + ".txt");
            Files.write(out, html.getBytes());

            respond(ex, 201, html);
        }
    }

    static class RecordListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            Path dir = Paths.get("./store");
            if (!Files.exists(dir)) {
                respond(ex, 200, "No data yet");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("<html><body><h3>Entries</h3><ul>");
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.txt")) {
                for (Path p : ds) {
                    String nm = p.getFileName().toString();
                    sb.append("<li><a href='/file?f=" + nm + "'>" + nm + "</a></li>");
                }
            }
            sb.append("</ul></body></html>");
            respond(ex, 200, sb.toString());
        }
    }

    static class FileFetchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String q = ex.getRequestURI().getQuery();
            String f = getParam(q, "f");
            if (f == null) {
                respond(ex, 400, "Missing");
                return;
            }

            Path base = Paths.get("./store");
            Path t = base.resolve(f).normalize();

            if (!Files.exists(t)) {
                respond(ex, 404, "NF");
                return;
            }

            byte[] b = Files.readAllBytes(t);
            ex.getResponseHeaders().add("Content-Type", "text/plain");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        }
    }

    static class BlobHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                respond(ex, 405, "POST only");
                return;
            }
            try (ObjectInputStream ois = new ObjectInputStream(ex.getRequestBody())) {
                Object o = ois.readObject();
                respond(ex, 200, "Received object: " + o.getClass().getName());
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                respond(ex, 500, sw.toString());
            }
        }
    }

    private static void respond(HttpExchange ex, int c, String msg) throws IOException {
        byte[] d = msg.getBytes();
        ex.sendResponseHeaders(c, d.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(d);
        }
    }

    private static Map<String, String> readForm(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int r;
        while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
        String body = new String(baos.toByteArray(), "UTF-8");
        Map<String, String> map = new HashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            String k = URLDecoder.decode(kv[0], "UTF-8");
            String v = kv.length > 1 ? URLDecoder.decode(kv[1], "UTF-8") : "";
            map.put(k, v);
        }
        return map;
    }

    private static String getParam(String q, String k) {
        if (q == null) return null;
        for (String pair : q.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(k)) return kv.length > 1 ? kv[1] : "";
        }
        return null;
    }
}
