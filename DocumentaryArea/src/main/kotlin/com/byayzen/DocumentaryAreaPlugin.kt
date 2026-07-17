// ! Bu araç @Kraptor123 tarafından | @kekikanime için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.Plugin

@CloudstreamPlugin
class DocumentaryAreaPlugin: Plugin() {
    override fun load() {
        registerMainAPI(DocumentaryArea())
    }
}