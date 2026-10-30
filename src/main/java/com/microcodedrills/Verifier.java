// Copyright (c) 2026 John Blanton. All rights reserved. See LICENSE.
package com.microcodedrills;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** --verify: every drill's answer must pass and an empty blank must not. Exit code 1 on any problem. */
final class Verifier {
    static int run(List<Pack> packs) throws Exception {
        Grader grader = new Grader();
        ExecutorService ex = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
        int problems = 0;
        for (Pack p : packs) {
            List<Future<String>> fs = new ArrayList<>();
            for (Pack.Drill d : p.drills()) {
                fs.add(ex.submit(() -> {
                    // mustContain is skipped for the empty check by construction (blank is rejected earlier), so splice directly.
                    Grader.Result good = grader.grade(p, d, d.answer());
                    if (!good.correct()) return p.id() + "/" + d.id() + ": answer does not pass: " + good.message();
                    Grader.Result bad = grader.grade(p, d, "// nothing");
                    if (bad.correct()) return p.id() + "/" + d.id() + ": blank still passes";
                    return null;
                }));
            }
            int n = 0;
            for (Future<String> f : fs) {
                String msg = f.get();
                if (msg != null) { problems++; System.out.println("PROBLEM " + msg); }
                else n++;
            }
            System.out.println(p.id() + ": " + n + "/" + p.drills().size() + " ok");
        }
        ex.shutdown();
        return problems == 0 ? 0 : 1;
    }
}
