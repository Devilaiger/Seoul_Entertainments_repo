// ! Bu araç @ByAyzen tarafından | @kekikanime için yazılmıştır.
package com.byayzen

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.seoulentertainment.app.plugins.Plugin

@CloudstreamPlugin
class SubspleasePlugin: Plugin() {
    override fun load() {
        registerMainAPI(Subsplease())
    }
}