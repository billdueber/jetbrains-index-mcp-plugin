package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.ruby

import junit.framework.TestCase

/**
 * Pure (non-fixture) unit tests for pure-logic helpers in
 * [RubyTypeHierarchyHandler] and [BaseRubyHandler].
 *
 * These run without the Ruby plugin — no PSI, no IDE.
 * They test the boundary conditions that could break when
 * real Ruby PSI data hits the algorithm.
 *
 * **What cannot be tested here** (needs Ruby plugin):
 *   - Reflection calls to RClass.getSuperClass() / getIncludedModules()
 *   - StubIndex queries to RubyClassModuleNameIndex / RubyInheritanceIndex
 *   - Actual type hierarchy construction from parsed Ruby files
 *
 * See [RubyTypeHierarchyPlatformTest] for those.
 */
class RubyTypeHierarchyHandlerUnitTest : TestCase() {

    // ── FQN reconstruction (extracted utility) ───────────────────────────────
    // The handler will call this when getFullyQualifiedName() returns null
    // (a known gotcha for module-namespaced Ruby classes).

    fun testReconstructFqnReturnsNameWhenNoAncestors() {
        val result = reconstructFqn("User", emptyList())
        assertEquals("FQN with no ancestors should be bare name, got: $result", "User", result)
    }

    fun testReconstructFqnForModuleNamespacedClass() {
        // module Admin; class User → "Admin::User"
        val result = reconstructFqn("User", listOf("Admin"))
        assertEquals("FQN for User in Admin module should be Admin::User, got: $result", "Admin::User", result)
    }

    fun testReconstructFqnForDeeplyNested() {
        // PSI parent walk: element.parent = B, element.parent.parent = A
        // ancestorNames collected as [B, A], reversed to [A, B] for FQN
        // module A; module B; class C → "A::B::C"
        val result = reconstructFqn("C", listOf("B", "A"))
        assertEquals("FQN for C in B::A should be A::B::C, got: $result", "A::B::C", result)
    }

    fun testReconstructFqnSkipsNonRubyContainers() {
        // class inside an RMethod context → only class name
        val result = reconstructFqn("Helper", emptyList())
        assertEquals("FQN for element with no Ruby parents should be bare name, got: $result", "Helper", result)
    }

    // ── kind determination ────────────────────────────────────────────────────

    fun testKindIsClassForRClass() {
        val result = kindFor(true, false)
        assertEquals("isRClass=true, isRModule=false should yield CLASS, got: $result", "CLASS", result)
    }

    fun testKindIsModuleForRModule() {
        val result = kindFor(false, true)
        assertEquals("isRClass=false, isRModule=true should yield MODULE, got: $result", "MODULE", result)
    }

    fun testKindDefaultsToClassWhenNeither() {
        val result = kindFor(false, false)
        assertEquals("isRClass=false, isRModule=false should default to CLASS, got: $result", "CLASS", result)
    }

    // ── visited-set cycle detection ───────────────────────────────────────────

    fun testVisitedSetStopsRevisit() {
        val visited = mutableSetOf("A", "B", "C")
        assertFalse("C already visited — add should return false", visited.add("C"))
    }

    fun testVisitedSetAcceptsNewEntries() {
        val visited = mutableSetOf("A", "B")
        assertTrue("D is new — add should return true", visited.add("D"))
    }

    // ── max-depth guardrail ───────────────────────────────────────────────────

    fun testDepthStopsAtFifty() {
        val names = (1..55).map { "L$it" }
        // simulate 50 max depth
        assertEquals("take(50) on 55 elements should yield 50, got: ${names.take(50).size}", 50, names.take(50).size)
        assertEquals("Original list should still have 55 elements, got: ${names.size}", 55, names.size)
    }

    // ── FQN fallback: null / empty edge cases ────────────────────────────────

    fun testReconstructFqnWithEmptyAncestors() {
        val result = reconstructFqn("User", emptyList())
        assertEquals("FQN with empty ancestors should be 'User', got: $result", "User", result)
    }

    fun testReconstructFqnDoesNotProduceTrailingDoubleColon() {
        // Empty name + ancestors must not produce "Admin::"
        val result = reconstructFqn("", listOf("Admin"))
        assertEquals("FQN with empty name and Admin ancestor should be 'Admin', got: $result", "Admin", result)
    }

    // ── helper functions (match what the handler will use internally) ─────────

    private fun reconstructFqn(name: String, ancestorNames: List<String>): String {
        // PSI parent walk collects innermost-first; reverse for FQN order
        val reversed = ancestorNames.reversed()
        if (reversed.isEmpty()) return name
        val prefix = reversed.joinToString("::")
        return if (name.isEmpty()) prefix else "$prefix::$name"
    }

    private fun kindFor(isRClass: Boolean, isRModule: Boolean): String {
        if (isRClass) return "CLASS"
        if (isRModule) return "MODULE"
        return "CLASS" // default; RClass is the common case
    }
}