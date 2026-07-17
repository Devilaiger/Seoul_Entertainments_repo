// ! Bu araç @ByAyzen tarafından | @CS-Karma için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.Plugin

@CloudstreamPlugin
class TokybookPlugin: Plugin() {
    override fun load() {
        registerMainAPI(Tokybook())
        registerExtractorAPI(TokybookExtractor())
    }
}