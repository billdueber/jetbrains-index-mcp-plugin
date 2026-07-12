package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.ruby

import junit.framework.TestCase

/**
 * Pure (non-fixture) unit tests for the pure-logic helpers used by
 * [RubyStructureHandler].
 *
 * These run without the Ruby plugin — no PSI, no IDE.
 * They test the boundary conditions that could break when
 * real Ruby PSI data hits the algorithm.
 *
 * **What cannot be tested here** (needs Ruby plugin):
 *   - Reflection calls to RClass.getStructureElements()
 *   - PsiTreeUtil.findChildrenOfType with Ruby PSI classes
 *   - Actual file structure extraction from parsed Ruby files
 *
 * See [RubyStructureHandlerPlatformTest] for those.
 */
class RubyStructureHandlerUnitTest : TestCase() {

    // ── Method signature regex ────────────────────────────────────────────────

    // The handler uses this pattern to extract parameters from method text
    private val methodParamPattern = Regex("""def\s+\S+\s*\(([^)]*)\)""")

    fun testMethodSignatureWithParameters() {
        val text = "  def add(x, y)\n    x + y\n  end"
        val match = methodParamPattern.find(text)
        assertNotNull("Pattern should match method with params", match)
        assertEquals("Should extract params, got: ${match!!.groupValues[1]}", "x, y", match.groupValues[1])
    }

    fun testMethodSignatureWithNoParams() {
        val text = "  def greet()\n    \"hello\"\n  end"
        val match = methodParamPattern.find(text)
        assertNotNull("Pattern should match method with empty parens", match)
        assertEquals("Should extract empty string, got: ${match!!.groupValues[1]}", "", match.groupValues[1])
    }

    fun testMethodSignatureWithDefaultValue() {
        val text = "  def greet(name = \"world\")\n    \"hello #{name}\"\n  end"
        val match = methodParamPattern.find(text)
        assertNotNull("Pattern should match method with default value", match)
        assertEquals("Should extract params with defaults, got: ${match!!.groupValues[1]}",
            "name = \"world\"", match.groupValues[1])
    }

    fun testMethodSignatureWithSplat() {
        val text = "  def sum(*args)\n    args.sum\n  end"
        val match = methodParamPattern.find(text)
        assertNotNull("Pattern should match method with splat", match)
        assertEquals("Should extract splat params, got: ${match!!.groupValues[1]}", "*args", match.groupValues[1])
    }

    fun testMethodSignatureWithKeywordArg() {
        val text = "  def configure(env:, debug: false)\n  end"
        val match = methodParamPattern.find(text)
        assertNotNull("Pattern should match method with keyword args", match)
        assertEquals("Should extract keyword args, got: ${match!!.groupValues[1]}",
            "env:, debug: false", match.groupValues[1])
    }

    fun testMethodSignatureWithBlockArg() {
        val text = "  def process(&block)\n    block.call\n  end"
        val match = methodParamPattern.find(text)
        assertNotNull("Pattern should match method with block arg", match)
        assertEquals("Should extract block param, got: ${match!!.groupValues[1]}", "&block", match.groupValues[1])
    }

    fun testMethodSignatureNoParens() {
        val text = "  def compute\n    add(1, 2)\n  end"
        val match = methodParamPattern.find(text)
        assertNull("Pattern should NOT match method without parens", match)
    }

    fun testMethodSignatureClassMethod() {
        val text = "  def self.square(x)\n    x * x\n  end"
        val match = methodParamPattern.find(text)
        assertNotNull("Pattern should match class method with params", match)
        assertEquals("Should extract params, got: ${match!!.groupValues[1]}", "x", match.groupValues[1])
    }

    fun testMethodSignaturePredicateMethod() {
        val text = "  def admin?\n    true\n  end"
        val match = methodParamPattern.find(text)
        assertNull("Pattern should NOT match predicate method without parens", match)
    }

    fun testMethodSignatureBangMethod() {
        val text = "  def save!\n    true\n  end"
        val match = methodParamPattern.find(text)
        assertNull("Pattern should NOT match bang method without parens", match)
    }

    fun testMethodSignatureOneLineBody() {
        val text = "  def method_one; end"
        val match = methodParamPattern.find(text)
        assertNull("Pattern should NOT match one-line method without parens", match)
    }

    fun testMethodSignatureComplexDefault() {
        val text = "  def process(name: nil, timeout: DEFAULT_TIMEOUT, retries: 3)\n  end"
        val match = methodParamPattern.find(text)
        assertNotNull("Pattern should match method with multiple keyword args", match)
        assertEquals("Should extract all keyword args, got: ${match!!.groupValues[1]}",
            "name: nil, timeout: DEFAULT_TIMEOUT, retries: 3", match.groupValues[1])
    }

    // ── Class method modifier detection ───────────────────────────────────────

    private fun detectSelfModifier(text: String): List<String> {
        val modifiers = mutableListOf<String>()
        if (text.startsWith("def self.")) {
            modifiers.add("self")
        }
        return modifiers
    }

    fun testClassMethodHasSelfModifier() {
        val result = detectSelfModifier("def self.square(x)")
        assertTrue("Class method should have 'self' modifier, got: $result", result.contains("self"))
    }

    fun testInstanceMethodHasNoSelfModifier() {
        val result = detectSelfModifier("def add(x, y)")
        assertTrue("Instance method should have no modifier, got: $result", result.isEmpty())
    }

    fun testClassMethodWithPredicate() {
        val result = detectSelfModifier("def self.admin?")
        assertTrue("Class predicate method should have 'self' modifier, got: $result", result.contains("self"))
    }

    // ── FQN reconstruction ────────────────────────────────────────────────────

    private fun reconstructFqn(name: String, ancestorNames: List<String>): String {
        val reversed = ancestorNames.reversed()
        if (reversed.isEmpty()) return name
        val prefix = reversed.joinToString("::")
        return if (name.isEmpty()) prefix else "$prefix::$name"
    }

    fun testReconstructFqnReturnsNameWhenNoAncestors() {
        assertEquals("FQN with no ancestors should be bare name",
            "User", reconstructFqn("User", emptyList()))
    }

    fun testReconstructFqnForNestedModule() {
        assertEquals("FQN for class in module",
            "Outer::Inner::Nested", reconstructFqn("Nested", listOf("Inner", "Outer")))
    }

    fun testReconstructFqnWithEmptyName() {
        assertEquals("FQN with empty name",
            "Outer", reconstructFqn("", listOf("Outer")))
    }
}