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

import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.agp.application)
}

val projectCompileSdk: String by project
val projectMinSdk: String by project
val projectTargetSdk: String by project

configure<ApplicationExtension> {
    namespace = "io.github.sysadmindoc.swiftfloris.addons.dictionarypack.sample"
    compileSdk = projectCompileSdk.toInt()
    buildToolsVersion = tools.versions.buildTools.get()

    defaultConfig {
        applicationId = "io.github.sysadmindoc.swiftfloris.addons.dictionarypack.sample"
        minSdk = projectMinSdk.toInt()
        targetSdk = projectTargetSdk.toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
