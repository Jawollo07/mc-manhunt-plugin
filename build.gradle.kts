plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    maven(url = "https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    implementation("com.zaxxer:HikariCP:4.0.3")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("io.papermc.paper:paper-api:26.3.build.+")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.test {
    useJUnitPlatform()
}

tasks {
  runServer {
    // Configure the Minecraft version for our task.
    // This is the only required configuration besides applying the plugin.
    // Your plugin's jar (or shadowJar if present) will be used automatically.
    minecraftVersion("1.21.8")
  }
}