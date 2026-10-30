// Copyright (c) 2026 John Blanton. All rights reserved. See LICENSE.
package com.microcodedrills;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Entry point. Options: --port N (default 8765), --verify, --no-open. */
public final class Main {
    public static void main(String[] args) throws Exception {
        int port = 8765;
        boolean verify = false, open = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--verify" -> verify = true;
                case "--no-open" -> open = false;
                default -> { System.err.println("Unknown option: " + args[i]); System.exit(2); }
            }
        }
        Path root = Path.of("").toAbsolutePath();
        List<Pack> packs = loadPacks(root.resolve("packs"));
        if (packs.isEmpty()) { System.err.println("No packs found in " + root.resolve("packs")); System.exit(2); }
        if (verify) System.exit(Verifier.run(packs));

        Server server = new Server(packs, root.resolve("web").toAbsolutePath().normalize());
        int bound = server.start(port);
        String url = "http://localhost:" + bound + "/";
        System.out.println("MicroCodeDrills running at " + url + "  (Ctrl+C to stop)");
        if (open && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try { Desktop.getDesktop().browse(URI.create(url)); } catch (Exception ignored) { }
        }
    }

    private static List<Pack> loadPacks(Path dir) throws Exception {
        List<Pack> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        List<Path> dirs;
        try (Stream<Path> s = Files.list(dir)) {
            dirs = s.filter(p -> Files.exists(p.resolve("pack.json"))).sorted().toList();
        }
        for (Path p : dirs) out.add(Pack.load(p));
        return out;
    }
}
