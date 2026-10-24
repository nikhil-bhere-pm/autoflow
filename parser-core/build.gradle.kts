plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.pennywiseai"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

sourceSets {
    main {
        kotlin.setSrcDirs(listOf("src/main/kotlin", "src/commonMain/kotlin"))
        resources.setSrcDirs(listOf("src/main/resources"))
    }
    test {
        kotlin.setSrcDirs(listOf("src/test/kotlin"))
        resources.setSrcDirs(listOf("src/test/resources"))
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    filter {
        excludeTestsMatching("*DocTest*")
    }

    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }

    maxParallelForks = maxOf(1, Runtime.getRuntime().availableProcessors() / 2)
}