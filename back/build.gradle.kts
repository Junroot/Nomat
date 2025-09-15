import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	val kotlinVersion = "1.9.10"
	id("org.springframework.boot") version "3.4.8"
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
	implementation("mysql:mysql-connector-java:8.0.33")
	implementation("org.springframework.boot:spring-boot-testcontainers")
	implementation("org.testcontainers:mysql")
	implementation("org.testcontainers:elasticsearch")
	implementation("org.testcontainers:kafka:1.21.3")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.data:spring-data-elasticsearch")
	implementation("org.springframework.retry:spring-retry")
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	implementation("net.logstash.logback:logstash-logback-encoder:7.4")
	implementation("ch.qos.logback.access:logback-access-common:2.0.6")
	implementation("ch.qos.logback.access:logback-access-tomcat:2.0.6")
	implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
	implementation("io.debezium:debezium-api:3.2.0.Final")
	implementation("io.debezium:debezium-embedded:3.2.0.Final")
	implementation("io.debezium:debezium-connector-mysql:3.2.0.Final")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
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

tasks.named("check").configure {
	this.setDependsOn(this.dependsOn.filterNot {
		it is TaskProvider<*> && it.name == "detekt"
	})
}
