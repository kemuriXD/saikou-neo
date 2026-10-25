package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.json.JsonReader

/** What AniZip knows of a show's episodes, in order, and its extras, such as a film's making-of. */
public data class AniZipEpisodes(
    val episodes: List<AniZipEpisode>,
    val extras: List<AniZipExtra>,
)

/** One episode. AniZip lists episodes before they air, with only their date. */
public data class AniZipEpisode(
    val number: Int,
    val title: String?,
    val synopsis: String?,
    /** A 16:9 still from the episode. */
    val thumbnail: String?,
    val airDate: AirDate?,
    val minutes: Int?,
)

public data class AniZipExtra(
    val title: String,
    val minutes: Int?,
)

/** A day, as AniZip writes it. Months and days count from 1. */
public data class AirDate(
    val year: Int,
    val month: Int,
    val day: Int,
)

/**
 * Reads `episodes`, which AniZip keys by number. Specials are keyed `S1`, `S2` and so on, and become extras. Other
 * keys, such as `O1` for a film's subtitles, are left out.
 */
internal fun JsonReader.episodes(): AniZipEpisodes {
    val episodes = mutableListOf<AniZipEpisode>()
    val extras = mutableListOf<Pair<Int, AniZipExtra>>()
    beginObject()
    while (hasNext()) {
        if (nextName() != "episodes" || peek() != JsonReader.Token.BEGIN_OBJECT) {
            skipValue()
            continue
        }
        beginObject()
        while (hasNext()) {
            val key = nextName()
            val fields = readEpisode()
            val number = key.toIntOrNull()
            val special =
                specialKey
                    .matchEntire(key)
                    ?.groupValues
                    ?.get(1)
                    ?.toInt()
            when {
                number != null && number > 0 -> {
                    episodes += fields.toEpisode(number)
                }

                special != null -> {
                    fields.title?.let { extras += special to AniZipExtra(it, fields.minutes) }
                }
            }
        }
        endObject()
    }
    endObject()
    return AniZipEpisodes(episodes.sortedBy { it.number }, extras.sortedBy { it.first }.map { it.second })
}

private val specialKey = Regex("""S(\d+)""")

// AniDB names an episode it knows nothing about "Episode 5", which says no more than its number.
private val placeholderTitle = Regex("""Episode \d+""")

private class EpisodeFields(
    val title: String?,
    val synopsis: String?,
    val thumbnail: String?,
    val airDate: AirDate?,
    val minutes: Int?,
) {
    fun toEpisode(number: Int) = AniZipEpisode(number, title, synopsis, thumbnail, airDate, minutes)
}

/** One episode's fields. TheTVDB's come first (`airDate`, `runtime`, `overview`), then AniDB's. */
private fun JsonReader.readEpisode(): EpisodeFields {
    if (peek() != JsonReader.Token.BEGIN_OBJECT) {
        skipValue()
        return EpisodeFields(null, null, null, null, null)
    }
    var titles = emptyMap<String, String>()
    val fields = mutableMapOf<String, String>()
    beginObject()
    while (hasNext()) {
        val name = nextName()
        when {
            name == "title" -> {
                titles = readFields()
            }

            peek() == JsonReader.Token.STRING || peek() == JsonReader.Token.NUMBER || peek() == JsonReader.Token.LONG -> {
                fields[name] =
                    nextString()!!
            }

            else -> {
                skipValue()
            }
        }
    }
    endObject()
    return EpisodeFields(
        title = (titles["en"] ?: titles["x-jat"])?.trim()?.takeUnless { it.isEmpty() || it.matches(placeholderTitle) },
        synopsis = (fields["overview"] ?: fields["summary"])?.trim()?.ifEmpty { null },
        thumbnail = fields["image"]?.takeIf { it.startsWith("https://") },
        airDate = (fields["airDate"] ?: fields["airdate"])?.toAirDate(),
        minutes = (fields["runtime"] ?: fields["length"])?.toIntOrNull()?.takeIf { it > 0 },
    )
}

private val isoDate = Regex("""(\d{4})-(\d{2})-(\d{2})""")

private fun String.toAirDate(): AirDate? =
    isoDate.matchEntire(this)?.destructured?.let { (year, month, day) -> AirDate(year.toInt(), month.toInt(), day.toInt()) }
