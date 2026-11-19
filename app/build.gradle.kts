/*
 * Copyright 2026 JuLogic 404
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

// ---------------------------------------------------------------------------
// Assinatura do release
// ---------------------------------------------------------------------------
// As credenciais NUNCA ficam no repositorio. Ordem de leitura:
//   1. Variaveis de ambiente (usadas no GitHub Actions: nada de passwords em disco)
//        KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
//   2. keystore.properties na raiz do projeto (uso local, esta no .gitignore)
// Sem credenciais, o release usa a assinatura de debug para o projeto compilar num
// clone novo (APK nao publicavel). Com -PexigirAssinaturaRelease=true falha em vez disso.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

fun credencial(variavelAmbiente: String, propriedade: String): String? =
    System.getenv(variavelAmbiente)?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(propriedade)

val caminhoKeystore = credencial("KEYSTORE_PATH", "storeFile")
val ficheiroKeystore = caminhoKeystore?.let { rootProject.file(it) }
val hasReleaseKeystore = ficheiroKeystore?.exists() == true

val exigirAssinatura = providers.gradleProperty("exigirAssinaturaRelease").orNull == "true"
if (exigirAssinatura && !hasReleaseKeystore) {
    throw GradleException(
        "Assinatura de release exigida mas o keystore nao foi encontrado. " +
            "Defina KEYSTORE_PATH/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD ou keystore.properties " +
            "(ver docs/ASSINATURA.md)."
    )
}

// Versao: o CI passa -PversaoNome / -PversaoCodigo a partir da tag (ex.: v4.1.0).
val versaoNome = providers.gradleProperty("versaoNome").orNull ?: "4.0.2"
val versaoCodigo = providers.gradleProperty("versaoCodigo").orNull?.toInt() ?: 6

android {
    namespace = "ao.julogic404.kubixapics"
    compileSdk = 36

    defaultConfig {
        applicationId = "ao.julogic404.kubixapics"
        minSdk = 24
        targetSdk = 35
        versionCode = versaoCodigo
        versionName = versaoNome

        vectorDrawables.useSupportLibrary = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = ficheiroKeystore
                storePassword = credencial("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = credencial("KEY_ALIAS", "keyAlias")
                keyPassword = credencial("KEY_PASSWORD", "keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                // Fallback: permite `assembleRelease` sem keystore (APK nao publicavel).
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    // Apenas pt/en: reduz o APK (substitui resourceConfigurations, descontinuado).
    androidResources {
        localeFilters += listOf("pt", "en")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "DebugProbesKt.bin")
    }
}

dependencies {
    // Modulos internos
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:convert"))
    implementation(project(":feature:resize"))
    implementation(project(":feature:crop"))
    implementation(project(":feature:compress"))

    // AndroidX / Material
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
