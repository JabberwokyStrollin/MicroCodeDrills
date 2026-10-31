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
            List<Topic> topics, List<Drill> drills, Path dir) {

    record Topic(String id, String title) {}

    record Drill(String id, String topic, String prompt, List<String> given, String template, String answer,
                 String why, String mustContain) {}

    @SuppressWarnings("unchecked")
    static Pack load(Path dir) throws IOException {
        Map<String, Object> m = Json.object(Files.readString(dir.resolve("pack.json")));
        List<Topic> topics = new ArrayList<>();
        for (Object o : (List<Object>) m.get("topics")) {
            Map<String, Object> s = (Map<String, Object>) o;
            topics.add(new Topic((String) s.get("id"), (String) s.get("title")));
        }
        List<Drill> drills = new ArrayList<>();
        Path dd = dir.resolve("drills");
        List<Path> files;
        try (Stream<Path> w = Files.walk(dd)) {
            files = w.filter(p -> p.toString().endsWith(".json")).sorted(Comparator.comparing(Path::toString)).toList();
        }
        for (Path f : files) {
            Map<String, Object> d = Json.object(Files.readString(f));
            if (d.containsKey("retired")) continue; // placeholder left where an old drill file was removed
            drills.add(new Drill((String) d.get("id"), (String) d.get("topic"), (String) d.get("prompt"), strings(d.get("given")),
                    (String) d.get("template"), (String) d.get("answer"), (String) d.get("why"),
                    (String) d.get("mustContain")));
        }
        drills.sort(Comparator.comparing(Drill::id));
        return new Pack((String) m.get("id"), (String) m.get("title"), (String) m.get("language"),
                (String) m.get("sourceFile"), (String) m.get("blankMarker"),
                strings(m.get("compile")), strings(m.get("run")),
                ((Number) m.getOrDefault("timeoutSeconds", 15.0)).intValue(),
                (String) m.getOrDefault("passToken", "PASS"), topics, drills, dir);
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
