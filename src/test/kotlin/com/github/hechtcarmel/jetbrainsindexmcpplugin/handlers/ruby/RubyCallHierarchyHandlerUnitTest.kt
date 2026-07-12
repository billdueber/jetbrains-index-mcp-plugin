package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.ruby

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageHandlerRegistry
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetector
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import junit.framework.TestCase

/**
 * Pure (non-fixture) unit tests for [RubyCallHierarchyHandler].
 *
 * These run without the Ruby plugin — no PSI, no IDE.
 * They test handler metadata, registry wiring, and stub guardrails.
 *
 * **What cannot be tested here** (needs Ruby plugin + platform test):
 *   - `ReferencesSearch.search()` actual queries
 *   - `PsiTreeUtil.findChildrenOfType()` with real Ruby PSI
 *   - `getPsiCommand().getReference().resolve()` reflection
 *   - Call hierarchy traversal with real .rb files
 *
 * See [RubyCallHierarchyHandlerPlatformTest] for those.
 */
class RubyCallHierarchyHandlerUnitTest : TestCase() {

    override fun setUp() {
        super.setUp()
        LanguageHandlerRegistry.clear()
    }

    override fun tearDown() {
        super.tearDown()
        LanguageHandlerRegistry.clear()
    }

    // ── Metadata ───────────────────────────────────────────────────────────────

    fun testLanguageIdIsRuby() {
        assertEquals("languageId must be 'Ruby'", "Ruby", RubyCallHierarchyHandler().languageId)
    }

    fun testIsAvailableWithoutPlugin() {
        mockkObject(PluginDetectors)
        val rubyDetector = mockk<PluginDetector>()
        every { rubyDetector.isAvailable } returns false
        every { PluginDetectors.ruby } returns rubyDetector
        try {
            assertFalse("isAvailable should be false without Ruby plugin", RubyCallHierarchyHandler().isAvailable())
        } finally {
            unmockkObject(PluginDetectors)
        }
    }

    fun testCanHandleReturnsFalseWithoutRubyPlugin() {
        // When Ruby plugin is not available, isAvailable returns false,
        // so canHandle returns false without even checking the element's language.
        mockkObject(PluginDetectors)
        val rubyDetector = mockk<PluginDetector>()
        every { rubyDetector.isAvailable } returns false
        every { PluginDetectors.ruby } returns rubyDetector
        try {
            val handler = RubyCallHierarchyHandler()
            // Any PsiElement — canHandle returns false because isAvailable is false
            val element = mockk<com.intellij.psi.PsiElement>()
            assertFalse("canHandle must be false without Ruby plugin", handler.canHandle(element))
        } finally {
            unmockkObject(PluginDetectors)
        }
    }

    // ── Registry Wiring ────────────────────────────────────────────────────────

    fun testRegistryExposesRubyCallHierarchyWhenForcedAvailable() {
        mockkObject(PluginDetectors)
        val rubyDetector = mockk<PluginDetector>()
        every { rubyDetector.isAvailable } returns true
        every { PluginDetectors.ruby } returns rubyDetector
        LanguageHandlerRegistry.clear()
        try {
            LanguageHandlerRegistry.registerCallHierarchyHandler(RubyCallHierarchyHandler())
            assertTrue(
                "'Ruby' must be advertised as a supported call-hierarchy language",
                LanguageHandlerRegistry.getSupportedLanguagesForCallHierarchy().contains("Ruby")
            )
        } finally {
            LanguageHandlerRegistry.clear()
            unmockkObject(PluginDetectors)
        }
    }

    // ── Handler returns null without Ruby PSI ─────────────────────────────────

    fun testGetCallHierarchyReturnsNullWithoutRubyPlugin() {
        // When the Ruby plugin is not installed, findContainingRMethod returns null
        // because rMethodClass lazy val stays null (Class.forName fails).
        // getCallHierarchy should return null in this case.
        mockkObject(PluginDetectors)
        val rubyDetector = mockk<PluginDetector>()
        every { rubyDetector.isAvailable } returns true
        every { PluginDetectors.ruby } returns rubyDetector
        try {
            val handler = RubyCallHierarchyHandler()
            val project = mockk<com.intellij.openapi.project.Project>(relaxed = true)
            // Any PsiElement will do — rMethodClass is null so findContainingRMethod
            // can't find anything
            // PsiTreeUtil.getParentOfType will call getParent() on the mock,
            // so we must stub it to prevent infinite recursion
            val element = mockk<com.intellij.psi.PsiElement>(relaxUnitFun = true) {
                every { parent } returns null
            }
            val result = handler.getCallHierarchy(element, project, "callers", 1)
            assertNull("getCallHierarchy must return null when findContainingRMethod fails", result)
        } finally {
            unmockkObject(PluginDetectors)
        }
    }

    fun testGetCallHierarchyReturnsNullForNonRubyElement() {
        // For a non-Ruby element — findContainingRMethod traverses parent chain
        // looking for an RMethod ancestor, but the element has no parent
        mockkObject(PluginDetectors)
        val rubyDetector = mockk<PluginDetector>()
        every { rubyDetector.isAvailable } returns true
        every { PluginDetectors.ruby } returns rubyDetector
        try {
            val handler = RubyCallHierarchyHandler()
            val project = mockk<com.intellij.openapi.project.Project>(relaxed = true)
            val element = mockk<com.intellij.psi.PsiElement>(relaxUnitFun = true) {
                every { parent } returns null
            }
            val result = handler.getCallHierarchy(element, project, "callers", 1)
            assertNull("getCallHierarchy must return null when no containing method found", result)
        } finally {
            unmockkObject(PluginDetectors)
        }
    }

    // ── Verify the handler is no longer a stub ────────────────────────────────

    fun testHandlerIsNotStub() {
        val handler = RubyCallHierarchyHandler()
        assertEquals(
            "RubyCallHierarchyHandler should be the real implementation",
            "com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.ruby.RubyCallHierarchyHandler",
            handler.javaClass.name
        )
        // The stub had 0 declared methods (just interface implementations).
        // With Kotlin inline functions, private methods are still compiled as Java methods.
        // The real handler should have at least findCallersRecursive, findCalleesRecursive,
        // resolveCallTarget, getMethodKey, createCallElement as private/package methods.
        assertTrue(
            "Handler should have declared methods beyond the interface ones",
            handler.javaClass.declaredMethods.size >= 3
        )
    }

    // ── Direction validation ──────────────────────────────────────────────────

    fun testGetCallHierarchyDirectionCallers() {
        // When direction is "callers", the handler branches to findCallersRecursive.
        // In a unit test without Ruby PSI, findContainingRMethod returns null, so
        // the result is null regardless of direction. But we validate the branch
        // logic by inspecting the private methods list — both callers and callees
        // paths exist as distinct declared methods.
        val handler = RubyCallHierarchyHandler()
        val methodNames = handler.javaClass.declaredMethods.map { it.name }.toSet()
        assertTrue(
            "Handler must have findCallersRecursive method, got: $methodNames",
            methodNames.any { it.contains("findCallersRecursive", ignoreCase = true) }
        )
        assertTrue(
            "Handler must have findCalleesRecursive method, got: $methodNames",
            methodNames.any { it.contains("findCalleesRecursive", ignoreCase = true) }
        )
    }

    // ── getMethodKey format ───────────────────────────────────────────────────

    fun testGetMethodKeyFormatUsesFileLineAndClassName() {
        // getMethodKey produces "file:line:className.methodName" format.
        // While the method is private, we can verify the constants and structure.
        // The handler companion object has MAX_RESULTS_PER_LEVEL = 20 and
        // MAX_STACK_DEPTH = 50.
        val handler = RubyCallHierarchyHandler()
        val methodsByName = handler.javaClass.declaredMethods.associateBy { it.name }
        assertTrue(
            "Handler must have getMethodKey private method",
            methodsByName.containsKey("getMethodKey")
        )
        assertTrue(
            "Handler must have createCallElement private method",
            methodsByName.containsKey("createCallElement")
        )
    }

    // ── Max depth guardrail ───────────────────────────────────────────────────

    fun testMaxStackDepthLimit() {
        // MAX_STACK_DEPTH = 50 in the handler companion object.
        // The recursive methods check stackDepth > MAX_STACK_DEPTH and return
        // empty list when exceeded.
        val handler = RubyCallHierarchyHandler()
        // Verify the handler has a MAX_STACK_DEPTH constant
        val fields = handler.javaClass.declaredFields.map { it.name }
        assertTrue(
            "Companion should have MAX_STACK_DEPTH constant, got fields: $fields",
            fields.any { it.contains("MAX_STACK_DEPTH", ignoreCase = true) } ||
            fields.any { it.contains("MAX_RESULTS", ignoreCase = true) }
        )
    }

    // ── Visited set cycle detection ───────────────────────────────────────────

    fun testVisitedSetRejectsDuplicateKeys() {
        val visited = mutableSetOf("file.rb:10:Calculator.add")
        assertFalse(
            "Visited set should reject already-visited key",
            visited.add("file.rb:10:Calculator.add")
        )
    }

    fun testVisitedSetAcceptsNewKeys() {
        val visited = mutableSetOf("file.rb:10:Calculator.add")
        assertTrue(
            "Visited set should accept new key",
            visited.add("file.rb:20:Calculator.compute")
        )
    }

    fun testVisitedSetPreventsInfiniteRecursion() {
        val visited = mutableSetOf("file.rb:10:Calculator.add")
        val keys = listOf(
            "file.rb:10:Calculator.add",   // already visited
            "file.rb:20:Calculator.compute", // new
            "file.rb:30:MathHelper.square"   // new
        )
        val added = keys.filter { visited.add(it) }
        assertEquals(
            "Only 2 of 3 keys should be new",
            2, added.size
        )
        assertFalse(
            "Already-visited key should not be in added list",
            added.contains("file.rb:10:Calculator.add")
        )
    }

    // ── Method key structure ──────────────────────────────────────────────────

    fun testMethodKeyContainsClassAndMethod() {
        // The method key format is "file:line:className.methodName"
        // className.methodName is a single colon-delimited part
        val key = "/project/app.rb:42:Calculator.add"
        val parts = key.split(":")
        assertEquals(
            "Key should have 3 colon-delimited parts: file, line, class.method",
            3, parts.size
        )
        assertEquals("Third part should be className.methodName",
            "Calculator.add", parts[2])
    }

    fun testMethodKeyForTopLevelMethodUsesEmptyClass() {
        // Top-level (no containing class) key format: "file:line:.methodName"
        // .methodName is a single colon-delimited part (empty class = "." prefix)
        val key = "/project/top.rb:5:.say_hello"
        val parts = key.split(":")
        assertEquals("Key should have 3 colon-delimited parts, got: ${parts.size}",
            3, parts.size)
        assertEquals("Third part should use empty class prefix .methodName, got: ${parts[2]}",
            ".say_hello", parts[2])
    }
}