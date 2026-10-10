package io.github.thomaswcode.privacyscreenblackout.geometry

import io.github.thomaswcode.privacyscreenblackout.model.CellState
import io.github.thomaswcode.privacyscreenblackout.model.MaskSpec
import kotlin.math.ceil
import kotlin.math.min

/** Rectangle in pixels (right/bottom exclusive). */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * Turns the fully-black parts of a mask into a small set of rectangles for touch-blocking windows.
 * Cells that are only partly black are left out so visible areas always receive touches.
 */
object RegionDecomposer {

    /** Merges a boolean cell grid into rectangles: horizontal runs first, then identical runs stacked downwards. */
    fun mergeCells(grid: Array<BooleanArray>): List<IntArray> {
        val out = ArrayList<IntArray>()
        // key = (startCol, endCol) -> startRow of the open rectangle
        var open = LinkedHashMap<Pair<Int, Int>, Int>()
        for (row in 0..grid.size) {
            val runs = LinkedHashSet<Pair<Int, Int>>()
            if (row < grid.size) {
                val cells = grid[row]
                var c = 0
                while (c < cells.size) {
                    if (cells[c]) {
                        val start = c
                        while (c < cells.size && cells[c]) c++
                        runs += start to c
                    } else c++
                }
            }
            val next = LinkedHashMap<Pair<Int, Int>, Int>()
            for ((run, startRow) in open) {
                if (run in runs) next[run] = startRow
                else out += intArrayOf(run.first, startRow, run.second, row)
            }
            for (run in runs) if (run !in next) next[run] = row
            open = next
        }
        return out
    }

    /**
     * Classifies the mask into cells of [cellPx] and returns merged blocker rectangles in pixels.
     * If more than [maxRects] are needed the cell size doubles until the result fits.
     */
    fun blockerRects(spec: MaskSpec, cellPx: Int, maxRects: Int): List<PixelRect> {
        var cell = cellPx.coerceAtLeast(1)
        while (true) {
            val rects = blockerRectsAt(spec, cell)
            if (rects.size <= maxRects || cell >= maxOf(spec.width, spec.height)) return rects.take(maxRects)
            cell *= 2
        }
    }

    private fun blockerRectsAt(spec: MaskSpec, cell: Int): List<PixelRect> {
        val cols = ceil(spec.width / cell.toDouble()).toInt()
        val rows = ceil(spec.height / cell.toDouble()).toInt()
        if (cols <= 0 || rows <= 0) return emptyList()
        val grid = Array(rows) { r ->
            BooleanArray(cols) { c ->
                val l = (c * cell).toFloat()
                val t = (r * cell).toFloat()
                val rr = min((c + 1) * cell, spec.width).toFloat()
                val bb = min((r + 1) * cell, spec.height).toFloat()
                spec.cellState(l, t, rr, bb) == CellState.BLACK_ALL
            }
        }
        return mergeCells(grid).map { (c0, r0, c1, r1) ->
            PixelRect(c0 * cell, r0 * cell, min(c1 * cell, spec.width), min(r1 * cell, spec.height))
        }
    }

    private operator fun IntArray.component1() = this[0]
    private operator fun IntArray.component2() = this[1]
    private operator fun IntArray.component3() = this[2]
    private operator fun IntArray.component4() = this[3]
}
