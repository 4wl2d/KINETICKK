// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.content.api

/** Exact presentation grammars emitted by reward choices and build notifications. */
internal fun String.dynamicContentInRussian(): String? {
    for ((pattern, render) in ContentMessageTemplates) {
        val match = pattern.matchEntire(this) ?: continue
        render(match.groupValues)?.let { return it }
    }
    for (separator in listOf("  //  ", " // ")) {
        if (separator !in this) continue
        val parts = split(separator)
        val translated = parts.map { it.contentInRussian() ?: return null }
        return translated.joinToString(separator)
    }
    return null
}

private typealias ContentMessageRenderer = (List<String>) -> String?

private val ContentMessageTemplates: List<Pair<Regex, ContentMessageRenderer>> = listOf(
    Regex("Amplify (.+)") to { g -> g[1].contentInRussian()?.let { "Усилить: $it" } },
    Regex("Replace (.+)") to { g -> g[1].contentInRussian()?.let { "Заменить: $it" } },
    Regex("Meld (.+)") to { g -> g[1].contentInRussian()?.let { "Слить: $it" } },
    Regex("Advance the current system from level ([0-9]+) to ([0-9]+) immediately\\.") to
        { g -> "Сразу повысить текущую систему с уровня ${g[1]} до ${g[2]}." },
    Regex("Merge the duplicate resonance and advance rank ([0-9]+) to ([0-9]+)\\.") to
        { g -> "Объединить повторный резонанс и повысить ранг с ${g[1]} до ${g[2]}." },
    Regex("This resonance is already rank ([0-9]+); selecting it salvages Kinetic Matter\\.") to
        { g -> "Этот резонанс уже имеет ранг ${g[1]}; его выбор принесёт кинетическую материю." },
    Regex("Collapse this offering into one of the (four|[0-9]+) bound Relics and raise its rank\\.") to
        { g -> "Слить дар с одной из ${if (g[1] == "four") "4" else g[1]} привязанных реликвий и повысить её ранг." },
    Regex("Break slot ([0-9]+) and bind (.+) at rank 1\\.") to
        { g -> g[2].contentInRussian()?.let { "Освободить ячейку ${g[1]} и привязать «$it» с рангом 1." } },
    Regex("Collapse the offering into slot ([0-9]+) and advance rank ([0-9]+) to ([0-9]+)\\.") to
        { g -> "Слить дар с ячейкой ${g[1]} и повысить ранг с ${g[2]} до ${g[3]}." },
    Regex("Slot ([0-9]+) is already rank ([0-9]+); salvage the excess resonance\\.") to
        { g -> "Ячейка ${g[1]} уже имеет ранг ${g[2]}; утилизировать избыточный резонанс." },
    Regex("LEVEL ([0-9]+)") to { g -> "УРОВЕНЬ ${g[1]}" },
    Regex("RANK ([0-9]+)") to { g -> "РАНГ ${g[1]}" },
    Regex("SLOT ([0-9]+)") to { g -> "ЯЧЕЙКА ${g[1]}" },
    Regex("SLOT ([0-9]+) BOUND") to { g -> "ЯЧЕЙКА ${g[1]} ПРИВЯЗАНА" },
    Regex("(.+) (SALVAGED|RESONANCE|ACQUIRED|SYNCHRONIZED)") to { g ->
        val name = g[1].contentInRussian()
        val status = g[2].contentInRussian()
        if (name != null && status != null) "$name // $status" else null
    },
    Regex("(.+) mastery advanced") to { g -> g[1].contentInRussian()?.let { "$it: мастерство повышено" } },
    Regex("([12]) different (.+) relics?") to { g -> g[2].contentInRussian()?.let {
        "${g[1]} ${if (g[1] == "1") "новая реликвия" else "разные реликвии"} аспекта «$it»"
    } },
    Regex("([+−]) (.+)") to { g -> g[2].contentInRussian()?.let { "${g[1]} $it" } },
    Regex("(.+) ([+−-]?[0-9]+(?:\\.[0-9]+)?)(%|×|/s|s)?") to { g ->
        g[1].contentInRussian()?.let {
            val unit = when (g[3]) { "/s" -> "/с"; "s" -> " с"; else -> g[3] }
            "$it ${g[2].replace('.', ',')}$unit"
        }
    },
)
