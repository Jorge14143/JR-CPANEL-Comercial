import java.util.Properties

val releaseSigning = Properties().apply {
 val file = rootProject.file("release-signing.properties")
 if (file.exists()) file.inputStream().use(::load)
}

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace="ar.com.jrelectricidad.jrserver"; compileSdk=35
 defaultConfig { applicationId="ar.com.jrelectricidad.jrserver"; minSdk=26; targetSdk=35; versionCode=2; versionName="1.0.1" }
 signingConfigs {
  if (releaseSigning.isNotEmpty()) {
   create("release") {
    storeFile=file(releaseSigning.getProperty("storeFile"))
    storePassword=releaseSigning.getProperty("storePassword")
    keyAlias=releaseSigning.getProperty("keyAlias")
    keyPassword=releaseSigning.getProperty("keyPassword")
   }
  }
 }
 buildTypes {
  getByName("release") {
   isMinifyEnabled=false
   isDebuggable=false
   if (releaseSigning.isNotEmpty()) signingConfig=signingConfigs.getByName("release")
  }
 }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
 buildFeatures { compose=true; buildConfig=true }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.biometric:biometric:1.1.0")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
 implementation("androidx.work:work-runtime-ktx:2.10.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
