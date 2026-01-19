import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost

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

// Get version from root project's gradle.properties
val kodioVersion: String by rootProject.extra.properties.getOrElse("kodioVersion") { 
    project.findProperty("kodio.version")?.toString() ?: "0.0.1-SNAPSHOT"
}

afterEvaluate {
    configure<MavenPublishBaseExtension> {
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
        
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

