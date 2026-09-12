plugins {
    kotlin("jvm") version "2.0.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.joml:joml:1.10.4")
    implementation(kotlin("stdlib"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

sourceSets {
    main {
        kotlin {
            srcDir("../common/src/main/kotlin/org/valkyrienskies/eureka/math")
        }
        java {
            srcDir("../common/src/main/java")
            include("org/valkyrienskies/eureka/mixin/MixinModPresence.java")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
