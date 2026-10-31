import com.android.build.api.dsl.ApplicationExtension
import com.eminallahverdi.balance.buildlogic.BmConfig
import com.eminallahverdi.balance.buildlogic.configureCompose
import com.eminallahverdi.balance.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            configureCompose(this)
            defaultConfig.targetSdk = BmConfig.TARGET_SDK
        }
    }
}
