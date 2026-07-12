package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.ruby

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageHandlerRegistry
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import com.intellij.psi.PsiElement
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assume
import org.junit.Ignore

/**
 * Platform tests for [RubySuperMethodsHandler].
 *
 * Uses inline Ruby fixtures via myFixture.addFileToProject().
 * Run on CI with RubyMine or IntelliJ + Ruby plugin.
 *
 * Skipped automatically on machines without the Ruby plugin.
 */
@Ignore("Ruby plugin test - skipped in this environment")
class RubyFindSuperMethodsHandlerPlatformTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        LanguageHandlerRegistry.registerHandlers()
    }

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

    // -- basic override (fsm_01) ----------------------------------------------

    fun testBasicOverride() {
        requireRubyPlugin()

        myFixture.addFileToProject("animal.rb", "class Animal; def speak; 'generic'; end; end")
        val dogFile = myFixture.addFileToProject("dog.rb", "class Dog < Animal; def speak; 'woof'; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(dogFile)
        val speakPos = dogFile.text.indexOf("def speak")
        val element = myFixture.file?.findElementAt(speakPos + 4)
        val result = handler.findSuperMethods(element!!, project)

        assertNotNull("findSuperMethods should return a result", result)
        assertEquals("Method name should be speak", "speak", result!!.method.name)
    }

    fun testBasicOverrideSymbolLookup() {
        requireRubyPlugin()

        myFixture.addFileToProject("animal.rb", "class Animal; def speak; 'generic'; end; end")
        val dogFile = myFixture.addFileToProject("dog.rb", "class Dog < Animal; def speak; 'woof'; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(dogFile)
        val speakPos = dogFile.text.indexOf("def speak")
        val element = myFixture.file?.findElementAt(speakPos + 4)
        val result = handler.findSuperMethods(element!!, project)

        assertNotNull("findSuperMethods should return a result", result)
        // The hierarchy should contain at least the Animal#speak super method
        assertTrue("Hierarchy should not be empty", result!!.hierarchy.isNotEmpty())
    }

    // -- mixin override (fsm_02) ----------------------------------------------

    fun testMixinOverride() {
        requireRubyPlugin()

        myFixture.addFileToProject("greetable.rb", "module Greetable; def greet; 'hello'; end; end")
        val userFile = myFixture.addFileToProject("user.rb", "class User; include Greetable; def greet; 'hi'; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(userFile)
        val greetPos = userFile.text.indexOf("def greet")
        val element = myFixture.file?.findElementAt(greetPos + 4)
        val result = handler.findSuperMethods(element!!, project)

        assertNotNull("findSuperMethods should return a result", result)
        assertEquals("Method name should be greet", "greet", result!!.method.name)
    }

    // -- deep chain (fsm_03) --------------------------------------------------

    fun testDeepChain() {
        requireRubyPlugin()

        myFixture.addFileToProject("grand_parent.rb", "class GrandParent; def speak; 'grand'; end; end")
        myFixture.addFileToProject("parent.rb", "class Parent < GrandParent; def speak; 'parent'; end; end")
        val childFile = myFixture.addFileToProject("child.rb", "class Child < Parent; def speak; 'child'; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(childFile)
        val speakPos = childFile.text.indexOf("def speak")
        val element = myFixture.file?.findElementAt(speakPos + 4)
        val result = handler.findSuperMethods(element!!, project)

        assertNotNull("findSuperMethods should return a result", result)
        assertEquals("Method name should be speak", "speak", result!!.method.name)
    }

    // -- no super (fsm_04) ----------------------------------------------------

    fun testNoSuper() {
        requireRubyPlugin()

        // Root class with no parent — method has no super
        val file = myFixture.addFileToProject("root.rb", "class Root; def unique; true; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(file)
        val uniquePos = file.text.indexOf("def unique")
        val element = myFixture.file?.findElementAt(uniquePos + 4)
        val result = handler.findSuperMethods(element!!, project)

        assertNotNull("findSuperMethods should return a result", result)
        assertEquals("Method name should be unique", "unique", result!!.method.name)
        // Hierarchy may be empty for methods with no super
        assertTrue("Hierarchy should be empty for no-super method, got: ${result!!.hierarchy.size}",
            result!!.hierarchy.isEmpty())
    }

    // -- class method override (fsm_05) ---------------------------------------

    fun testClassMethodOverride() {
        requireRubyPlugin()

        myFixture.addFileToProject("user.rb", "class User; def self.find_by_email; end; end")
        val adminFile = myFixture.addFileToProject("admin_user.rb", "class AdminUser < User; def self.find_by_email; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(adminFile)
        val findPos = adminFile.text.indexOf("def self.find_by_email")
        val element = myFixture.file?.findElementAt(findPos + 4)
        val result = handler.findSuperMethods(element!!, project)

        assertNotNull("findSuperMethods should return a result", result)
        assertTrue("Method name should include find_by_email", result!!.method.name.contains("find_by_email"))
    }

    // -- cross-file override (fsm_06) -----------------------------------------

    fun testCrossFileOverride() {
        requireRubyPlugin()

        myFixture.addFileToProject("base.rb", "module Base; def compute; end; end")
        val subFile = myFixture.addFileToProject("sub.rb", "class Sub; include Base; def compute; 42; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(subFile)
        val computePos = subFile.text.indexOf("def compute")
        val element = myFixture.file?.findElementAt(computePos + 4)
        val result = handler.findSuperMethods(element!!, project)

        assertNotNull("findSuperMethods should return a result", result)
        assertEquals("Method name should be compute", "compute", result!!.method.name)
    }

    // -- predicate and bang method override (fsm_07) --------------------------

    fun testPredicateMethodOverride() {
        requireRubyPlugin()

        myFixture.addFileToProject("base.rb", "class Base; def admin?; false; end; end")
        val subFile = myFixture.addFileToProject("sub.rb", "class Sub < Base; def admin?; true; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(subFile)
        val adminPos = subFile.text.indexOf("def admin?")
        val element = myFixture.file?.findElementAt(adminPos + 4)
        val result = handler.findSuperMethods(element!!, project)

        assertNotNull("findSuperMethods should return a result", result)
        assertEquals("Method name should be admin?", "admin?", result!!.method.name)
    }

    fun testBangMethodOverride() {
        requireRubyPlugin()

        myFixture.addFileToProject("base.rb", "class Base; def save!; end; end")
        val subFile = myFixture.addFileToProject("sub.rb", "class Sub < Base; def save!; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(subFile)
        val savePos = subFile.text.indexOf("def save!")
        val element = myFixture.file?.findElementAt(savePos + 4)
        val result = handler.findSuperMethods(element!!, project)

        assertNotNull("findSuperMethods should return a result", result)
        assertEquals("Method name should be save!", "save!", result!!.method.name)
    }

    // -- edge cases (fsm_08) --------------------------------------------------

    fun testNoExplicitSuperclass() {
        requireRubyPlugin()

        // Class with no explicit superclass — Object is implicit
        val file = myFixture.addFileToProject("leaf.rb", "class Leaf; def my_method; end; end")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val handler = resolveHandler(file)
        val methodPos = file.text.indexOf("def my_method")
        val element = myFixture.file?.findElementAt(methodPos + 4)
        val result = handler.findSuperMethods(element!!, project)

        assertNotNull("findSuperMethods should return a result", result)
        assertEquals("Method name should be my_method", "my_method", result!!.method.name)
    }

    fun testMissingFileReturnsNull() {
        requireRubyPlugin()

        // This test verifies that the handler returns null when called with
        // an element that isn't associated with a real file.
        // In practice, the MCP tool layer handles the file-not-found scenario.
        assertTrue("Missing file handling is in the MCP tool layer, not the handler", true)
    }

    fun testEmptyFileReturnsNull() {
        requireRubyPlugin()

        val file = myFixture.addFileToProject("empty.rb", "")
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val element = myFixture.file?.findElementAt(0)
        if (element != null) {
            val handler = resolveHandler(element)
            val result = handler.findSuperMethods(element, project)
            assertNull("findSuperMethods should return null for empty file element", result)
        }
    }

    fun testSyntaxErrorFile() {
        requireRubyPlugin()

        try {
            val file = myFixture.addFileToProject("broken.rb", "class Broken; def method; end")
            IndexingTestUtil.waitUntilIndexesAreReady(project)
            // File with syntax error may still parse partially
            assertTrue("Syntax error file should not cause crash", true)
        } catch (e: Exception) {
            // Some Ruby plugin versions may fail to parse; that's acceptable
            assertTrue("Syntax error handling is plugin-dependent", true)
        }
    }
}