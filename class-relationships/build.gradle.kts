plugins {
    java
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.simple)
    implementation(libs.calcite.core)
    implementation(libs.classgraph)
    implementation(libs.janino.commons.compiler)
}

application {
    mainClass.set("dgroomes.ClassRelationshipsRunner")
}
