// Copyright (c) 2026 John Blanton. All rights reserved. See LICENSE.
package com.microcodedrills;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Splices an answer into a drill template, compiles and runs it in a child process, and grades the output. */
final class Grader {
    record Result(boolean correct, String message) {}

    private static final int MAX_OUTPUT = 4000;

    /** Replace the marker line with the answer, indenting every answer line to match the marker. */
    static String splice(String template, String marker, String answer) {
        StringBuilder out = new StringBuilder();
        for (String line : template.split("\n", -1)) {
            int at = line.indexOf(marker);
            if (at < 0) { out.append(line).append('\n'); continue; }
            String indent = line.substring(0, at);
            for (String a : answer.stripTrailing().split("\n", -1)) {
                out.append(a.isBlank() ? "" : indent + a).append('\n');
            }
        }
        return out.toString();
    }

    Result grade(Pack pack, Pack.Drill drill, String answer) {
        if (answer == null || answer.isBlank()) return new Result(false, "Nothing was written.");
        String must = drill.mustContain();
        if (must != null && !normalize(answer).contains(normalize(must))) {
            return new Result(false, "Your code may work, but this drill asks for a specific form. Re-read the prompt: it names the variant to write.");
        }
        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("mcd-");
            Path file = tmp.resolve(pack.sourceFile());
            Files.writeString(file, splice(drill.template(), pack.blankMarker(), answer));
            if (!pack.compile().isEmpty()) {
                Proc c = exec(expand(pack.compile(), pack, file, tmp), tmp, 30);
                if (c.exit != 0) return new Result(false, "Compile error:\n" + tidy(c.output, tmp));
            }
            Proc r = exec(expand(pack.run(), pack, file, tmp), tmp, pack.timeoutSeconds());
            if (r.timedOut) return new Result(false, "Timed out after " + pack.timeoutSeconds() + " seconds (infinite loop?).");
            if (r.output.strip().equals(pack.passToken())) return new Result(true, "");
            return new Result(false, tidy(r.output, tmp));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Result(false, "Could not run the drill: " + e.getMessage());
        } finally {
            if (tmp != null) deleteQuietly(tmp);
        }
    }

    private static String normalize(String s) { return s.replaceAll("\\s+", ""); }

    private static List<String> expand(List<String> cmd, Pack pack, Path file, Path tmp) {
        return cmd.stream().map(a -> a.replace("{file}", file.toString())
                .replace("{outDir}", tmp.toString())
                .replace("{packDir}", pack.dir().toString())).toList();
    }

    private record Proc(int exit, String output, boolean timedOut) {}

    private static Proc exec(List<String> cmd, Path dir, int timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        Process p = pb.start();
        p.getOutputStream().close();
        StringBuilder buf = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (InputStream in = p.getInputStream()) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) {
                    synchronized (buf) { if (buf.length() < MAX_OUTPUT) buf.append(new String(b, 0, n, StandardCharsets.UTF_8)); }
                }
            } catch (IOException ignored) { }
        });
        reader.setDaemon(true);
        reader.start();
        boolean done = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!done) { p.descendants().forEach(ProcessHandle::destroyForcibly); p.destroyForcibly(); p.waitFor(); }
        reader.join(1000);
        synchronized (buf) {
            String o = buf.length() > MAX_OUTPUT ? buf.substring(0, MAX_OUTPUT) : buf.toString();
            return new Proc(done ? p.exitValue() : -1, o, !done);
        }
    }

    /** Remove temp paths and tool noise so messages stay short and readable. */
    private static String tidy(String out, Path tmp) {
        StringBuilder b = new StringBuilder();
        for (String l : out.replace(tmp.toString() + "/", "").split("\n")) {
            if (l.startsWith("Picked up ")) continue;
            b.append(l).append('\n');
        }
        String s = b.toString().strip();
        return s.length() > 1500 ? s.substring(0, 1500) + "\n..." : s;
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> w = Files.walk(dir)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) { }
    }
}
