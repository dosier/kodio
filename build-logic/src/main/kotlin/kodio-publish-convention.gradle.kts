/**
 * Convention plugin for configuring Maven publishing across all Kodio modules.
 * 
 * Usage in module build.gradle.kts:
 * ```
 * plugins {
 *     id("kodio-publish-convention")
 * }
 * ```
 * 
 * Then configure the artifact name:
 * ```
 * kodioPublishing {
 *     artifactId = "transcription"
 *     description = "Audio transcription extension for Kodio"
 * }
 * ```
 */

plugins {
    id("com.vanniktech.maven.publish")
}

// Extension for module-specific configuration
interface KodioPublishingExtension {
    val artifactId: Property<String>
    val description: Property<String>
}

val extension = extensions.create<KodioPublishingExtension>("kodioPublishing")

afterEvaluate {
    // Get version from gradle.properties
    val kodioVersion = project.findProperty("kodio.version")?.toString() ?: "0.0.1-SNAPSHOT"
    
    mavenPublishing {
        // Uses Central Portal by default (SonatypeHost was removed in 0.34.0)
        publishToMavenCentral()
        
        signAllPublications()
        
        coordinates(
            groupId = project.group.toString(),
            artifactId = extension.artifactId.get(),
            version = kodioVersion
        )
        
        pom {
            name.set("Kodio ${extension.artifactId.get().replaceFirstChar { it.uppercase() }}")
            description.set(extension.description.get())
            inceptionYear.set("2025")
            url.set("https://github.com/dosier/kodio")
            
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            
            developers {
                developer {
                    id.set("dosier")
                    name.set("Stan")
                    url.set("https://github.com/dosier")
                }
            }
            
            scm {
                url.set("https://github.com/dosier/kodio")
                connection.set("scm:git:git://github.com/dosier/kodio.git")
                developerConnection.set("scm:git:ssh://git@github.com/dosier/kodio.git")
            }
        }
    }
}

