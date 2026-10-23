// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.ball.gameplay.nucleus.model

import kinetickk.ball.content.api.ContentBounds
import kinetickk.ball.content.api.StatusElement

/** Cells of one [CombatFieldTable] record. Times are run seconds; ids and ordinals are stored as exact floats. */
internal object FieldCell {
    /** [CombatFieldKind] ordinal. */
    const val KIND = 0
    /** [FieldSource] ordinal. */
    const val SOURCE = 1
    /** Reaction ordinal, item id or skill slot. */
    const val OWNER = 2
    const val X = 3
    const val Y = 4
    /** Far end of a segment field (Frost Line); equal to [X]/[Y] for round fields. */
    const val X2 = 5
    const val Y2 = 6
    const val RADIUS = 7
    /** Damage per second; a Null Well's is a share of each occupant's weighted maximum integrity. */
    const val DPS = 8
    /** Status element ordinal applied per tick, or -1. */
    const val ELEMENT = 9
    /** Status stacks applied per tick. */
    const val STACKS = 10
    const val GENERATION = 11
    const val PULL = 12
    const val CREATED_AT = 13
    /** When the granted life ends, for presentation; the step itself counts [REMAINING] down. */
    const val EXPIRES_AT = 14
    /** Seconds of life left, decremented every step. */
    const val REMAINING = 15
    /** Seconds accumulated toward the next tick. */
    const val TICK_CLOCK = 16
    /** Kind counter: bolts eaten, growths, echoes, zaps. */
    const val CHARGE = 17
    /** Enemy id an attractor follows, or -1. */
    const val ANCHOR_ID = 18
    /** [FieldFlags] bits. */
    const val FLAGS = 19
    /** A skill value the field carries (zap damage, rank values). */
    const val POWER = 20
    /** Admission order: the oldest field of a kind is replaced first, and fields written during a pass wait. */
    const val SEQUENCE = 21
    const val STRIDE = 22
}

internal object FieldFlags {
    const val FOLLOWS_CORE: Int = 1
    const val FOLLOWS_ANCHOR: Int = 1 shl 1
    const val CORE_INSIDE: Int = 1 shl 2
    const val RANK_III: Int = 1 shl 3
    /** A reaction field in its keystone form (Dwarf Star, Plague Pit, Dead Air, Rewind, Hungry Void). */
    const val KEYSTONE: Int = 1 shl 4
}

/** A detached copy of one record, for tests, fingerprints and diagnostics (allocates; never on a hot path). */
internal data class CombatFieldRecord(
    val kind: CombatFieldKind,
    val source: FieldSource,
    val owner: Int,
    val x: Float,
    val y: Float,
    val x2: Float,
    val y2: Float,
    val radius: Float,
    val damagePerSecond: Float,
    val element: StatusElement?,
    val stacksPerTick: Int,
    val generation: Int,
    val pull: Float,
    val createdAt: Float,
    val expiresAt: Float,
    val remaining: Float,
    val tickClock: Float,
    val charge: Float,
    val anchorId: Int,
    val flags: Int,
    val power: Float,
    val sequence: Int,
)

/**
 * The run's lingering fields: at most [CAPACITY] fixed-stride records, live ones packed at the front in
 * admission order (removal compacts in place, so creation order is scan order). Forks share storage until
 * the first changed write; a step allocates nothing. [revision] moves whenever the set of fields, a position
 * or a counter changes, never for the per-step life and tick clocks.
 */
internal class CombatFieldTable private constructor(
    private val cells: CopyOnWriteFloatArray,
) {
    constructor() : this(CopyOnWriteFloatArray(FloatArray(CAPACITY * FieldCell.STRIDE)))

    var count: Int = 0
        private set
    var revision: Int = 0
        private set
    /** Sequence the next admitted record receives. */
    var nextSequence: Int = 0
        private set

    operator fun get(index: Int, cell: Int): Float = cells[offset(index) + cell]

    operator fun set(index: Int, cell: Int, value: Float) {
        cells[offset(index) + cell] = value
    }

    fun kind(index: Int): CombatFieldKind = CombatFieldKind.entries[get(index, FieldCell.KIND).toInt()]

    fun source(index: Int): FieldSource = FieldSource.entries[get(index, FieldCell.SOURCE).toInt()]

    fun owner(index: Int): Int = get(index, FieldCell.OWNER).toInt()

    fun element(index: Int): StatusElement? = StatusElement.entries.getOrNull(get(index, FieldCell.ELEMENT).toInt())

    fun sequence(index: Int): Int = get(index, FieldCell.SEQUENCE).toInt()

    fun flags(index: Int): Int = get(index, FieldCell.FLAGS).toInt()

    fun hasFlag(index: Int, flag: Int): Boolean = flags(index) and flag != 0

    fun setFlag(index: Int, flag: Int, on: Boolean) {
        val flags = flags(index)
        val next = if (on) flags or flag else flags and flag.inv()
        if (next != flags) set(index, FieldCell.FLAGS, next.toFloat())
    }

    /** Live fields of [kind]. */
    fun countOf(kind: CombatFieldKind): Int {
        var total = 0
        for (index in 0 until count) if (kind(index) == kind) total++
        return total
    }

    /** Live fields from [source]. */
    fun countFrom(source: FieldSource): Int {
        var total = 0
        for (index in 0 until count) if (source(index) == source) total++
        return total
    }

    /** The live field of [kind] admitted first, or -1. */
    fun oldestOf(kind: CombatFieldKind): Int {
        var oldest = -1
        for (index in 0 until count) {
            if (kind(index) != kind) continue
            if (oldest < 0 || sequence(index) < sequence(oldest)) oldest = index
        }
        return oldest
    }

    /** The live skill field of [slot], or -1. */
    fun skillSlotIndex(slot: Int): Int {
        for (index in 0 until count) {
            if (source(index) == FieldSource.SKILL && owner(index) == slot) return index
        }
        return -1
    }

    /** Claims a new record at the end; the caller fills every cell through [stamp] and the setters. */
    fun append(): Int {
        check(count < CAPACITY) { "combat field table holds at most $CAPACITY fields" }
        return count++
    }

    /** Gives the record at [index] the next admission sequence (a new or overwritten field). */
    fun stamp(index: Int) {
        set(index, FieldCell.SEQUENCE, (nextSequence++).toFloat())
        revision++
    }

    /** Marks a change of position, size or counter that presentation shows. */
    fun touch() {
        revision++
    }

    /** Drops every record whose [FieldCell.REMAINING] ran out, keeping the order of the rest. */
    fun removeExpired() {
        var write = 0
        for (read in 0 until count) {
            if (get(read, FieldCell.REMAINING) <= 0f) continue
            if (write != read) {
                val from = offset(read)
                val to = offset(write)
                for (cell in 0 until FieldCell.STRIDE) cells[to + cell] = cells[from + cell]
            }
            write++
        }
        if (write != count) {
            count = write
            revision++
        }
    }

    fun clear() {
        if (count == 0 && nextSequence == 0) return
        count = 0
        nextSequence = 0
        revision++
    }

    /** An isolated candidate: counters copied, records shared until the first changed write. */
    fun fork(): CombatFieldTable = CombatFieldTable(cells.fork()).also { copy ->
        copy.count = count
        copy.revision = revision
        copy.nextSequence = nextSequence
    }

    fun sharesStorageWith(other: CombatFieldTable): Boolean = cells.sharesStorageWith(other.cells)

    fun record(index: Int): CombatFieldRecord {
        require(index in 0 until count) { "field $index is not live" }
        return CombatFieldRecord(
            kind = kind(index),
            source = source(index),
            owner = owner(index),
            x = get(index, FieldCell.X),
            y = get(index, FieldCell.Y),
            x2 = get(index, FieldCell.X2),
            y2 = get(index, FieldCell.Y2),
            radius = get(index, FieldCell.RADIUS),
            damagePerSecond = get(index, FieldCell.DPS),
            element = element(index),
            stacksPerTick = get(index, FieldCell.STACKS).toInt(),
            generation = get(index, FieldCell.GENERATION).toInt(),
            pull = get(index, FieldCell.PULL),
            createdAt = get(index, FieldCell.CREATED_AT),
            expiresAt = get(index, FieldCell.EXPIRES_AT),
            remaining = get(index, FieldCell.REMAINING),
            tickClock = get(index, FieldCell.TICK_CLOCK),
            charge = get(index, FieldCell.CHARGE),
            anchorId = get(index, FieldCell.ANCHOR_ID).toInt(),
            flags = flags(index),
            power = get(index, FieldCell.POWER),
            sequence = sequence(index),
        )
    }

    fun records(): List<CombatFieldRecord> = List(count) { record(it) }

    /** Deterministic text of the live records and counters (fingerprints). */
    override fun toString(): String = "CombatFieldTable(count=$count, revision=$revision, next=$nextSequence, ${records()})"

    private fun offset(index: Int): Int = index * FieldCell.STRIDE

    companion object {
        const val CAPACITY: Int = ContentBounds.MAX_COMBAT_FIELDS
    }
}
