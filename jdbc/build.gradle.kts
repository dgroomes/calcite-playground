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
    implementation(libs.h2)
}

application {
    mainClass.set("dgroomes.JdbcRunner")
}
