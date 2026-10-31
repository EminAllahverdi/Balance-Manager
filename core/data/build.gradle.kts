plugins {
    alias(libs.plugins.bm.android.library)
    alias(libs.plugins.bm.hilt)
}

android {
    namespace = "com.eminallahverdi.balance.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:datastore"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
