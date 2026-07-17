package com.Toonstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.seoulentertainment.app.app
import com.seoulentertainment.app.extractors.EmturbovidExtractor
import com.seoulentertainment.app.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.extractors.Vidmolyme

@CloudstreamPlugin
class ToonstreamProvider: BasePlugin() {
    override fun load() {
        registerMainAPI(Toonstream())
        registerExtractorAPI(StreamSB8())
        registerExtractorAPI(Vidmolyme())
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(D000d())
        registerExtractorAPI(vidhidevip())
        registerExtractorAPI(Cdnwish())
        registerExtractorAPI(FileMoonnl())
        registerExtractorAPI(Cloudy())
        registerExtractorAPI(GDMirrorbot())
        registerExtractorAPI(Techinmind())
        registerExtractorAPI(EmturbovidExtractor())
        registerExtractorAPI(Zephyrflick())
    }


    companion object {
        private const val DOMAINS_URL =
            "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
        var cachedDomains: Domains? = null

        suspend fun getDomains(forceRefresh: Boolean = false): Domains? {
            if (cachedDomains == null || forceRefresh) {
                try {
                    cachedDomains = app.get(DOMAINS_URL).parsedSafe<Domains>()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }
            return cachedDomains
        }

        data class Domains(
            @param:JsonProperty("toonstream")
            val Toonstream: String,
        )
    }
}