import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

// M0: ensure the JVM unit-test task runs on the JUnit Platform (see docs/PLAN.md §0 / T1).
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// M0: dev verify harness (docs/PLAN.md §M0) — forward -PunidictDevVerify to the client JVM as
// -Dunidict.devVerify=true so `runClient` logs [unidict-verify] self-check lines.
// (Equivalent: set env var UNIDICT_DEV_VERIFY=true before running.)
tasks.withType<JavaExec>().configureEach {
    if (project.hasProperty("unidictDevVerify")) {
        systemProperty("unidict.devVerify", "true")
    }
}
