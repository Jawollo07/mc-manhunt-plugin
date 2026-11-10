plugins {
  java
}

repositories {
  maven(url = "https://repo.papermc.io/repository/maven-public/") {
    name = "papermc"
  }
}

dependencies {
  compileOnly("io.papermc.paper:paper-api:26.3.build.+")
  implementation("com.zaxxer:HikariCP:4.0.3")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}