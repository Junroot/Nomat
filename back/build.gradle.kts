import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	val kotlinVersion = "1.9.10"
	id("org.springframework.boot") version "3.2.2"
	id("io.spring.dependency-management") version "1.1.4"
	id("io.gitlab.arturbosch.detekt") version "1.23.3"
	kotlin("jvm") version kotlinVersion
	kotlin("plugin.spring") version kotlinVersion
	kotlin("plugin.jpa") version kotlinVersion
}

group = "ilpak"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.mariadb.jdbc:mariadb-java-client")
	implementation("org.springframework.boot:spring-boot-testcontainers")
	implementation("org.testcontainers:mariadb")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.23")
	testImplementation("io.kotest:kotest-assertions-core:5.8.1")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs += "-Xjsr305=strict"
		jvmTarget = "17"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	testLogging {
		events = setOf(TestLogEvent.STARTED, TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.STANDARD_ERROR)
		exceptionFormat = TestExceptionFormat.FULL
	}
}

detekt {
	toolVersion = "1.23.3"
	source.setFrom("src/main/java", "src/main/kotlin")
	parallel = false
	config.setFrom("detekt/config.yml")
	buildUponDefaultConfig = true
	allRules = false
	ignoreFailures = true
	// Specify the base path for file paths in the formatted reports.
	// If not set, all file paths reported will be absolute file path.
	basePath = projectDir.parent
}

tasks.detekt.configure {
	reports {
		// Enable/Disable XML report (default: true)
		xml.required.set(true)
		xml.outputLocation.set(file("build/reports/detekt.xml"))
	}
}
