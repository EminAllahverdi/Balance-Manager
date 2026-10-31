import com.eminallahverdi.balance.buildlogic.BmConfig
import com.eminallahverdi.balance.buildlogic.configureKotlin
import com.eminallahverdi.balance.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Pure Kotlin modules (no Android): fast to build and unit-test. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = BmConfig.JAVA
            targetCompatibility = BmConfig.JAVA
        }
        configureKotlin()
        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("truth").get())
        }
    }
}
