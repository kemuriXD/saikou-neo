package ani.saikou.neo.shared.sources

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun entry(
    id: String,
    version: String,
    api: Int = 1,
) = """{ "id": "$id", "name": "Source $id", "version": "$version", "api": $api, "apk": "https://example.com/$id.apk" }"""

private fun listed(
    id: String,
    version: String,
    api: Int = 1,
) = ListedSource(id, "Source $id", version, api, "https://example.com/$id.apk")

/** The phone reading a link for a TV, with the TV's rules and what the TV said it has (#405). */
class LinkForTvTest {
    private val pages =
        mapOf(
            "https://example.com/all.json" to
                """{ "sources": [ ${
                    listOf(
                        entry("com.example.fresh", "1.0.0"),
                        entry("com.example.same", "1.0.0"),
                        entry("com.example.older", "2.0.0"),
                        entry("com.example.nolist", "1.0.0"),
                        entry("com.example.newer", "1.0.0", api = 5),
                    ).joinToString()
                } ] }""",
            "https://example.com/installed.json" to """{ "sources": [ ${entry("com.example.same", "1.0.0")} ] }""",
            "https://example.com/source.apk" to "PK-not-a-list",
            "https://example.com/broken.json" to """{ "sources": [ { "id": """,
            "https://v.gd/forward.php?format=json&shorturl=All" to """{ "url": "https://example.com/all.json" }""",
        )

    // This TV runs API versions 1 to 4.
    private val tv =
        TvSources(
            installed =
                listOf(
                    TvSource("com.example.same", "1.0.0", hasList = true),
                    TvSource("com.example.older", "1.5.0", hasList = true),
                    TvSource("com.example.nolist", "1.0.0", hasList = false),
                ),
            api = 1..4,
        )

    private suspend fun read(text: String): LinkForTv {
        val engine = PagesEngine(pages)
        return readLinkForTv(text, tv, ShortCodes(engine), SourceDownloads(engine))
    }

    @Test
    fun eachSourceShowsWhatItIsOnTheTv() =
        runTest {
            val read = read("https://example.com/all.json") as LinkForTv.Listed

            val statuses = read.choices.associate { it.source.id to it.status }
            assertEquals(ChoiceStatus.New, statuses["com.example.fresh"])
            assertEquals(ChoiceStatus.Installed, statuses["com.example.same"])
            assertEquals(ChoiceStatus.Update("1.5.0"), statuses["com.example.older"])
            assertEquals(ChoiceStatus.AddAgain, statuses["com.example.nolist"])
            assertEquals(ChoiceStatus.CantUse(SourceProblem.BUILT_FOR_NEWER_APP), statuses["com.example.newer"])
            assertTrue(read.choices.none { it.ticked })
            assertEquals("https://example.com/all.json", read.list)
        }

    @Test
    fun aCodeIsSentAsTheLinkItLedTo() =
        runTest {
            val read = read("All") as LinkForTv.Listed

            assertEquals("https://example.com/all.json", read.list)
        }

    @Test
    fun onlyTickedSourcesThatCanBePickedAreSent() =
        runTest {
            val read = read("https://example.com/all.json") as LinkForTv.Listed
            val ticked =
                read.copy(
                    choices = read.choices.map { it.copy(ticked = it.source.id in setOf("com.example.fresh", "com.example.same")) },
                )

            assertEquals(
                PickedSources.FromList("https://example.com/all.json", listOf(listed("com.example.fresh", "1.0.0"))),
                ticked.picked(),
            )
            assertNull(read.picked())
        }

    @Test
    fun aLinkThatIsNotAListIsOneApk() =
        runTest {
            val read = read("https://example.com/source.apk")

            assertEquals(LinkForTv.OneApk("https://example.com/source.apk"), read)
            assertEquals(PickedSources.OneApk("https://example.com/source.apk"), (read as LinkForTv.OneApk).picked())
        }

    @Test
    fun aListTheTvHasAllOfSaysSo() =
        runTest {
            val read = read("https://example.com/installed.json") as LinkForTv.Listed

            assertEquals(listOf(ChoiceStatus.Installed), read.choices.map { it.status })
            assertEquals(AddError.ALL_INSTALLED, read.choices.whyNone())
        }

    @Test
    fun linksTheTvWouldRefuseAreRefused() =
        runTest {
            assertEquals(LinkForTv.Failed(AddError.NOT_SECURE), read("http://example.com/all.json"))
            assertEquals(LinkForTv.Failed(AddError.NOT_A_SOURCE), read("https://example.com/gone.json"))
            assertEquals(LinkForTv.Failed(AddError.NO_SUCH_CODE), read("not a link"))
        }

    @Test
    fun anUnreadableListSaysSo() =
        runTest {
            assertEquals(LinkForTv.Failed(AddError.BROKEN_LIST), read("https://example.com/broken.json"))
        }
}
