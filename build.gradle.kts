plugins {
    id("org.springframework.boot") version "3.2.4" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    kotlin("jvm") version "1.9.24" apply false
    kotlin("plugin.spring") version "1.9.24" apply false
    kotlin("plugin.jpa") version "1.9.24" apply false
    // HU-09: analisis de calidad. La tarea "sonar" se ejecuta desde Jenkins (jenkins/vars/circleGuardQuality.groovy)
    id("org.sonarqube") version "5.1.0.4882"
}

// La version del analisis sigue a version.txt (Release Please). SonarQube compara el
// "codigo nuevo" contra el analisis de la version anterior.
val circleGuardVersion: String = rootProject.file("version.txt").takeIf { it.exists() }?.readText()?.trim() ?: "0.0.0"

sonar {
    properties {
        property("sonar.projectKey", "circleguard")
        property("sonar.projectName", "CircleGuard")
        property("sonar.projectVersion", circleGuardVersion)
        property("sonar.sourceEncoding", "UTF-8")
        // Jenkins espera el resultado con waitForQualityGate (webhook); el escaner no espera
        property("sonar.qualitygate.wait", "false")
    }
}

allprojects {
    group = "com.circleguard"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    // HU-09: cobertura por servicio para el pipeline y para SonarQube
    apply(plugin = "jacoco")
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("com.h2database:h2")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "21"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        // El reporte de cobertura se genera siempre, tambien si una prueba falla
        finalizedBy("jacocoTestReport")
    }

    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.12" // soporta clases de Java 21
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn("test")
        reports {
            xml.required.set(true)  // lo lee SonarQube
            html.required.set(true) // lo publica Jenkins
            csv.required.set(false)
        }
    }

    extensions.configure<org.sonarqube.gradle.SonarExtension> {
        properties {
            property(
                "sonar.coverage.jacoco.xmlReportPaths",
                layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile.path,
            )
        }
    }
}
