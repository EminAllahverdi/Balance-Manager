plugins {
    alias(libs.plugins.bm.android.feature)
}

android {
    namespace = "com.eminallahverdi.balance.feature.receipts"
}

dependencies {
    // Both run in Google Play services, free and without a key: the scanner needs no camera permission, and text is read on the phone.
    implementation(libs.play.services.code.scanner)
    implementation(libs.play.services.text.recognition)
    implementation(libs.androidx.activity.compose)
}
