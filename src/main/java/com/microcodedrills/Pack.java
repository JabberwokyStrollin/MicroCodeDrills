// Copyright (c) 2026 John Blanton. All rights reserved. See LICENSE.
package com.microcodedrills;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** A question set loaded from packs/<id>/. */
record Pack(String id, String title, String language, String sourceFile, String blankMarker,
            List<String> compile, List<String> run, int timeoutSeconds, String passToken,
            List<Section> sections, List<Drill> drills, Path dir) {

    record Section(String id, String title) {}

    record Drill(String id, String section, String prompt, String template, String answer,
                 String why, String mustContain) {}

    @SuppressWarnings("unchecked")
    static Pack load(Path dir) throws IOException {
        Map<String, Object> m = Json.object(Files.readString(dir.resolve("pack.json")));
        List<Section> sections = new ArrayList<>();
        for (Object o : (List<Object>) m.get("sections")) {
            Map<String, Object> s = (Map<String, Object>) o;
            sections.add(new Section((String) s.get("id"), (String) s.get("title")));
        }
        List<Drill> drills = new ArrayList<>();
        Path dd = dir.resolve("drills");
        List<Path> files;
        try (Stream<Path> w = Files.walk(dd)) {
            files = w.filter(p -> p.toString().endsWith(".json")).sorted(Comparator.comparing(Path::toString)).toList();
        }
        for (Path f : files) {
            Map<String, Object> d = Json.object(Files.readString(f));
            drills.add(new Drill((String) d.get("id"), (String) d.get("section"), (String) d.get("prompt"),
                    (String) d.get("template"), (String) d.get("answer"), (String) d.get("why"),
                    (String) d.get("mustContain")));
        }
        drills.sort(Comparator.comparing(Drill::id));
        return new Pack((String) m.get("id"), (String) m.get("title"), (String) m.get("language"),
                (String) m.get("sourceFile"), (String) m.get("blankMarker"),
                strings(m.get("compile")), strings(m.get("run")),
                ((Number) m.getOrDefault("timeoutSeconds", 15.0)).intValue(),
                (String) m.getOrDefault("passToken", "PASS"), sections, drills, dir);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object o) {
        return o == null ? List.of() : (List<String>) (List<?>) o;
    }

    Drill drill(String id) {
        for (Drill d : drills) if (d.id().equals(id)) return d;
        return null;
    }
}
