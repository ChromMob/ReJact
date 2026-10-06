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
