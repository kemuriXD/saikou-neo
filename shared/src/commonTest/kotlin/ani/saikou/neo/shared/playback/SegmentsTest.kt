package ani.saikou.neo.shared.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SegmentsTest {
    @Test
    fun noSegmentsIsOnePlainStretch() {
        assertEquals(listOf(Stretch(null, 0, 1_420_000)), stretches(emptyList(), 1_420_000))
    }

    @Test
    fun anUnknownLengthHasNoStretches() {
        assertEquals(emptyList<Stretch>(), stretches(listOf(Segment(SegmentKind.Opening, 0, 90_000)), 0))
    }

    @Test
    fun segmentsAreNamedAndTheGapsBetweenAreTheEpisode() {
        val segments =
            listOf(
                Segment(SegmentKind.Ending, 1_310_000, 1_400_000),
                Segment(SegmentKind.Recap, 0, 70_000),
                Segment(SegmentKind.Opening, 70_000, 160_000),
                Segment(SegmentKind.Preview, 1_400_000, 1_420_000),
            )

        assertEquals(
            listOf(
                Stretch(SegmentKind.Recap, 0, 70_000),
                Stretch(SegmentKind.Opening, 70_000, 160_000),
                Stretch(null, 160_000, 1_310_000),
                Stretch(SegmentKind.Ending, 1_310_000, 1_400_000),
                Stretch(SegmentKind.Preview, 1_400_000, 1_420_000),
            ),
            stretches(segments, 1_420_000),
        )
    }

    @Test
    fun overlapsAreTrimmedAndSegmentsPastTheEndAreCut() {
        val segments =
            listOf(
                Segment(SegmentKind.Opening, 10_000, 100_000),
                Segment(SegmentKind.Recap, 0, 30_000),
                Segment(SegmentKind.Ending, 500_000, 700_000),
                Segment(SegmentKind.Preview, 700_000, 800_000),
            )

        assertEquals(
            listOf(
                Stretch(SegmentKind.Recap, 0, 30_000),
                Stretch(SegmentKind.Opening, 30_000, 100_000),
                Stretch(null, 100_000, 500_000),
                Stretch(SegmentKind.Ending, 500_000, 600_000),
            ),
            stretches(segments, 600_000),
        )
    }

    @Test
    fun findsTheStretchAtAPosition() {
        val bar = stretches(listOf(Segment(SegmentKind.Opening, 60_000, 150_000)), 600_000)

        assertEquals(Stretch(null, 0, 60_000), bar.at(0))
        assertEquals(SegmentKind.Opening, bar.at(60_000)?.kind)
        assertEquals(null, bar.at(150_000)?.kind)
        assertEquals(Stretch(null, 150_000, 600_000), bar.at(600_000))
        assertNull(bar.at(600_001))
    }
}
