package com.phisher98

import com.seoulentertainment.app.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class OnePacePlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(OnepaceProvider())
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(Vidmolynet())
        registerExtractorAPI(GDMirrorbot())
        registerExtractorAPI(Cdnwish())
        registerExtractorAPI(Dhcplay())
        registerExtractorAPI(vidcloudupns())
        registerExtractorAPI(Animedekhoco())
        registerExtractorAPI(MyAnimeworld())
        registerExtractorAPI(AWSStream())
        registerExtractorAPI(ascdn21())
    }
}
