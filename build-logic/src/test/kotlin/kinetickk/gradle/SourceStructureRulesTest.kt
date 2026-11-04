// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceStructureRulesTest {
    private val leaves = setOf(
        "ball/gameplay/nucleus", "ball/gameplay/interaction", "ball/gameplay/impl", "ball/profile/interaction",
        "app/shared", "a/one", "a/two",
    )

    private fun src(path: String, vararg lines: String) = RepositorySource(path, lines.joinToString("\n", postfix = "\n"))

    private fun body(count: Int): Array<String> = Array(count) { "// filler $it" }

    /** A source whose package matches its directory below `kotlin/`. */
    private fun kt(module: String, sourceSet: String, pkg: String, name: String, vararg lines: String) =
        src("$module/src/$sourceSet/kotlin/${pkg.replace('.', '/')}/$name.kt", "package $pkg", *lines)

    private fun measure(
        vararg sources: RepositorySource,
        config: StructureConfig = StructureConfig(),
        document: String? = null,
        exists: (String) -> Boolean = { false },
    ): List<Measurement> = measureSourceStructure(sources.toList(), leaves, document, exists, config)

    private fun List<Measurement>.of(rule: StructureRule) = filter { it.rule == rule }

    @Test
    fun mainFilesOverSixHundredLinesAreMeasuredAndAnUnterminatedLastLineCounts() {
        val path = "a/one/src/commonMain/kotlin/a/b/Big.kt"
        assertTrue(measure(src(path, "package a.b", *body(599))).of(StructureRule.MAIN_FILE_LINES).isEmpty())
        assertEquals(
            listOf(Measurement(StructureRule.MAIN_FILE_LINES, path, 601)),
            measure(src(path, "package a.b", *body(600))).of(StructureRule.MAIN_FILE_LINES),
        )
        val unterminated = RepositorySource(path, (listOf("package a.b") + body(600)).joinToString("\n"))
        assertEquals(601, measure(unterminated).of(StructureRule.MAIN_FILE_LINES).single().value)
    }

    @Test
    fun testFilesUseTheThousandLineLimitAndNeverTheMainRule() {
        val path = "a/one/src/desktopTest/kotlin/a/b/BigTest.kt"
        assertTrue(measure(src(path, "package a.b", *body(999))).of(StructureRule.TEST_FILE_LINES).isEmpty())
        assertEquals(1001, measure(src(path, "package a.b", *body(1000))).of(StructureRule.TEST_FILE_LINES).single().value)
        val seven = measure(src(path, "package a.b", *body(699)))
        assertTrue(seven.of(StructureRule.MAIN_FILE_LINES).isEmpty() && seven.of(StructureRule.TEST_FILE_LINES).isEmpty())
    }

    @Test
    fun functionsOverOneHundredLinesAreKeyedByFileAndNameWithTheLongestSameNamedFunction() {
        fun function(name: String, lines: Int) = arrayOf("fun $name() {", *Array(lines - 2) { "    step($it)" }, "}")
        val path = "a/one/src/commonMain/kotlin/a/b/Long.kt"
        assertTrue(measure(src(path, "package a.b", *function("fits", 100))).of(StructureRule.FUNCTION_LINES).isEmpty())
        assertEquals(
            listOf(Measurement(StructureRule.FUNCTION_LINES, "$path#draw", 120, line = 103)),
            measure(
                src(path, "package a.b", *function("draw", 101), *function("draw", 120), *function("other", 50)),
            ).of(StructureRule.FUNCTION_LINES),
        )
    }

    @Test
    fun packageFilesCountOneSourceDirectoryNotThePackageAcrossSourceSets() {
        fun files(sourceSet: String, count: Int) = Array(count) { kt("a/one", sourceSet, "a.b", "F$it") }
        assertTrue(measure(*files("commonMain", 25)).of(StructureRule.PACKAGE_FILES).isEmpty())
        assertEquals(
            listOf(Measurement(StructureRule.PACKAGE_FILES, "a/one/src/commonMain/kotlin/a/b", 26)),
            measure(*files("commonMain", 26)).of(StructureRule.PACKAGE_FILES),
        )
        assertTrue(measure(*files("commonMain", 13), *files("desktopMain", 13)).of(StructureRule.PACKAGE_FILES).isEmpty())
    }

    @Test
    fun packageDeclarationsMustMatchTheDirectoryInMainAndTestSources() {
        val measured = measure(
            src("a/one/src/commonMain/kotlin/a/b/Main.kt", "package a.c"),
            src("a/one/src/desktopTest/kotlin/a/b/MainTest.kt", "package a"),
            src("a/one/src/commonMain/kotlin/a/b/NoPackage.kt", "val x = 1"),
            src("a/one/src/commonMain/kotlin/Root.kt", "val y = 1"),
            src("a/one/src/commonMain/kotlin/a/b/Fine.kt", "// package a.c", "package a.b"),
        ).of(StructureRule.PACKAGE_DIRECTORY)
        assertEquals(
            listOf(
                "a/one/src/commonMain/kotlin/a/b/Main.kt",
                "a/one/src/commonMain/kotlin/a/b/NoPackage.kt",
                "a/one/src/desktopTest/kotlin/a/b/MainTest.kt",
            ),
            measured.map { it.key },
        )
        assertTrue("declares package 'a.c', directory expects 'a.b'" in measured.first().detail, measured.first().detail)
    }

    @Test
    fun interactionCodeMayImportOnlyTheRenderContractFromTheNucleus() {
        val pkg = "kinetickk.ball.gameplay.interaction.canvas"
        val imports = arrayOf(
            "import kinetickk.ball.gameplay.nucleus.model.clamp",
            "import kinetickk.ball.gameplay.nucleus.simulation.*",
            "import kinetickk.ball.gameplay.nucleus.GameplayNucleus",
            "import kinetickk.ball.gameplay.nucleus.reducer.GameReducer",
            "import kinetickk.ball.gameplay.nucleus.model.clamp as c",
            "import kinetickk.ball.gameplay.nucleus.render.GameplayRenderModel",
            "import kinetickk.ball.gameplay.nucleus.protocol.VisualFxCue",
            "// import kinetickk.ball.gameplay.nucleus.model.formatRunTime",
        )
        val renderer = measure(kt("ball/gameplay/interaction", "commonMain", pkg, "Renderer", *imports))
            .of(StructureRule.RENDERER_NUCLEUS_IMPORTS)
            .single()
        assertEquals(5, renderer.value)
        assertTrue("line 2: import kinetickk.ball.gameplay.nucleus.model.clamp" in renderer.detail, renderer.detail)
        assertTrue("render.GameplayRenderModel" !in renderer.detail, renderer.detail)

        assertTrue(
            measure(
                kt("ball/gameplay/impl", "commonMain", "kinetickk.ball.gameplay.impl", "Owner", *imports),
                kt("ball/gameplay/interaction", "desktopTest", pkg, "RendererTest", *imports),
            ).of(StructureRule.RENDERER_NUCLEUS_IMPORTS).isEmpty(),
        )
    }

    @Test
    fun renderContractFilesMustNotImportOtherNucleusPackages() {
        val pkg = "kinetickk.ball.gameplay.nucleus.render"
        val contract = measure(
            kt("ball/gameplay/nucleus", "commonMain", pkg, "Model", "import kinetickk.ball.gameplay.nucleus.model.X"),
            kt("ball/gameplay/nucleus", "commonMain", pkg, "Fine", "import kinetickk.ball.gameplay.nucleus.protocol.Y"),
        ).of(StructureRule.CONTRACT_IMPORTS)
        assertEquals(listOf("ball/gameplay/nucleus/src/commonMain/kotlin/kinetickk/ball/gameplay/nucleus/render/Model.kt"), contract.map { it.key })
    }

    @Test
    fun featureImplPackagesArePrivateToTheFeatureAndTheCompositionRoot() {
        val module = "ball/profile/interaction"
        val workshop = "kinetickk.ball.profile.interaction.workshop.impl"
        val measured = measure(
            kt(module, "commonMain", workshop, "Two", "import kinetickk.ball.profile.interaction.armory.impl.X", "import kinetickk.ball.profile.interaction.armory.impl.*"),
            kt(module, "commonMain", workshop, "None",
                "import kinetickk.ball.profile.interaction.armory.api.X",
                "import kinetickk.ball.profile.interaction.workshop.impl.sub.X",
                "import org.jetbrains.skia.impl.BufferUtil",
            ),
            kt("app/shared", "commonMain", "kinetickk.app.shared", "Root", "import kinetickk.ball.profile.interaction.armory.impl.X"),
            kt(module, "commonTest", workshop, "TwoTest", "import kinetickk.ball.profile.interaction.armory.impl.X"),
        ).of(StructureRule.FEATURE_IMPL_IMPORTS)
        assertEquals(listOf(2), measured.map { it.value })
        assertTrue(measured.single().key.endsWith("/workshop/impl/Two.kt"))
    }

    @Test
    fun packageCyclesAreCountedPerModuleWithWitnessImports() {
        val cycle = measure(
            kt("a/one", "commonMain", "p.a", "A", "import p.b.B"),
            kt("a/one", "commonMain", "p.b", "B", "import p.a.A"),
        ).of(StructureRule.PACKAGE_CYCLES).single()
        assertEquals(":a:one", cycle.key)
        assertEquals(2, cycle.value)
        assertTrue("p.a -> p.b: a/one/src/commonMain/kotlin/p/a/A.kt:2 import p.b.B" in cycle.detail, cycle.detail)
        assertTrue("p.b -> p.a: a/one/src/commonMain/kotlin/p/b/B.kt:2 import p.a.A" in cycle.detail, cycle.detail)

        assertTrue(
            measure(
                kt("a/one", "commonMain", "p.a", "A", "import p.b.B"),
                kt("a/one", "commonMain", "p.b", "B", "import p.c.C"),
                kt("a/one", "commonMain", "p.c", "C"),
            ).of(StructureRule.PACKAGE_CYCLES).isEmpty(),
        )
        assertTrue(
            measure(
                kt("a/one", "commonMain", "p.a", "A", "import q.b.B"),
                kt("a/two", "commonMain", "q.b", "B", "import p.a.A"),
            ).of(StructureRule.PACKAGE_CYCLES).isEmpty(),
        )
    }

    @Test
    fun simulationDomainsCollapseIntoOneNodeThatStillCannotCycleWithModel() {
        val nucleus = "ball/gameplay/nucleus"
        val simulation = "kinetickk.ball.gameplay.nucleus.simulation"
        assertTrue(
            measure(
                kt(nucleus, "commonMain", "$simulation.combat", "Combat", "import $simulation.enemies.Enemy"),
                kt(nucleus, "commonMain", "$simulation.enemies", "Enemy", "import $simulation.combat.Combat"),
            ).of(StructureRule.PACKAGE_CYCLES).isEmpty(),
        )
        assertEquals(
            2,
            measure(
                kt(nucleus, "commonMain", "kinetickk.ball.gameplay.nucleus.model", "Model", "import $simulation.combat.Combat"),
                kt(nucleus, "commonMain", "$simulation.combat", "Combat", "import kinetickk.ball.gameplay.nucleus.model.Model"),
            ).of(StructureRule.PACKAGE_CYCLES).single().value,
        )
    }

    @Test
    fun importsResolveToTheLongestDeclaredPackagePrefix() {
        assertEquals(
            2,
            measure(
                kt("a/one", "commonMain", "kinetickk.x", "X", "import kinetickk.x.a.Foo.Bar"),
                kt("a/one", "commonMain", "kinetickk.x.a", "Foo", "import kinetickk.x.Baz"),
            ).of(StructureRule.PACKAGE_CYCLES).single().value,
        )
    }

    @Test
    fun ruleConfigCatchesStalePackageConstantsAndSourcesOutsideTheLeafModules() {
        val config = StructureConfig(
            contractPackages = listOf("a.b", "a.gone"),
            cycleTolerantRoots = listOf("a"),
        )
        val measured = measure(
            kt("a/one", "commonMain", "a.b.c", "F"),
            src("tools/x/src/commonMain/kotlin/a/b/G.kt", "package a.b"),
            config = config,
        ).of(StructureRule.RULE_CONFIG)
        assertEquals(listOf("a.gone", "tools/x/src/commonMain/kotlin/a/b/G.kt"), measured.map { it.key })
    }

    @Test
    fun architectureDocumentPathsAndRelativeLinksMustExist() {
        val existing = setOf("ball/gameplay/nucleus", "docs/project/DEVELOPMENT.md", "docs/balance/policy.md", "AGENTS.md")
        val document = listOf(
            "Read `ball/gameplay/nucleus` and `docs/project/DEVELOPMENT.md:12-30` and `AGENTS.md`.",
            "Placeholders like `⟨ball/gameplay/nucleus/simulation/core⟩` and `ball/…/HudRenderer.kt` are skipped.",
            "Symbols like `kinetickk.ball.gameplay` and `GameplayNucleus` are not paths.",
            "Missing: `ball/gameplay/missing/File.kt#section`.",
            "Links: [policy](../balance/policy.md), [dev](DEVELOPMENT.md#build), [web](https://example.com), [top](#top),",
            "and a broken one: [gone](GONE.md).",
        ).joinToString("\n")
        val measured = measure(document = document, exists = { it in existing }).of(StructureRule.ARCHITECTURE_DOC_PATHS)
        assertEquals(
            listOf("ball/gameplay/missing/File.kt" to 4, "docs/project/GONE.md" to 6),
            measured.map { it.key to it.line },
        )
    }

    @Test
    fun aMissingArchitectureDocumentIsNotCheckedYet() {
        assertTrue(measure(document = null).of(StructureRule.ARCHITECTURE_DOC_PATHS).isEmpty())
    }

    @Test
    fun unbalancedBracesAfterMaskingFailTheFunctionScan() {
        val measured = measure(src("a/one/src/commonMain/kotlin/a/b/Broken.kt", "package a.b", "fun f() {", "    if (x) {", "}"))
        assertEquals(listOf("a/one/src/commonMain/kotlin/a/b/Broken.kt"), measured.of(StructureRule.FUNCTION_SCAN).map { it.key })
    }
}
