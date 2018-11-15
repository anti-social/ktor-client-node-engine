import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

plugins {
    id("kotlin2js") version "1.3.0"
}

group = "company.evo"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
    maven { setUrl("https://kotlin.bintray.com/ktor") }
    maven { setUrl("https://kotlin.bintray.com/kotlinx") }
    mavenLocal()
}

dependencies {
    val ktorVersion = "1.0.0-beta-3"

    compile(kotlin("stdlib-js"))

    compile("io.ktor:ktor-client-core:$ktorVersion")
    compile("io.ktor:ktor-client-js:$ktorVersion")
    compile("com.example:kotlin-dsl-multiplatform:0.0.1")
}

tasks.withType<Kotlin2JsCompile> {
    kotlinOptions {
        moduleKind = "commonjs"
    }
}
val compileKotlin2Js by tasks.getting(Kotlin2JsCompile::class)

val assembleWeb = task<Sync>("assembleWeb") {
    configurations.compile.forEach {
        from(zipTree(it.absolutePath)) {
            includeEmptyDirs = false
            include {
                val path = it.path
                path.endsWith(".js") && (
                        path.startsWith("META-INF/resources/") ||
                                !path.startsWith("META-INF/")
                        )
            }
        }
    }
    from(compileKotlin2Js.destinationDir)
    into("$buildDir/web")

    dependsOn("classes")
}

tasks.getByName("assemble") {
    dependsOn(assembleWeb)
}
