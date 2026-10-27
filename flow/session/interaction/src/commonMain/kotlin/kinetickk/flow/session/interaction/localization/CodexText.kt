// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.flow.session.interaction.localization

import kinetickk.foundation.common.localization.TextResource

/**
 * Codex copy of the build system: tabs, reactions, pairings, tags and their (!) explanations.
 * Labels stay short; every rule sentence sits behind an info button.
 */
internal enum class CodexText(
    override val english: String,
    override val russian: String,
) : TextResource {
    TAB_REACTIONS("Reactions", "Реакции"),
    TAB_PAIRINGS("Pairings", "Сочетания"),
    TAB_TAGS("Tags", "Теги"),
    TAB_BUILD_SHORT("Build", "Сборка"),
    TAB_CATALOG_SHORT("Catalog", "Каталог"),
    TAB_REACTIONS_SHORT("Reactions", "Реакции"),
    TAB_PAIRINGS_SHORT("Pairs", "Пары"),
    TAB_TAGS_SHORT("Tags", "Теги"),

    REACTIONS_PROGRESS_LABEL("Reactions", "Реакции"),
    PAIRINGS_PROGRESS_LABEL("Pairings", "Сочетания"),
    REACTIONS_PROGRESS("Reactions discovered {0} of {1}", "Открыто реакций: {0} из {1}"),
    PAIRINGS_PROGRESS("Named pairings discovered {0} of {1}", "Открыто именных сочетаний: {0} из {1}"),

    REACTION_RULE(
        "A reaction fires when a new element lands on an enemy that holds 2 or more stacks of another element. " +
            "It consumes both, and its residue can feed the next reaction. A Break fires when one element reaches its threshold.",
        "Реакция срабатывает, когда на врага, у которого уже есть 2 или больше слоёв другой стихии, ложится новая. " +
            "Она поглощает обе, а её остаток может запустить следующую реакцию. Пробой срабатывает, когда одна стихия набирает порог.",
    ),
    PAIRING_RULE(
        "Relics that share a key harmonize: each partner strengthens the upside by {0} %. Opposed keys interfere: " +
            "each weakens it by {1} %. A named pairing replaces both rules for its two relics. Costs never change.",
        "Реликвии с общим ключом гармонируют: каждая усиливает преимущество другой на {0} %. Противоположные ключи " +
            "мешают: каждая ослабляет его на {1} %. Именное сочетание заменяет оба правила для своих двух реликвий. Цена не меняется никогда.",
    ),
    TAGS_RULE(
        "Every owned item adds a point to each of its tags, and a Reborn item adds two. Resonance tiers open at {0}, {1} and {2} points.",
        "Каждый предмет даёт по очку каждому своему тегу, а перерождённый по два. Уровни резонанса открываются при {0}, {1} и {2} очках.",
    ),
    AFFINITY_RULE(
        "Items with the element, a fully stacked item and a bound relic of its aspect raise its affinity. Tiers open at {0}, {1} and {2}.",
        "Сродство стихии растёт от предметов с ней, от предмета с максимумом копий и от связанной реликвии её аспекта. Уровни открываются при {0}, {1} и {2}.",
    ),
    KEYS_RULE(
        "Keys predict how relics interact. Relics that share a key harmonize: each strengthens the other's upside by {0} %. " +
            "Opposed keys interfere: each weakens it by {1} %.",
        "Ключи предсказывают, как взаимодействуют реликвии. Реликвии с общим ключом гармонируют: каждая усиливает " +
            "преимущество другой на {0} %. Противоположные ключи мешают: каждая ослабляет его на {1} %.",
    ),
    BREAK_RULE(
        "A Break fires when one element reaches this many stacks on an enemy: the stacks are spent and the Break lands.",
        "Пробой срабатывает, когда на враге набирается столько слоёв одной стихии: слои расходуются, и пробой срабатывает.",
    ),

    KIND_REACTION("Reaction", "Реакция"),
    KIND_BREAK("Break", "Пробой"),
    KIND_ARCHETYPE("Archetype", "Архетип"),
    KIND_AFFINITY("Affinity", "Сродство"),
    KIND_KEY("Relic key", "Ключ реликвии"),
    KIND_CONFLICT("Conflict", "Конфликт"),

    UNKNOWN_REACTION("Unknown reaction", "Неизвестная реакция"),
    UNKNOWN_PAIRING("Unknown pairing", "Неизвестное сочетание"),
    REACTION_HELP("Trigger it during a run to learn its effect.", "Вызовите её в забеге, чтобы узнать её действие."),
    PAIRING_HELP("Bind both relics in one run to learn this pairing.", "Свяжите обе реликвии в одном забеге, чтобы узнать это сочетание."),
    INPUTS("Elements", "Стихии"),
    LEAVES("Leaves {0}", "Оставляет {0}"),
    FEEDS("Can feed", "Может запустить"),
    STACKS_TO_BREAK("Stacks to break", "Слоёв до пробоя"),
    FADES_AFTER("Stack lasts", "Слой держится"),
    REACHABLE("Reachable", "Доступна"),
    THIS_RUN("This run", "Этот забег"),
    OUT_OF_REACH("Out of reach", "Недоступна"),
    DISCOVERED("Discovered", "Открыто"),

    CONCORD("Concord", "Созвучие"),
    DISCORD("Discord", "Разлад"),
    HARMONY("Harmony", "Гармония"),
    DISSONANCE("Dissonance", "Диссонанс"),
    HARMONY_EFFECT("A shared key strengthens both upsides by {0} %.", "Общий ключ усиливает оба преимущества на {0} %."),
    DISSONANCE_EFFECT("Opposed keys weaken both upsides by {0} %.", "Противоположные ключи ослабляют оба преимущества на {0} %."),
    LINK_ACTIVE("Bound", "Связаны"),

    SECTION_CONCORDS("Concords", "Созвучия"),
    SECTION_HARMONY("Harmony", "Гармония"),
    SECTION_DISSONANCE("Dissonance", "Диссонанс"),
    SECTION_INCOMPATIBLE("Known incompatibilities", "Известные несовместимости"),
    SECTION_RESONANCE("Resonance", "Резонанс"),
    SECTION_AFFINITY("Affinity", "Сродство"),
    SECTION_KEYS("Relic keys", "Ключи реликвий"),
    SECTION_COSTS("Costs", "Цена"),
    SECTION_LINKS("Relic links", "Связи реликвий"),
    SECTION_REACTIONS("Reactions", "Реакции"),
    SECTION_LOADOUT("Loadout", "Снаряжение"),

    SUPPRESSED("Suppressed", "Подавлен"),
    SUPPRESSED_EFFECT(
        "Your bound relics disable every effect of this item.",
        "Связанные реликвии отключают все эффекты этого предмета.",
    ),
    DORMANT("Dormant", "Спит"),
    DORMANT_HELP(
        "Its effects need an active item or a weapon of another type.",
        "Его эффектам нужен активный предмет или оружие другого типа.",
    ),
    NOT_IN_RUN("Not in this run", "Не в этом забеге"),
    NOT_IN_RUN_HELP(
        "This run's draft deck left it out; the next run deals a new deck.",
        "Колода этого забега его не включила; следующий забег раздаст новую колоду.",
    ),
    REBORN("Reborn", "Перерождённый"),
    RISK("Risk", "Риск"),
    /**
     * Active items: the detail's chip (as the reward card's), the spoken mark of an active's cell, the detail's Active
     * and Passive sections and the rank an owned active holds.
     */
    ACTIVE_CHIP("Active", "Актив"),
    ACTIVE_ITEM("Active item", "Активный предмет"),
    ACTIVE_PART("Active", "Активный эффект"),
    PASSIVE_PART("Passive", "Пассивный эффект"),
    ACTIVE_RANK("Rank {0}/{1}", "Ранг {0}/{1}"),
    ACTIVE_OFFERS_HELP(
        "An active item joins ordinary offers once your run level reaches the item's level; the Matter you have collected does not open it sooner.",
        "Активный предмет попадает в обычные предложения, когда уровень забега достигает уровня предмета; собранная материя не открывает его раньше.",
    ),
    SEVERED("Severed", "Отсечена"),
    SEVERED_HELP(
        "A replaced relic keeps its cost for a while after it leaves its slot.",
        "Заменённая реликвия ещё какое-то время сохраняет свою цену после того, как покинула ячейку.",
    ),
    COST("Cost", "Цена"),
    KEYS("Keys", "Ключи"),
    ATTUNED("Attuned", "Настройка"),
    ATTUNED_HELP(
        "Clause effects of items with these tags grow by {0} % while the relic is bound.",
        "Эффекты условий у предметов с этими тегами растут на {0} %, пока реликвия связана.",
    ),
    RANK_N("Rank {0}", "Ранг {0}"),
    POINTS("Points", "Очки"),
    DOMINANT("Dominant", "Основная"),
    OPPOSES("Opposes", "Противостоит"),
    CARRIED_BY("Carried by", "Носители"),
    UNLOCKS_AT("Unlocks at", "Открывается"),
    REBIRTH_GATE_HELP(
        "Reach this Rebirth tier to find it during runs.",
        "Достигните этого уровня перерождения, чтобы находить это в забегах.",
    ),
    /** The Items matrix sort: the wide toolbar label and the control's spoken purpose. */
    SORT("Sort", "Сортировка"),
    SORT_TAG("Tag", "Тег"),
    SORT_ELEMENT("Element", "Стихия"),
    /** Name of the Items row holding the items without a tag. */
    NO_TAG("No tag", "Без тега"),
    NO_PAIRINGS("No pairings yet", "Сочетаний пока нет"),
    NO_PAIRINGS_HELP(
        "Discover at least two relics to see how they interact.",
        "Откройте хотя бы две реликвии, чтобы увидеть, как они взаимодействуют.",
    ),
    CONFLICT_DEADEYE_PRISM(
        "The Reticle caps critical chance at 30 %, so the Prism never converts more than that.",
        "Прицел ограничивает шанс критического удара 30 %, поэтому Призма никогда не превращает больше.",
    ),
    CONFLICT_HARDLIGHT_AEGIS(
        "The Aegis already turns all healing into shield, so the Weave has no overheal left to convert.",
        "Эгида и так превращает всё лечение в щит, поэтому Плетению нечего преобразовывать.",
    ),
    RESONANCE_SOURCE("Resonance", "Резонанс"),
    /** Two names read together (accessibility summaries of pairs and reaction inputs). */
    BOTH("{0} and {1}", "{0} и {1}"),
    SECONDS("{0} s", "{0} с"),
    ENEMIES_TITLE("Enemies", "Враги"),
    ENEMY_KIND("Enemy", "Враг"),
    ELITE_KIND("Elite", "Элита"),
    MINI_BOSS_KIND("Mini-boss", "Мини-босс"),
    BOSS_KIND("Boss", "Босс"),
    THREAT("Threat", "Угроза"),
    MECHANIC("Mechanic", "Механика"),
    REFIT("Refit", "Доработка"),
    HONING("Honing", "Заточка"),
    TRIGGER("Trigger", "Источник"),
    DIFFICULTY("Difficulty", "Сложность"),
    APPEARS("Appears", "Появляется"),
    FROM_TIME("From {0}", "С {0}"),
    REBORN_GATE("Reborn {0}", "Перерождение {0}"),
    IN_ROSTER("In the roster", "В составе"),
    ENEMY_HELP(
        "An enemy joins the waves once the run clock, plus the Rebirth tier's head start, reaches its time, and only from its Rebirth rank.",
        "Враг входит в волны, когда время забега с форой уровня перерождения достигает его срока, и только с его ранга перерождения.",
    ),
    ENEMY_LOCKED_HELP(
        "Reach this Rebirth rank to meet this enemy in runs.",
        "Достигните этого ранга перерождения, чтобы встретить этого врага в забегах.",
    ),
    WEAPON_FAMILY("Family", "Семейство"),
    WEAPON_ELEMENT("Element", "Стихия"),
    WEAPON_KEY("Relic key", "Ключ реликвий"),
    WEAPON_SCALING("Scales with", "Растёт от"),
    WEAPON_CADENCE("Cadence", "Ритм"),
    CADENCE_CONTINUOUS("Continuous", "Непрерывно"),
    CADENCE_CONTACT("On contact", "При касании"),
    CADENCE_DISCRETE("Periodic", "Периодически"),
    WEAPON_SIGNATURE("Signature", "Фирменное"),
    WEAPON_SIGNATURE_OF("Signature of {0}", "Фирменное оружие формы «{0}»"),
    WEAPON_SIGNATURE_ACTIVE(
        "Active with your character: +20% weapon power, and runs start one weapon level higher.",
        "Действует с вашим персонажем: +20% мощности оружия, забег начинается на уровень оружия выше.",
    ),
    WEAPON_SIGNATURE_INACTIVE(
        "With {0}: +20% weapon power, and runs start one weapon level higher.",
        "С формой «{0}»: +20% мощности оружия, забег начинается на уровень оружия выше.",
    ),
    WEAPON_TWIST("Signature twist", "Фирменный эффект"),
    WEAPON_FORMS("Mastery forms", "Формы мастерства"),
    WEAPON_HARMONY_BOUND(
        "Bound Relics carrying the {0} key: {1}. The weapon gains 20% power; each of these Relics grows 10% stronger.",
        "Привязанные реликвии с ключом «{0}»: {1}. Оружие получает +20% мощности; каждая из этих реликвий усиливается на 10%.",
    ),
    WEAPON_HARMONY_HEIRLOOM(
        "Heirloom candidates carrying the {0} key: {1}. Choosing one adds 20% weapon power.",
        "Реликвии наследия с ключом «{0}»: {1}. Выбор любой из них даёт +20% мощности оружия.",
    ),
    WEAPON_HARMONY_NONE(
        "A Relic with the {0} key, chosen as heirloom or bound in a run, adds 20% weapon power and grows 10% stronger itself.",
        "Реликвия с ключом «{0}», выбранная из наследия или привязанная в забеге, даёт +20% мощности оружия и сама усиливается на 10%.",
    ),
    WEAPON_RESONANCE(
        "Each point in {0} or {1} adds 5% weapon power, up to 50%.",
        "Каждое очко «{0}» или «{1}» даёт +5% мощности оружия, до +50%.",
    ),
    WEAPON_RESONANCE_NOW("This run: {0} points, +{1}% weapon power.", "В этом забеге очков: {0}, +{1}% мощности оружия."),
}
