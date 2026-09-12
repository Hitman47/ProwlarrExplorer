package dev.mkdev.prowlarrexplorer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseTitleTest {

    @Test
    fun movie() {
        val p = ReleaseTitle.parse("Dune.Part.Two.2024.MULTI.VFF.2160p.WEB-DL.HDR.x265-GROUP")
        assertEquals("Dune Part Two", p.title)
        assertEquals(2024, p.year)
        assertEquals(listOf("2160p", "HDR", "WEB-DL", "x265", "MULTI", "VFF"), p.tags.map { it.label })
        assertEquals("Dune Part Two (2024)", p.heading)
    }

    @Test
    fun episode() {
        val p = ReleaseTitle.parse("The.Bear.S03E05.1080p.WEB.H264-NTb")
        assertEquals("The Bear", p.title)
        assertEquals(3, p.season)
        assertEquals(5, p.episode)
        assertEquals("The Bear · S03E05", p.heading)
        assertEquals(listOf("1080p", "WEB", "H.264"), p.tags.map { it.label })
    }

    @Test
    fun frenchSeason() {
        val p = ReleaseTitle.parse("Lupin Saison 2 FRENCH 720p HDTV")
        assertEquals("Lupin", p.title)
        assertEquals(2, p.season)
    }

    @Test
    fun bookKeepsSeparators() {
        val p = ReleaseTitle.parse("Pierre Bottero - Ellana (2006) [EPUB]")
        assertEquals("Pierre Bottero - Ellana", p.title)
        assertEquals(2006, p.year)
    }

    @Test
    fun noTagsFallsBackToRaw() {
        val p = ReleaseTitle.parse("Some Random Name")
        assertEquals("Some Random Name", p.title)
        assertEquals(null, p.year)
    }

    @Test
    fun yearFirstIsNotAYear() {
        val p = ReleaseTitle.parse("1917.2019.1080p.BluRay.x264")
        assertEquals("1917", p.title)
        assertEquals(2019, p.year)
    }
}
