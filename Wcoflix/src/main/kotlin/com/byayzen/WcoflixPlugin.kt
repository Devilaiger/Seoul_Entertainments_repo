// ! Bu araç @ByAyzen tarafından | @cs-kraptor için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.BasePlugin

@CloudstreamPlugin
class WcoflixPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Wcoflix())
        registerExtractorAPI(WcoStreamExtractor())
    }
}