plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.22.2")
    implementation("org.slf4j:slf4j-api:2.0.19")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("io.rest-assured:rest-assured:6.0.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.2")
    testImplementation("org.testng:testng:7.11.0")
    testImplementation("org.hamcrest:hamcrest:3.0")
    // The reusable code logs through SLF4J. The consuming test project chooses
    // Logback as its runtime provider and owns logback-test.xml.
    testRuntimeOnly("ch.qos.logback:logback-classic:1.6.4")
}

tasks.test {
    useTestNG {
        // The suite file supplies the normal local environment selection.
        suites("src/test/resources/testng.xml")
    }

    // A CI command such as `gradlew test -Denvironment=int` sets a property
    // on Gradle's JVM. Forward it explicitly to TestNG's forked test JVM.
    providers.systemProperty("environment").orNull?.let {
        systemProperty("environment", it)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    // Show the exact source location when a library API becomes deprecated.
    options.compilerArgs.add("-Xlint:deprecation")
}
