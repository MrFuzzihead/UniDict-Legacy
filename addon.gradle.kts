import java.io.File
import java.io.FileOutputStream

import org.gradle.api.GradleException
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

// M0: ensure the JVM unit-test task runs on the JUnit Platform (see docs/PLAN.md §0 / T1).
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// ---------------------------------------------------------------------------------------------
// Dev verify harness (docs/PLAN.md §M0, docs/TestPlan.md T3).
//
//   -PunidictDevVerify      -> runClient/runServer get -Dunidict.devVerify=true (the [unidict-verify]
//                              self-check lines; equivalent to UNIDICT_DEV_VERIFY=true).
//   ./gradlew verifyServer  -> boot the dev *dedicated server*, run the verify oracle, stop the JVM
//                              after the stage-2 checks and assert the captured console output. This is
//                              the headless half of T3: no client, no display.
//
// The verify lines go to the console appender (docs/STATUS.md), so the server run captures the process
// output itself instead of relying on run/server/logs/latest.log.
// ---------------------------------------------------------------------------------------------
val devVerifyProperty = project.hasProperty("unidictDevVerify")
val serverVerifyRequested = devVerifyProperty ||
    gradle.startParameter.taskNames.any { it.substringAfterLast(':') == "verifyServer" }
val verifyServerLog = layout.buildDirectory.file("unidict-verify-server.log")
// RFG runs the dedicated server from run/server/ and prompts for the EULA / online-mode on the console
// inside its own doFirst. A verify run has no console, so a dependency must create those files first.
val serverRunDir = layout.projectDirectory.dir("run/server").asFile

val prepareDevServer by tasks.registering {
    group = "verification"
    description = "Prepares the dev dedicated-server run directory (EULA + headless server.properties)."
    doLast {
        serverRunDir.mkdirs()

        val eula = File(serverRunDir, "eula.txt")
        if (!eula.exists()) eula.writeText("eula=true\n")

        val properties = File(serverRunDir, "server.properties")
        if (!properties.exists()) {
            properties.writeText(
                "online-mode=false\n" +
                    "level-type=FLAT\n" +
                    "generate-structures=false\n" +
                    "spawn-npcs=false\n" +
                    "spawn-animals=false\n" +
                    "spawn-monsters=false\n" +
                    "allow-nether=false\n" +
                    "view-distance=4\n")
        } else {
            // An interactive run may have left a bare "online-mode=" (no answer) — normalise that line.
            val text = properties.readText()
            val fixed = text.lineSequence()
                .joinToString("\n") { line ->
                    if (line.trimStart().startsWith("online-mode=")) "online-mode=false" else line
                }
            if (fixed != text) properties.writeText(fixed)
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    if (devVerifyProperty) systemProperty("unidict.devVerify", "true")

    if (name == "runServer" && serverVerifyRequested) {
        // Runs before RFG's own doFirst, so the EULA/online-mode prompts are skipped.
        dependsOn(prepareDevServer)
        systemProperty("unidict.devVerify", "true")
        // Dev-only: stop the JVM after the stage-2 checks so this is a single-shot assertion.
        systemProperty("unidict.verify.exitAfterStart", "true")
        val logFile = verifyServerLog.get().asFile
        doFirst {
            logFile.parentFile.mkdirs()
            logFile.writeText("")
            val out = FileOutputStream(logFile, true)
            standardOutput = out
            errorOutput = out
        }
        doLast { (standardOutput as? FileOutputStream)?.flush() }
    }
}

/** Last [lines] lines of a captured run, for assertion failures. */
fun tailOf(text: String, lines: Int = 40): String = text.lines().takeLast(lines).joinToString("\n")

tasks.register("verifyServer") {
    group = "verification"
    description = "Boots the dev dedicated server, runs the [unidict-verify] oracle and asserts the captured result."
    dependsOn("runServer")
    doLast {
        val logFile = verifyServerLog.get().asFile
        if (!logFile.exists()) throw GradleException("verifyServer: no captured server log at $logFile")
        val text = logFile.readText()
        val problems = mutableListOf<String>()

        // 1. No hard failures.
        val failLines = text.lines().filter { it.contains("[unidict-verify] FAIL ") }
        if (failLines.isNotEmpty()) problems += "FAIL lines present:\n" + failLines.take(20).joinToString("\n")

        // 2. Stage 1 (load-complete) and stage 2 (post-remap) summaries both ran and passed.
        fun checkSummary(label: String, pattern: Regex) {
            val match = pattern.find(text)
            if (match == null) {
                problems += "missing $label — the harness did not run (EULA, startup crash, or devVerify not forwarded?)"
                return
            }
            val passed = match.groupValues[1].toInt()
            val failed = match.groupValues[2].toInt()
            if (failed != 0) problems += "$label reports $failed failure(s)"
            if (passed == 0) problems += "$label reports 0 passes"
            if (failed == 0 && passed > 0) logger.lifecycle("verifyServer: $label — $passed passed, 0 failed")
        }
        checkSummary("stage-1 summary", Regex("\\[unidict-verify] summary: (\\d+) passed, (\\d+) failed"))
        checkSummary("stage-2 summary", Regex("\\[unidict-verify] stage-2 summary: (\\d+) passed, (\\d+) failed"))

        // 3. The dev-only exit hook ran (proves the run is single-shot and stage 2 completed).
        if (!text.contains("[unidict-verify] harness complete (")) {
            problems += "missing 'harness complete' line — the exit hook did not run"
        }

        // 4. The transparency report rendered a non-empty model and at least one rewrite.
        val report = Regex("\\[unidict-verify] PASS report summary=resources=(\\d+) rewrites=(\\d+)").find(text)
        if (report == null) {
            problems += "missing 'report summary=' line"
        } else {
            if (report.groupValues[1].toInt() <= 0) problems += "report summary reports resources=0"
            if (report.groupValues[2].toInt() <= 0) problems += "report summary reports rewrites=0"
        }

        // 4b. The startup config report (BUG-14) logged the resolved preset and the ignored-key set.
        if (!text.contains("[unidict] config preset=")) {
            problems += "missing '[unidict] config preset=… ignoredKeys=…' log line"
        }

        // 5. Every server-side integration actually ran. (IE is compileOnly and NEI is client-only, so
        //    neither is on the dev server runtime; NEI's hide decision is still covered by `hidden=` and T2.)
        val requiredIntegrations = listOf(
            "crafting",
            "furnace",
            "ic2",
            "ae2",
            "Chest",
            "TE",
            "EIO",
            "Railcraft",
            "Forestry",
            "TinkersConstruct",
            "Galacticraft")
        for (integration in requiredIntegrations) {
            if (!text.contains("integration=$integration ")) problems += "missing integration=$integration verify line"
        }

        // 6. The core oracle checks.
        for (check in listOf(
            "spikeA oredict-bridge",
            "kindDiagnostics",
            "lateOd ",
            "lateOdServer ",
            "resourceIndex coverage",
            "resourceIndex no-cross-wiring",
            "resourceIndex no-spurious-rewrite",
            "resourceIndex model-complete ",
            "seam=crafting/shapedRecipes",
            "seam=crafting/shapelessRecipes",
            "seam=crafting/shapedOreRecipe",
            "seam=crafting/shapelessOreRecipe",
            "seam=crafting/ic2AdvRecipe",
            "seam=crafting/ic2AdvShapelessRecipe",
            "seam=chest/chestGenHooks",
            "seam=chest/weightedRandomChestContent",
            "seam=forestry/shapedOreRecipe",
            "seam=forestry/centrifugeRecipe",
            "seam=enderio/oreDictionaryPreferences",
            "seam=railcraft/blastFurnaceCraftingManager",
            "seam=te/furnaceManager",
            "seam=te/pulverizerManager",
            "seam=te/smelterManager",
            "seam=galacticraft/shapedRecipes",
            "seam=galacticraft/shapelessOreRecipe")) {
            if (!text.contains("[unidict-verify] PASS $check")) problems += "missing PASS '$check'"
        }

        // 7. BUG-8 Phase A gate: the model is built at LOAD_COMPLETE, so the stage-1 late-name check must
        //    see a complete dictionary. Any late name here means the model was built too early again.
        val lateOd = Regex(
            "\\[unidict-verify] PASS lateOd added=(\\d+) withEntries=(\\d+) modelled=(\\d+) total=(\\d+)")
            .find(text)
        if (lateOd == null) {
            problems += "missing the stage-1 'lateOd' line"
        } else {
            val added = lateOd.groupValues[1].toInt()
            val withEntries = lateOd.groupValues[2].toInt()
            val modelled = lateOd.groupValues[3].toInt()
            if (added != 0 || withEntries != 0 || modelled != 0) {
                problems += "the model is missing $added late Ore Dictionary name(s) ($withEntries with entries, " +
                    "$modelled modelled by the config) — BUG-8 Phase A regressed (build at LOAD_COMPLETE)"
            }
            logger.lifecycle(
                "verifyServer: late-OD names added=$added withEntries=$withEntries modelled=$modelled " +
                    "(dictionary total ${lateOd.groupValues[4]})")
        }

        // 8. Post-LOAD_COMPLETE residue (registrations after the model build) + the accurate completeness
        //    check: every modelled name that has entries must be fully present in the model.
        Regex(
            "\\[unidict-verify] PASS lateOdServer added=(\\d+) withEntries=(\\d+) modelled=(\\d+) total=(\\d+)")
            .find(text)?.let {
                logger.lifecycle(
                    "verifyServer: post-load-complete OD names added=${it.groupValues[1]} " +
                        "(with entries: ${it.groupValues[2]}, modelled: ${it.groupValues[3]})")
            }
        Regex("\\[unidict-verify] PASS resourceIndex model-complete expected=(\\d+) missing=(\\d+)")
            .find(text)?.let {
                logger.lifecycle(
                    "verifyServer: model completeness — ${it.groupValues[1]} modelled names expected, " +
                        "missing=${it.groupValues[2]}")
            }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("verifyServer FAILED:\n- ").append(problems.joinToString("\n- "))
                    append("\n\n(dev config lives in run/server/config/unidict.cfg)")
                    append("\n--- log tail ---\n").append(tailOf(text))
                })
        }
        logger.lifecycle("verifyServer: PASS")
    }
}
