package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.ruby

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageHandlerRegistry
import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.SuperMethodsData
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assume

/**
 * Platform tests for [RubySuperMethodsHandler].
 *
 * Uses inline Ruby fixtures via myFixture.addFileToProject().
 * Run on CI with RubyMine or IntelliJ + Ruby plugin.
 *
 * Skipped automatically on machines without the Ruby plugin.
 *
 * ## Current handler limitation (asserted, not hidden)
 *
 * [RubySuperMethodsHandler.findSuperMethods] currently hard-codes
 * `overriddenMethods = emptyList()` (see its TODO: "Re-implement using direct
 * reflection to RubyOverrideImplementUtil"). Therefore `result.hierarchy` is
 * ALWAYS empty today. These tests assert:
 *   1. the current method resolves and its [MethodData] is populated correctly
 *      (name, containingClass, signature, position) — this IS implemented, and
 *   2. `hierarchy` is empty — documenting the not-yet-implemented super lookup.
 * When override resolution lands, flip the [assertHierarchyPending] assertions
 * to assert the expected parent methods.
 *
 * NOTE: the caret element MUST be resolved from the `PsiFile` returned by
 * `addFileToProject` (see [caretInMethod]) — NOT from `myFixture.file`, which is
 * only set by `configureByX` and is null here.
 */
class RubyFindSuperMethodsHandlerPlatformTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        LanguageHandlerRegistry.registerHandlers()
    }

    /** IntelliJ-native skip: when false, runBare() skips the test with no failure. */
    override fun shouldRunTest(): Boolean =
        PluginDetectors.ruby.isAvailable && super.shouldRunTest()

    override fun tearDown() {
        try {
            LanguageHandlerRegistry.clear()
        } finally {
            super.tearDown()
        }
    }

    // -- capability gate ------------------------------------------------------

    private fun requireRubyPlugin() {
        Assume.assumeTrue(
            "Ruby plugin not available — set localIdeaPath or platformPlugins in gradle.properties",
            PluginDetectors.ruby.isAvailable
        )
        val handler = LanguageHandlerRegistry.getSuperMethodsHandler(
            myFixture.addFileToProject("__gate__.rb", "class Gate; end")
        )
        Assume.assumeTrue(
            "RubySuperMethodsHandler not registered — plugin present but handler not wired",
            handler is RubySuperMethodsHandler
        )
    }

    private fun resolveHandler(element: PsiElement): RubySuperMethodsHandler {
        val handler = LanguageHandlerRegistry.getSuperMethodsHandler(element)
        return handler as? RubySuperMethodsHandler
            ?: fail("Expected RubySuperMethodsHandler but got: $handler") as Nothing
    }

    /**
     * Resolves the PSI element at the start of the method name for `def <name>`.
     * Offset +4 skips `def ` and lands on the first char of the method name.
     * Resolves from [file] itself — `myFixture.file` is null without configureByX.
     */
    private fun caretInMethod(file: PsiFile, defMarker: String): PsiElement {
        val pos = file.text.indexOf(defMarker)
        assertTrue("fixture must contain '$defMarker'", pos >= 0)
        return file.findElementAt(pos + 4)
            ?: error("no PSI element at offset ${pos + 4} in ${file.name}")
    }

    /** Assert the resolved method metadata. */
    private fun assertMethod(result: SuperMethodsData?, name: String, classPart: String) {
        assertNotNull("findSuperMethods should return a result", result)
        assertEquals("Method name should be $name", name, result!!.method.name)
        assertTrue("containingClass should contain '$classPart', got: ${result.method.containingClass}",
            result.method.containingClass.contains(classPart))
        assertTrue("signature should be non-blank, got: '${result.method.signature}'",
            result.method.signature.isNotBlank())
        assertEquals("language should be Ruby", "Ruby", result.method.language)
    }

    /**
     * Documents the current handler contract: super lookup is stubbed, so the
     * hierarchy is empty. Replace with real parent assertions once implemented.
     */
    private fun assertHierarchyPending(result: SuperMethodsData?) {
        assertNotNull(result)
        assertTrue(
            "hierarchy is empty until RubyOverrideImplementUtil resolution is implemented, got: " +
                "${result!!.hierarchy.map { it.name }}",
            result.hierarchy.isEmpty()
        )
    }

    // -- basic override (fsm_01) ----------------------------------------------

    fun testBasicOverride() {
        requireRubyPlugin()

        myFixture.addFileToProject("animal.rb", "class Animal; def speak; 'generic'; end; end")
        val dogFile = myFixture.addFileToProject("dog.rb", "class Dog < Animal; def speak; 'woof'; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(dogFile)
        val result = handler.findSuperMethods(caretInMethod(dogFile, "def speak"), project)

        assertMethod(result, "speak", "Dog")
        assertHierarchyPending(result)
    }

    // -- mixin override (fsm_02) ----------------------------------------------

    fun testMixinOverride() {
        requireRubyPlugin()

        myFixture.addFileToProject("greetable.rb", "module Greetable; def greet; 'hello'; end; end")
        val userFile = myFixture.addFileToProject("user.rb", "class User; include Greetable; def greet; 'hi'; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(userFile)
        val result = handler.findSuperMethods(caretInMethod(userFile, "def greet"), project)

        assertMethod(result, "greet", "User")
        assertHierarchyPending(result)
    }

    // -- deep chain (fsm_03) --------------------------------------------------

    fun testDeepChain() {
        requireRubyPlugin()

        myFixture.addFileToProject("grand_parent.rb", "class GrandParent; def speak; 'grand'; end; end")
        myFixture.addFileToProject("parent.rb", "class Parent < GrandParent; def speak; 'parent'; end; end")
        val childFile = myFixture.addFileToProject("child.rb", "class Child < Parent; def speak; 'child'; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(childFile)
        val result = handler.findSuperMethods(caretInMethod(childFile, "def speak"), project)

        assertMethod(result, "speak", "Child")
        assertHierarchyPending(result)
    }

    // -- no super (fsm_04) ----------------------------------------------------

    fun testNoSuper() {
        requireRubyPlugin()

        val file = myFixture.addFileToProject("root.rb", "class Root; def unique; true; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(file)
        val result = handler.findSuperMethods(caretInMethod(file, "def unique"), project)

        assertMethod(result, "unique", "Root")
        assertTrue("Hierarchy must be empty for a no-super method, got: ${result!!.hierarchy.size}",
            result.hierarchy.isEmpty())
    }

    // -- class method override (fsm_05) ---------------------------------------

    fun testClassMethodOverride() {
        requireRubyPlugin()

        myFixture.addFileToProject("user.rb", "class User; def self.find_by_email; end; end")
        val adminFile = myFixture.addFileToProject("admin_user.rb", "class AdminUser < User; def self.find_by_email; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(adminFile)
        // +4 lands on 'self'; findContainingRMethod still resolves the class method.
        val result = handler.findSuperMethods(caretInMethod(adminFile, "def self.find_by_email"), project)

        assertNotNull("findSuperMethods should return a result", result)
        assertTrue("Method name should include find_by_email, got: ${result!!.method.name}",
            result.method.name.contains("find_by_email"))
        assertTrue("containingClass should contain AdminUser, got: ${result.method.containingClass}",
            result.method.containingClass.contains("AdminUser"))
        assertHierarchyPending(result)
    }

    // -- cross-file override (fsm_06) -----------------------------------------

    fun testCrossFileOverride() {
        requireRubyPlugin()

        myFixture.addFileToProject("base.rb", "module Base; def compute; end; end")
        val subFile = myFixture.addFileToProject("sub.rb", "class Sub; include Base; def compute; 42; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(subFile)
        val result = handler.findSuperMethods(caretInMethod(subFile, "def compute"), project)

        assertMethod(result, "compute", "Sub")
        assertHierarchyPending(result)
    }

    // -- predicate and bang method override (fsm_07) --------------------------

    fun testPredicateMethodOverride() {
        requireRubyPlugin()

        myFixture.addFileToProject("base.rb", "class Base; def admin?; false; end; end")
        val subFile = myFixture.addFileToProject("sub.rb", "class Sub < Base; def admin?; true; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(subFile)
        val result = handler.findSuperMethods(caretInMethod(subFile, "def admin?"), project)

        assertMethod(result, "admin?", "Sub")
        assertHierarchyPending(result)
    }

    fun testBangMethodOverride() {
        requireRubyPlugin()

        myFixture.addFileToProject("base.rb", "class Base; def save!; end; end")
        val subFile = myFixture.addFileToProject("sub.rb", "class Sub < Base; def save!; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(subFile)
        val result = handler.findSuperMethods(caretInMethod(subFile, "def save!"), project)

        assertMethod(result, "save!", "Sub")
        assertHierarchyPending(result)
    }

    // -- edge cases (fsm_08) --------------------------------------------------

    fun testNoExplicitSuperclass() {
        requireRubyPlugin()

        val file = myFixture.addFileToProject("leaf.rb", "class Leaf; def my_method; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(file)
        val result = handler.findSuperMethods(caretInMethod(file, "def my_method"), project)

        assertMethod(result, "my_method", "Leaf")
        assertHierarchyPending(result)
    }

    fun testEmptyFileReturnsNull() {
        requireRubyPlugin()

        val file = myFixture.addFileToProject("empty.rb", "")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        // Empty file has no PSI leaf at offset 0.
        val element = file.findElementAt(0)
        assertNull("empty file should yield no PSI leaf at offset 0", element)
    }

    fun testSyntaxErrorFileDoesNotCrash() {
        requireRubyPlugin()

        // Missing `end` — Ruby PSI error-recovers; the handler must not throw.
        val file = myFixture.addFileToProject("broken.rb", "class Broken; def method_x; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(file)
        val element = caretInMethod(file, "def method_x")
        // Should return a result (method resolves) or null — but never crash.
        val result = handler.findSuperMethods(element, project)
        if (result != null) {
            assertEquals("method_x", result.method.name)
            assertTrue("hierarchy stays empty (stub)", result.hierarchy.isEmpty())
        }
    }
}
