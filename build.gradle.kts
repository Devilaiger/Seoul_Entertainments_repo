import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Properties

buildscript {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven(url = "local-repo")
        // Shitpack repo which contains our tools and dependencies
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        // Cloudstream gradle plugin which makes everything work and builds plugins
        classpath("com.github.recloudstream:gradle:master-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    }
}

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// Load secrets from local.properties if available
val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

// Helper to read secret from local.properties or system environment or fallback
fun getSecret(key: String, fallback: String = ""): String {
    return localProperties.getProperty(key)
        ?: System.getenv(key)
        ?: fallback
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // when running through github workflow, GITHUB_REPOSITORY should contain current repository name
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "user/repo")
    }

    android {
        namespace = "com.cncverse"
        (this as? com.android.build.gradle.LibraryExtension)?.buildFeatures?.buildConfig = true

        defaultConfig {
            minSdk = 21
            targetSdk = 35
            compileSdkVersion(35)

            // Inject secrets into BuildConfig
            buildConfigField("String", "MOVIEBOX_SECRET_KEY_DEFAULT", "\"${getSecret("MOVIEBOX_SECRET_KEY_DEFAULT")}\"")
            buildConfigField("String", "MOVIEBOX_SECRET_KEY_ALT", "\"${getSecret("MOVIEBOX_SECRET_KEY_ALT")}\"")
            buildConfigField("String", "CASTLE_SUFFIX", "\"${getSecret("CASTLE_SUFFIX")}\"")
            buildConfigField("String", "SIMKL_API", "\"${getSecret("SIMKL_API")}\"")
            buildConfigField("String", "MAL_API", "\"${getSecret("MAL_API")}\"")
            buildConfigField("String", "LIBRARY_PACKAGE_NAME", "\"com.cncverse\"")
            buildConfigField("String", "CRICIFY_PROVIDER_SECRET1", "\"${getSecret("CRICIFY_PROVIDER_SECRET1")}\"")
            buildConfigField("String", "CRICIFY_PROVIDER_SECRET2", "\"${getSecret("CRICIFY_PROVIDER_SECRET2")}\"")
            buildConfigField("String", "PIKASHOW_API_KEY", "\"${getSecret("PIKASHOW_API_KEY")}\"")
            buildConfigField("String", "PIKASHOW_HMAC_SECRET", "\"${getSecret("PIKASHOW_HMAC_SECRET")}\"")
            buildConfigField("String", "CRICFY_FIREBASE_API_KEY", "\"${getSecret("CRICFY_FIREBASE_API_KEY")}\"")
            buildConfigField("String", "CRICFY_FIREBASE_APP_ID", "\"${getSecret("CRICFY_FIREBASE_APP_ID")}\"")
            buildConfigField("String", "CRICFY_FIREBASE_PROJECT_NUMBER", "\"${getSecret("CRICFY_FIREBASE_PROJECT_NUMBER")}\"")
            buildConfigField("String", "SKLIVE_KEY", "\"${getSecret("SKLIVE_KEY")}\"")
            buildConfigField("String", "SKLIVE_IV", "\"${getSecret("SKLIVE_IV")}\"")
            buildConfigField("String", "SKLIVE_V23_KEY", "\"${getSecret("SKLIVE_V23_KEY")}\"")
            buildConfigField("String", "SKLIVE_V23_IV", "\"${getSecret("SKLIVE_V23_IV")}\"")
            buildConfigField("String", "SKTECH_FIREBASE_API_KEY", "\"${getSecret("SKTECH_FIREBASE_API_KEY")}\"")
            buildConfigField("String", "SKTECH_FIREBASE_APP_ID", "\"${getSecret("SKTECH_FIREBASE_APP_ID")}\"")
            buildConfigField("String", "SKTECH_FIREBASE_PROJECT_NUMBER", "\"${getSecret("SKTECH_FIREBASE_PROJECT_NUMBER")}\"")
            buildConfigField("String", "XON_FIREBASE_API_KEY", "\"${getSecret("XON_FIREBASE_API_KEY")}\"")
            buildConfigField("String", "XON_FIREBASE_APP_ID", "\"${getSecret("XON_FIREBASE_APP_ID")}\"")
            buildConfigField("String", "XON_FIREBASE_PROJECT_NUMBER", "\"${getSecret("XON_FIREBASE_PROJECT_NUMBER")}\"")
            buildConfigField("String", "CINETV_SECRET_KEY_ENCRYPTED", "\"${getSecret("CINETV_SECRET_KEY_ENCRYPTED")}\"")
            buildConfigField("String", "CINETV_DES_KEY", "\"${getSecret("CINETV_DES_KEY")}\"")
            buildConfigField("String", "CINETV_DES_IV", "\"${getSecret("CINETV_DES_IV")}\"")
            buildConfigField("String", "CINETV_AES_KEY", "\"${getSecret("CINETV_AES_KEY")}\"")
            buildConfigField("String", "CINETV_AES_IV", "\"${getSecret("CINETV_AES_IV")}\"")
            buildConfigField("String", "CINETV_WS_SECRET", "\"${getSecret("CINETV_WS_SECRET")}\"")
            buildConfigField("String", "SMARTLINK_URL", "\"${getSecret("SMARTLINK_URL")}\"")
            buildConfigField("String", "SPEEDLINK_URL", "\"${getSecret("SPEEDLINK_URL")}\"")
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17) // Required
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions"
                )
            }
        }
    }

    dependencies {
        val apk = configurations.findByName("apk") ?: configurations.create("apk")
        val implementation = configurations.getByName("implementation")
        val compileOnly = configurations.getByName("compileOnly")

        // Stubs for all Cloudstream classes resolved relative to root project libs directory
        val cloudstreamJar = File(rootProject.projectDir, "libs/cloudstream.jar")
        apk(files(cloudstreamJar))
        compileOnly(files(cloudstreamJar))

        // these dependencies can include any of those which are added by the app,
        // but you dont need to include any of them if you dont need them
        // https://github.com/recloudstream/cloudstream/blob/master/app/build.gradle
        implementation(kotlin("stdlib")) // adds standard kotlin features, like listOf, mapOf etc
        implementation("com.github.Blatzar:NiceHttp:0.4.11") // http library
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
        implementation("org.jsoup:jsoup:1.18.3") // html parser
        implementation("com.google.code.gson:gson:2.10.1")
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
