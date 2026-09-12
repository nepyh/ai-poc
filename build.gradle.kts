import java.util.Properties

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    application
}

group = "com.rooter"
version = "1.0-SNAPSHOT"

val koinVersion = "4.0.0"
val ktorVersion = "3.4.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.5.32")

    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    implementation("kr.dogfoot:hwplib:1.1.10")
    implementation("kr.dogfoot:hwpxlib:1.0.9")
}

kotlin {
    jvmToolchain(21)
}

val mainClassPath = "com.rooter.aipoc.MainKt"

application {
    mainClass.set(mainClassPath)
}

tasks.named<JavaExec>("run") {
    val envFile = File(projectDir, ".env")

    if (envFile.exists()) {
        envFile.bufferedReader().use { reader ->
            val properties = Properties()
            properties.load(reader)
            properties.forEach { (key, value) ->
                environment(key.toString(), value.toString())
            }
        }
    } else {
        logger.warn(".env 파일을 프로젝트 루트에서 찾을 수 없습니다.")
        logger.warn(".env.example을 복사해서 ANTHROPIC_API_KEY를 채워주세요.")
    }

    systemProperties(System.getProperties().mapKeys { it.key.toString() })
}
