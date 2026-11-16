/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * JetPref's datastore-model 0.3.0 (https://github.com/patrickgold/jetpref, tag v0.3.0,
 * Apache-2.0), built from source so one fix can ship: its event worker died for good when a
 * caller was cancelled while it waited, and no setting saved after that. Fixed in
 * runtime/DataStore.kt; 0.3.1, 0.4.0 and main still have it. The upstream multiplatform source
 * sets are merged into one Android source set, and nothing else is changed.
 *
 * jetpref-datastore-ui still comes from Maven Central, compiled against the published model,
 * so the root build substitutes this project for that module everywhere
 * and the Kotlin module name stays `datastore-model` to keep the same binary names. Drop this
 * module once a JetPref release has the fix.
 */

plugins {
    alias(libs.plugins.agp.library)
}

val projectMinSdk = providers.gradleProperty("projectMinSdk").get()
val projectCompileSdk = providers.gradleProperty("projectCompileSdk").get()

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        moduleName.set("datastore-model")
    }
}

configure<LibraryExtension> {
    namespace = "dev.patrickgold.jetpref.datastore"
    compileSdk = projectCompileSdk.toInt()

    defaultConfig {
        minSdk = projectMinSdk.toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        create("beta") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines)
}
