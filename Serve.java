// Copyright (c) 2026 John Blanton. All rights reserved. See LICENSE.
//
// Launcher: run from the project folder with   java Serve.java   (Java 21+)
// Compiles src/main/java into build/classes when needed, then starts the app.
// Options are passed through:  --port 8080   --verify   --no-open

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class Serve {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path src = root.resolve("src/main/java");
        Path out = root.resolve("build/classes");
        if (!Files.isDirectory(src)) {
            System.err.println("Run this from the MicroCodeDrills project folder (src/main/java not found).");
            System.exit(2);
        }
        compileIfStale(src, out);
        // The loader must stay open: the server keeps loading classes after main() returns.
        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()});
        Class<?> main = loader.loadClass("com.microcodedrills.Main");
        main.getMethod("main", String[].class).invoke(null, (Object) args);
    }

    private static void compileIfStale(Path src, Path out) throws IOException {
        List<Path> sources;
        try (Stream<Path> s = Files.walk(src)) {
            sources = s.filter(p -> p.toString().endsWith(".java")).toList();
        }
        long newest = 0;
        for (Path p : sources) newest = Math.max(newest, Files.getLastModifiedTime(p).toMillis());
        Path stamp = out.resolve(".stamp");
        if (Files.exists(stamp) && Files.getLastModifiedTime(stamp).toMillis() >= newest) return;

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            System.err.println("A JDK is required (this Java has no compiler). Install JDK 21 or newer.");
            System.exit(2);
        }
        Files.createDirectories(out);
        String[] args = Stream.concat(Stream.of("-d", out.toString(), "--release", "21"),
                sources.stream().map(Path::toString)).toArray(String[]::new);
        if (javac.run(null, null, null, args) != 0) System.exit(1);
        Files.writeString(stamp, "ok");
    }
}
