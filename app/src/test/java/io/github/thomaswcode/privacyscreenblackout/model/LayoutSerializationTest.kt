package io.github.thomaswcode.privacyscreenblackout.model

import io.github.thomaswcode.privacyscreenblackout.data.LayoutRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutSerializationTest {

    @Test
    fun roundTripsBothOrientationsAndAllShapeTypes() {
        val portrait = MaskSpec(1080, 2400)
            .withShape(RectShape(1f, 2f, 300f, 400f, Op.KEEP))
            .withShape(LassoShape(listOf(Pt(1f, 1f), Pt(50f, 5f), Pt(20f, 60f)), Op.BLACK))
            .withShape(StrokeShape(listOf(Pt(5f, 5f), Pt(10f, 10f)), 32f, Op.KEEP))
        val landscape = MaskSpec(2400, 1080, Background.BLACK)
        val layouts = listOf(
            Layout("a", "Banking", portrait = portrait, landscape = landscape, opacity = 0.8f, color = 0xFF202124.toInt()),
            Layout("b", "Empty"),
        )
        val json = LayoutRepository.json.encodeToString(layouts)
        assertEquals(layouts, LayoutRepository.json.decodeFromString<List<Layout>>(json))
    }

    @Test
    fun ignoresUnknownFields() {
        val json = """[{"id":"x","name":"Old","futureField":true}]"""
        assertEquals(listOf(Layout("x", "Old")), LayoutRepository.json.decodeFromString<List<Layout>>(json))
    }
}
