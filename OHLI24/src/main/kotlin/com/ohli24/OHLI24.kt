package com.ohli24

import com.seoulentertainment.app.DubStatus
import com.seoulentertainment.app.HomePageList
import com.seoulentertainment.app.HomePageResponse
import com.seoulentertainment.app.LoadResponse
import com.seoulentertainment.app.MainAPI
import com.seoulentertainment.app.MainPageRequest
import com.seoulentertainment.app.Score
import com.seoulentertainment.app.SearchResponse
import com.seoulentertainment.app.SubtitleFile
import com.seoulentertainment.app.TvType
import com.seoulentertainment.app.addEpisodes
import com.seoulentertainment.app.app
import com.seoulentertainment.app.fixUrl
import com.seoulentertainment.app.fixUrlNull
import com.seoulentertainment.app.mainPageOf
import com.seoulentertainment.app.newAnimeLoadResponse
import com.seoulentertainment.app.newEpisode
import com.seoulentertainment.app.newHomePageResponse
import com.seoulentertainment.app.newMovieLoadResponse
import com.seoulentertainment.app.newMovieSearchResponse
import com.seoulentertainment.app.utils.ExtractorLink
import com.seoulentertainment.app.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class OHLI24 : MainAPI() {
    override var mainUrl = "https://ani.ohli24.com"
    override var name = "OHLI24"
    override val hasMainPage = true
    override var lang = "ko"
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime,TvType.AnimeMovie)

    override val mainPage = mainPageOf(
        "bbs/board.php?bo_table=ing" to "방영중",
        "bbs/board.php?bo_table=fin" to "종영",
        "bbs/board.php?bo_table=theater" to "극장판"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.post("$mainUrl/${request.data}&page=$page", timeout = 100).document

        val home = document.select("div.list-row").mapNotNull {
                it.toSearchResult()
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }


    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.post-title")?.text() ?: return null
        val rawHref = this.select("div.list-desc a").attr("href")

        val href = fixUrl(
            if (rawHref.startsWith("./") || rawHref.startsWith("board.php")) {
                "$mainUrl/bbs/${rawHref.removePrefix("./")}"
            } else {
                rawHref
            }
        )

        val posterUrl = fixUrl(this.select("img").attr("src"))
        val score = this.select("div.rating").text()
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.score = Score.from10(score)
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/bbs/search.php?srows=24&gr_id=&sfl=wr_subject&stx=$query", timeout = 100).document
            .select("div.list-row").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 100).document
        doc.setBaseUri(url)
        val title = doc.selectFirst("div.view-title h1")?.text() ?: "UnKnown"
        val poster = fixUrlNull(doc.selectFirst("div.image img")?.attr("src") ?: doc.selectFirst("meta[property=og:image]")?.attr("content"))
        val genres = doc.select("p:contains(장르)").first()?.select("span:nth-of-type(2)")?.text()?.split(",")?.map { it.trim() } ?: emptyList()
        val year = title.substringAfterLast("(").substringBefore(")").toIntOrNull()
        val descript = doc.select("div.view-stocon,div.view-cont").html().split("<br>").map { Jsoup.parse(it).text().trim() }.filter { it.isNotEmpty() }.joinToString("\n")

        val items = doc.select("li.list-item a")

        val hasEpisodeText = items.any {
            it.text().contains("episode", ignoreCase = true) ||
                    it.text().contains("회") ||
                    it.text().contains("화")
        }

        val type = when {
            items.size > 1 -> TvType.TvSeries
            hasEpisodeText -> TvType.TvSeries
            else -> TvType.Movie
        }

        val href= fixUrl(doc.selectFirst("li.list-item a")?.absUrl("href").orEmpty())


        if (type == TvType.TvSeries)
        {
            val episodes= doc.select("li.list-item").map {
                val epno = it.select("div.wr-num").text().toIntOrNull()
                val href = it.selectFirst("a")?.absUrl("href").orEmpty()

                newEpisode(href)
                {
                    this.name = "Episode $epno"
                    this.episode = epno
                }

            }
            return newAnimeLoadResponse(title, url, TvType.Anime)
            {
                addEpisodes(DubStatus.Subbed, episodes.reversed())
                this.year = year
                this.tags = genres
                this.posterUrl = poster
                this.plot = descript
            }
        }
        else
        {
            return newMovieLoadResponse(title, url, TvType.AnimeMovie, href)
            {
                this.year = year
                this.tags = genres
                this.posterUrl = poster
                this.plot = descript
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, timeout = 20).document
        val iframeSrc = doc.selectFirst("iframe")?.attr("src")
        val vurl = doc.selectFirst("form.tt input[name=vurl]")?.attr("value")
        val iframe = iframeSrc ?: vurl.orEmpty()
        if (iframe.contains("/video/"))
        {
            Cdndania().getUrl(iframe,mainUrl,subtitleCallback,callback)
        }
        else loadExtractor(iframe,mainUrl,subtitleCallback,callback)
        return true
    }

}