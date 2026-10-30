// Copyright (c) 2026 John Blanton. All rights reserved. See LICENSE.
package com.microcodedrills;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** Localhost-only HTTP server: JSON API for packs and drills, plus the static web UI. */
final class Server {
    private final Map<String, Pack> packs = new LinkedHashMap<>();
    private final Path webDir;
    private final Grader grader = new Grader();
    private HttpServer http;
    private int port;

    Server(List<Pack> loaded, Path webDir) {
        for (Pack p : loaded) packs.put(p.id(), p);
        this.webDir = webDir;
    }

    int start(int requestedPort) throws IOException {
        http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), requestedPort), 0);
        port = http.getAddress().getPort();
        http.setExecutor(Executors.newCachedThreadPool());
        http.createContext("/api/", this::api);
        http.createContext("/", this::staticFile);
        http.start();
        return port;
    }

    // ---- API ----

    private void api(HttpExchange x) throws IOException {
        try {
            if (!hostOk(x)) { send(x, 403, "text/plain", "forbidden"); return; }
            String path = x.getRequestURI().getPath();
            String method = x.getRequestMethod();
            if (method.equals("GET") && path.equals("/api/packs")) {
                List<Object> out = new ArrayList<>();
                for (Pack p : packs.values()) out.add(packSummary(p));
                json(x, 200, out);
            } else if (method.equals("GET") && path.equals("/api/drills")) {
                Pack p = packs.get(query(x, "pack"));
                if (p == null) { json(x, 404, err("unknown pack")); return; }
                List<Object> out = new ArrayList<>();
                for (Pack.Drill d : p.drills()) out.add(Map.of("id", d.id(), "section", d.section()));
                json(x, 200, out);
            } else if (method.equals("GET") && path.equals("/api/drill")) {
                Pack p = packs.get(query(x, "pack"));
                Pack.Drill d = p == null ? null : p.drill(query(x, "id"));
                if (d == null) { json(x, 404, err("unknown drill")); return; }
                json(x, 200, drillView(p, d));
            } else if (method.equals("POST") && path.equals("/api/submit")) {
                Map<String, Object> body = Json.object(new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                Pack p = packs.get((String) body.get("pack"));
                Pack.Drill d = p == null ? null : p.drill((String) body.get("id"));
                if (d == null) { json(x, 404, err("unknown drill")); return; }
                Grader.Result r = grader.grade(p, d, (String) body.get("answer"));
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("correct", r.correct());
                out.put("message", r.message());
                out.put("expected", d.answer());
                out.put("why", d.why());
                json(x, 200, out);
            } else {
                json(x, 404, err("not found"));
            }
        } catch (IllegalArgumentException e) {
            json(x, 400, err("bad request: " + e.getMessage()));
        } catch (Throwable e) {
            json(x, 500, err("server error"));
        }
    }

    private static Map<String, Object> packSummary(Pack p) {
        List<Object> secs = new ArrayList<>();
        for (Pack.Section s : p.sections()) {
            long count = p.drills().stream().filter(d -> d.section().equals(s.id())).count();
            secs.add(Map.of("id", s.id(), "title", s.title(), "count", count));
        }
        return Map.of("id", p.id(), "title", p.title(), "language", p.language(),
                "count", p.drills().size(), "sections", secs);
    }

    /** The template is split around the blank so the UI can show code before and after the editor. */
    private static Map<String, Object> drillView(Pack p, Pack.Drill d) {
        String t = d.template();
        int at = t.indexOf(p.blankMarker());
        int lineStart = t.lastIndexOf('\n', at) + 1;
        int lineEnd = t.indexOf('\n', at);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.id());
        m.put("section", d.section());
        m.put("prompt", d.prompt());
        m.put("language", p.language());
        m.put("before", t.substring(0, lineStart));
        m.put("indent", t.substring(lineStart, at));
        m.put("after", lineEnd < 0 ? "" : t.substring(lineEnd + 1));
        return m;
    }

    // ---- static files ----

    private void staticFile(HttpExchange x) throws IOException {
        if (!hostOk(x)) { send(x, 403, "text/plain", "forbidden"); return; }
        String path = x.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path f = webDir.resolve(path.substring(1)).normalize();
        if (!f.startsWith(webDir) || !Files.isRegularFile(f)) { send(x, 404, "text/plain", "not found"); return; }
        String n = f.getFileName().toString();
        String type = n.endsWith(".html") ? "text/html; charset=utf-8" : n.endsWith(".css") ? "text/css; charset=utf-8"
                : n.endsWith(".js") ? "text/javascript; charset=utf-8" : "application/octet-stream";
        send(x, 200, type, Files.readAllBytes(f));
    }

    // ---- helpers ----

    /** Reject requests whose Host header is not this server on loopback (blocks DNS rebinding). */
    private boolean hostOk(HttpExchange x) {
        String h = x.getRequestHeaders().getFirst("Host");
        return h != null && (h.equals("localhost:" + port) || h.equals("127.0.0.1:" + port));
    }

    private static String query(HttpExchange x, String key) {
        String q = x.getRequestURI().getQuery();
        if (q == null) return null;
        for (String kv : q.split("&")) {
            int e = kv.indexOf('=');
            if (e > 0 && kv.substring(0, e).equals(key)) {
                return java.net.URLDecoder.decode(kv.substring(e + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static Map<String, Object> err(String m) { return Map.of("error", m); }

    private static void json(HttpExchange x, int code, Object body) throws IOException {
        send(x, code, "application/json; charset=utf-8", Json.write(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange x, int code, String type, String body) throws IOException {
        send(x, code, type, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange x, int code, String type, byte[] body) throws IOException {
        x.getResponseHeaders().set("Content-Type", type);
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(code, body.length);
        x.getResponseBody().write(body);
        x.close();
    }
}
