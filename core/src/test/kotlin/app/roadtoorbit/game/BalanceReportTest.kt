package app.roadtoorbit.game

import java.io.File
import kotlin.test.Test

/**
 * Not an assertion: a tuning aid. `./gradlew :core:test --tests '*BalanceReportTest*' -Dbalance=1 [-DbalanceSeeds=24]`
 * lets a skilled and a human-like pilot play every level on every difficulty and writes how hard it was to build/balance.txt:
 * wins, shields lost, where the losses happen and what the runs score.
 */
class BalanceReportTest {
    private class Pilot(val name: String, val latency: Float, val interval: Float, val noise: Float, val skill: Float)

    private val pilots = listOf(
        Pilot("skilled", 0f, 0.1f, 0f, 1f),
        Pilot("human", 0.28f, 0.3f, 0.25f, 1f),
    )

    @Test
    fun report() {
        if (System.getProperty("balance") == null) return
        val seeds = (System.getProperty("balanceSeeds") ?: "24").toInt()
        val out = StringBuilder()
        fun line(s: String) { out.appendLine(s); println(s) }
        line("pilot   level      difficulty  wins  shields lost/run  deaths car/plane/rocket  avg score   stars 0/1/2/3")
        for (level in Levels.ALL) for (d in Difficulty.values()) for (p in pilots) {
            var wins = 0
            var hits = 0
            var total = 0L
            val deaths = IntArray(3)
            val stars = IntArray(4)
            for (seed in 1L..seeds) {
                val r = SimRunner.play(seed * 7 + 1, difficulty = d, level = level, skill = p.skill, latency = p.latency, interval = p.interval, noise = p.noise)
                hits += r.hits
                total += r.score
                if (r.finished) wins++ else if (r.crashedInLeg in 0..2) deaths[r.crashedInLeg]++
                stars[if (r.finished) level.starScores.count { r.score >= (it * d.scoreScale).toInt() } else 0]++
            }
            line(
                "%-7s %-10s %-10s %3d/%-3d %-16.2f %2d / %2d / %2d            %-10d  %s".format(
                    p.name, level.name, d.label, wins, seeds, hits / seeds.toFloat(), deaths[0], deaths[1], deaths[2], total / seeds, stars.joinToString(" / "),
                ),
            )
        }
        File(System.getProperty("traceDir") ?: "build", "balance.txt").also { it.parentFile.mkdirs() }.writeText(out.toString())
    }
}
