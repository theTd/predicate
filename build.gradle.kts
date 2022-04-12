import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    `maven-publish`
    id("com.github.johnrengelman.shadow") version ("7.1.2")
}

group = "com.mineclay"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.mineclay.com/repository/zhuagroup/")
        credentials {
            username = project.findProperty("clayUsername").toString()
            password = project.findProperty("clayPassword").toString()
        }
    }
    maven {
        url = uri("https://papermc.io/repo/repository/maven-public/")
    }
}

dependencies {
    implementation("net.bytebuddy:byte-buddy:1.12.8")
    compileOnly("org.codehaus.groovy:groovy-all:3.0.10")
    compileOnly("org.projectlombok:lombok:1.18.22")
    annotationProcessor("org.projectlombok:lombok:1.18.22")
    compileOnly("org.spigotmc:spigot:1.12.2")
//    compileOnly("io.papermc.paper:paper-server:1.18.2-R0.1-SNAPSHOT")
//
//    testImplementation("io.papermc.paper:paper-server:1.18.2-R0.1-SNAPSHOT")
    testImplementation("org.codehaus.groovy:groovy-all:3.0.10")
    testImplementation("org.spigotmc:spigot:1.12.2")
    testImplementation("org.mockito:mockito-core:4.3.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.getByName<ProcessResources>("processResources") {
    expand("version" to project.version)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
    withJavadocJar()
    withSourcesJar()
}

tasks.withType(ShadowJar::class.java) {
    archiveClassifier.set("dist")
}

val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["shadowRuntimeElements"]) {
    skip()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])
        }
    }
    repositories {
        maven {
            val releasesRepoUrl = "https://maven.mineclay.com/repository/zhuapublic-release/"
            val snapshotsRepoUrl = "https://maven.mineclay.com/repository/zhuapublic-snapshot/"
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            credentials {
                username = project.findProperty("clayUsername").toString()
                password = project.findProperty("clayPassword").toString()
            }
        }
    }
}
