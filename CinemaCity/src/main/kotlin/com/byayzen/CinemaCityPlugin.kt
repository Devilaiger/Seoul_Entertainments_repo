// ! Bu araç @ByAyzen tarafından | @kekikanime için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.Plugin

@CloudstreamPlugin
class CinemaCityPlugin: Plugin() {
    override fun load() {
        registerMainAPI(CinemaCity())
    }
}