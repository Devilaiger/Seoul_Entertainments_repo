// ! Bu araç @ByAyzen tarafından | @CS-Karma için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.BasePlugin

@CloudstreamPlugin
class FilmmirasimPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Filmmirasim())
    }
}