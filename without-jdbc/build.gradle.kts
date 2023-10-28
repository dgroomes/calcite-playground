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
}

application {
    mainClass.set("dgroomes.WithoutJdbcRunner")
}
