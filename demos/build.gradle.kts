// ReJact demo applications (Examples gallery, Wire, CounterExample).
plugins {
    application
}

dependencies {
    implementation(project(":rejact"))
}

application {
    mainClass.set("me.chrommob.rejact.demo.Examples")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "me.chrommob.rejact.demo.Examples"
    }
}

tasks.register<JavaExec>("runCounter") {
    group = "application"
    description = "Run the counter example on port 8092"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("me.chrommob.rejact.examples.CounterExample")
}
