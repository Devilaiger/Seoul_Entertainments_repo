package com.Pinoymoviepedia

import com.seoulentertainment.app.extractors.Upstream
import com.seoulentertainment.app.extractors.VidHidePro3
import com.seoulentertainment.app.extractors.Voe
import com.seoulentertainment.app.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class PinoymoviepediaProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Pinoymoviepedia())
        registerMainAPI(Bluray())
        registerExtractorAPI(Ds2play())
        registerExtractorAPI(Upstream())
        registerExtractorAPI(Vidsp())
        registerExtractorAPI(VidHidePro3())
        registerExtractorAPI(VidHideplus())
        registerExtractorAPI(Voe())
        registerExtractorAPI(MixDropAg())
        registerExtractorAPI(Luluvdostore())
    }
}
