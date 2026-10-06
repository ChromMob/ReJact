import java.io.File

// ReJact v2 framework. Zero third-party dependencies.
//
// Spec pipeline:
//   spec/mdn (MDN browser-compat-data) + spec/overlay.json
//     -> SpecFromMdn -> build/generated/spec/elements.json
//     -> SpecGen     -> build/generated/rejact/{java,resources}
// Runtime: rejact-core.js + generated rejact-bindings.js -> /rejact-runtime.js on the classpath.

val htmlRef = layout.projectDirectory.file("spec/mdn/html.json")
val overlayFile = layout.projectDirectory.file("spec/overlay.json")
val coreJs = layout.projectDirectory.file("src/main/resources/rejact-core.js")
val squeezePy = layout.projectDirectory.file("tools/squeeze.py")

val generatedSpec = layout.buildDirectory.file("generated/spec/elements.json")
val genOut = layout.buildDirectory.dir("generated/rejact")
val genJava = genOut.map { it.dir("java") }
val genBindings = genOut.map { it.file("resources/rejact-bindings.js") }
val specgenClasses = layout.buildDirectory.dir("classes/java/specgen")
val runtimeJs = layout.buildDirectory.file("generated/runtime/rejact-runtime.js")

val compileSpecgen = tasks.register<JavaCompile>("compileSpecgen") {
    setSource(
        listOf(
            "src/main/java/me/chrommob/rejact/Json.java",
            "src/main/java/me/chrommob/rejactgen/SpecFromMdn.java",
            "src/main/java/me/chrommob/rejactgen/SpecGen.java",
        )
    )
    classpath = files()
    destinationDirectory.set(specgenClasses)
    options.encoding = "UTF-8"
    options.release.set(21)
}

val generateSpec = tasks.register<JavaExec>("generateSpec") {
    group = "build"
    description = "Generate the element/event spec from MDN browser-compat-data + overlay"
    dependsOn(compileSpecgen)
    classpath = files(specgenClasses)
    mainClass.set("me.chrommob.rejactgen.SpecFromMdn")
    inputs.file(htmlRef)
    inputs.file(overlayFile)
    outputs.file(generatedSpec)
    args(
        htmlRef.asFile.absolutePath,
        overlayFile.asFile.absolutePath,
        generatedSpec.get().asFile.absolutePath,
    )
}

val generateRejact = tasks.register<JavaExec>("generateRejact") {
    group = "build"
    description = "Generate typed tags/events and JS bindings from the MDN-derived spec"
    dependsOn(generateSpec)
    classpath = files(specgenClasses)
    mainClass.set("me.chrommob.rejactgen.SpecGen")
    inputs.file(generatedSpec)
    outputs.dir(genOut)
    args(
        generatedSpec.get().asFile.absolutePath,
        genOut.get().asFile.absolutePath,
    )
}

val assembleRuntime = tasks.register("assembleRuntime") {
    group = "build"
    description = "Assemble the fixed client runtime JS (core + generated bindings)"
    dependsOn(generateRejact)
    val out = runtimeJs
    val bindings = genBindings
    inputs.file(coreJs)
    inputs.file(bindings)
    outputs.file(out)
    doLast {
        val raw = coreJs.asFile.readText(Charsets.UTF_8) +
            "\n" +
            bindings.get().asFile.readText(Charsets.UTF_8)
        val pathDirs = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        val esbuild = System.getenv("ESBUILD")?.let(::File)
            ?: (pathDirs.map { File(it, "esbuild") } +
                File(System.getProperty("user.home"), ".hermes/hermes-agent/node_modules/.bin/esbuild"))
                .firstOrNull { it.canExecute() }

        val outFile = out.get().asFile
        outFile.parentFile.mkdirs()

        fun compact(cmd: List<String>): Boolean {
            val tmp = File.createTempFile("rejact-runtime", ".js")
            return try {
                tmp.writeText(raw)
                ProcessBuilder(cmd)
                    .redirectInput(tmp)
                    .redirectOutput(outFile)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
                    .waitFor() == 0 && outFile.length() > 0L
            } catch (_: java.io.IOException) {
                false
            } finally {
                tmp.delete()
            }
        }

        val ok = (esbuild != null && compact(listOf(esbuild.absolutePath, "--minify", "--loader=js"))) ||
            compact(listOf("npx", "--yes", "esbuild", "--minify", "--loader=js")) ||
            compact(listOf("python3", squeezePy.asFile.absolutePath))
        if (!ok) outFile.writeText(raw)
        logger.lifecycle("rejact-runtime.js ${outFile.length()} bytes")
    }
}

sourceSets {
    main {
        java.srcDir(genJava)
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateRejact)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(assembleRuntime)
    from(runtimeJs)
    // rejact-core.js is a build input only; the shipped artifact is the assembled runtime.
    exclude("rejact-core.js")
}
