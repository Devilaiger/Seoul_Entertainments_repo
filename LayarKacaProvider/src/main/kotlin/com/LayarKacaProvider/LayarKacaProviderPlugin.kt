package com.layarKacaProvider

import com.seoulentertainment.app.extractors.EmturbovidExtractor
import com.seoulentertainment.app.extractors.VidHidePro6
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.BasePlugin

@CloudstreamPlugin
class LayarKacaProviderPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(LayarKacaProvider())
        registerExtractorAPI(EmturbovidExtractor())
        registerExtractorAPI(Furher())
        registerExtractorAPI(Hownetwork())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(Furher2())
        registerExtractorAPI(Turbovidhls())
        registerExtractorAPI(Cloudhownetwork())
        registerExtractorAPI(Co4nxtrl())
    }
}
