package ani.saikou.neo.shared.catalogue

import com.apollographql.apollo.api.json.JsonReader

/** What AniZip knows of a show's episodes, in order, and its extras, such as a film's making-of. */
public data class AniZipEpisodes(
    val episodes: List<AniZipEpisode>,
    val extras: List<AniZipExtra>,
    /** The show's IMDb id, for its IMDb rating. */
    val imdbId: String? = null,
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
) : Comparable<AirDate> {
    override fun compareTo(other: AirDate): Int = compareValuesBy(this, other, AirDate::year, AirDate::month, AirDate::day)

    internal companion object {
        private const val SECONDS_PER_DAY = 86_400L

        /**
         * The day [epochSeconds] falls on for a clock [offsetSeconds] ahead of UTC. Howard Hinnant's `civil_from_days`,
         * since minSdk 24 has no java.time.
         */
        fun at(
            epochSeconds: Long,
            offsetSeconds: Int,
        ): AirDate {
            val days = (epochSeconds + offsetSeconds).floorDiv(SECONDS_PER_DAY) + 719_468
            val era = days.floorDiv(146_097L)
            val dayOfEra = days - era * 146_097
            val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val shiftedMonth = (5 * dayOfYear + 2) / 153
            val day = (dayOfYear - (153 * shiftedMonth + 2) / 5 + 1).toInt()
            val month = (if (shiftedMonth < 10) shiftedMonth + 3 else shiftedMonth - 9).toInt()
            val year = (yearOfEra + era * 400 + if (month <= 2) 1 else 0).toInt()
            return AirDate(year, month, day)
        }
    }
}

/**
 * Reads `episodes`, which AniZip keys by number, and the IMDb id from `mappings`. Specials are keyed `S1`, `S2` and so
 * on, and become extras. Other keys, such as `O1` for a film's subtitles, are left out.
 */
internal fun JsonReader.episodes(): AniZipEpisodes {
    val episodes = mutableListOf<AniZipEpisode>()
    val extras = mutableListOf<Pair<Int, AniZipExtra>>()
    var imdbId: String? = null
    beginObject()
    while (hasNext()) {
        val name = nextName()
        if (name == "mappings") {
            imdbId = readFields()["imdb_id"]?.takeIf { it.matches(imdbIdFormat) }
            continue
        }
        if (name != "episodes" || peek() != JsonReader.Token.BEGIN_OBJECT) {
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
    return AniZipEpisodes(episodes.sortedBy { it.number }, extras.sortedBy { it.first }.map { it.second }, imdbId)
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
