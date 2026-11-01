plugins {
    alias(libs.plugins.bm.android.library)
    alias(libs.plugins.bm.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.eminallahverdi.balance.importer.moneymanager"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
