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

package dev.patrickgold.florisboard.app

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * `android:localeConfig` is what puts SwiftFloris under Android Settings -> Apps -> Language on
 * Android 13+. AGP generates both the attribute and the resource it points at from the shipped
 * `values-<locale>` directories, so the failure mode is not a typo — it is the generation being
 * switched off or silently producing an empty list, which leaves the app absent from that screen
 * with nothing in the source tree looking wrong.
 *
 * This reads the generated resource out of the packaged resources and parses it the way the
 * platform does. Note that `android.app.LocaleConfig` itself cannot be used here: Robolectric
 * does not populate `ApplicationInfo.localeConfigRes`, so it reports no supported locales even
 * when the merged manifest carries the attribute correctly.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppLocaleConfigTest {
    @Test
    fun generatedLocaleConfigOffersEveryShippedTranslation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resourceId = context.resources.getIdentifier(
            "_generated_res_locale_config",
            "xml",
            context.packageName,
        )

        resourceId shouldNotBe 0

        val declared = context.resources.getXml(resourceId).use { parser ->
            buildList {
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                        parser.getAttributeValue(ANDROID_NAMESPACE, "name")?.let(::add)
                    }
                }
            }
        }

        // The unqualified `values/` resources are declared as en-US in res/resources.properties,
        // so the platform list is every shipped translation plus that fallback.
        declared shouldContainAll AppUiLocale.shippedTags
        declared.contains("en-US") shouldBe true
    }

    private inline fun <T> XmlResourceParser.use(block: (XmlResourceParser) -> T): T {
        return try {
            block(this)
        } finally {
            close()
        }
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
