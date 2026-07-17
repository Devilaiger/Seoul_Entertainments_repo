// ! Bu araç @byayzen tarafından | @CS-Karma için yazılmıştır.
package com.byayzen

import com.seoulentertainment.app.extractors.OkRuSSL
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.Plugin

@CloudstreamPlugin
class DoramasLatinoXPlugin: Plugin() {
    override fun load() {
        registerMainAPI(DoramasLatinoX())
        registerExtractorAPI(DoramasLatinoXExtractor())
        registerExtractorAPI(OkRuSSL())
    }
}