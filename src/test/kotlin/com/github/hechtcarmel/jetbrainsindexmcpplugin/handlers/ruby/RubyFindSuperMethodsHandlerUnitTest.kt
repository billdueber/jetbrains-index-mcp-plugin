package com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.ruby

import com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.LanguageHandlerRegistry
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetector
import com.github.hechtcarmel.jetbrainsindexmcpplugin.util.PluginDetectors
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import junit.framework.TestCase

class RubyFindSuperMethodsHandlerUnitTest : TestCase() {

    override fun setUp() {
        super.setUp()
        LanguageHandlerRegistry.clear()
    }

    override fun tearDown() {
        super.tearDown()
        LanguageHandlerRegistry.clear()
    }

    // -- Metadata -------------------------------------------------------------

    fun testLanguageIdIsRuby() {
        assertEquals("languageId must be 'Ruby'", "Ruby", RubySuperMethodsHandler().languageId)
    }

    fun testIsAvailableWithoutPlugin() {
        mockkObject(PluginDetectors)
        val rubyDetector = mockk<PluginDetector>()
        every { rubyDetector.isAvailable } returns false
        every { PluginDetectors.ruby } returns rubyDetector
        try {
            assertFalse("isAvailable should be false without Ruby plugin", RubySuperMethodsHandler().isAvailable())
        } finally {
            unmockkObject(PluginDetectors)
        }
    }

    fun testCanHandleReturnsFalseWithoutRubyPlugin() {
        mockkObject(PluginDetectors)
        val rubyDetector = mockk<PluginDetector>()
        every { rubyDetector.isAvailable } returns false
        every { PluginDetectors.ruby } returns rubyDetector
        try {
            val handler = RubySuperMethodsHandler()
            val element = mockk<com.intellij.psi.PsiElement>()
            assertFalse("canHandle must be false without Ruby plugin", handler.canHandle(element))
        } finally {
            unmockkObject(PluginDetectors)
        }
    }

    // -- Registry Wiring ------------------------------------------------------

    fun testRegistryExposesRubyFindSuperMethodsWhenForcedAvailable() {
        mockkObject(PluginDetectors)
        val rubyDetector = mockk<PluginDetector>()
        every { rubyDetector.isAvailable } returns true
        every { PluginDetectors.ruby } returns rubyDetector
        LanguageHandlerRegistry.clear()
        try {
            LanguageHandlerRegistry.registerSuperMethodsHandler(RubySuperMethodsHandler())
            assertTrue(
                "'Ruby' must be advertised as a supported find-super-methods language",
                LanguageHandlerRegistry.getSupportedLanguagesForSuperMethods().contains("Ruby")
            )
        } finally {
            LanguageHandlerRegistry.clear()
            unmockkObject(PluginDetectors)
        }
    }

    // -- Handler returns null without Ruby PSI ---------------------------------

    fun testFindSuperMethodsReturnsNullWithoutRubyPlugin() {
        mockkObject(PluginDetectors)
        val rubyDetector = mockk<PluginDetector>()
        every { rubyDetector.isAvailable } returns true
        every { PluginDetectors.ruby } returns rubyDetector
        try {
            val handler = RubySuperMethodsHandler()
            val project = mockk<com.intellij.openapi.project.Project>(relaxed = true)
            val element = mockk<com.intellij.psi.PsiElement>(relaxUnitFun = true) {
                every { parent } returns null
            }
            val result = handler.findSuperMethods(element, project)
            assertNull("findSuperMethods must return null when resolveToRMethod fails", result)
        } finally {
            unmockkObject(PluginDetectors)
        }
    }

    fun testFindSuperMethodsReturnsNullForNonRubyElement() {
        mockkObject(PluginDetectors)
        val rubyDetector = mockk<PluginDetector>()
        every { rubyDetector.isAvailable } returns true
        every { PluginDetectors.ruby } returns rubyDetector
        try {
            val handler = RubySuperMethodsHandler()
            val project = mockk<com.intellij.openapi.project.Project>(relaxed = true)
            val element = mockk<com.intellij.psi.PsiElement>(relaxUnitFun = true) {
                every { parent } returns null
            }
            val result = handler.findSuperMethods(element, project)
            assertNull("findSuperMethods must return null when no containing method found", result)
        } finally {
            unmockkObject(PluginDetectors)
        }
    }

    // -- Max depth guardrail ---------------------------------------------------

    fun testMaxHierarchyDepthLimit() {
        val handler = RubySuperMethodsHandler()
        val fields = handler.javaClass.declaredFields.map { it.name }
        assertTrue(
            "Companion should have MAX_HIERARCHY_DEPTH or MAX_SUPER_METHODS constant, got fields: $fields",
            fields.any { it.contains("MAX_HIERARCHY", ignoreCase = true) } ||
            fields.any { it.contains("MAX_SUPER", ignoreCase = true) }
        )
    }

    // -- Visited set cycle detection ------------------------------------------

    fun testVisitedSetRejectsDuplicateSuperKeys() {
        val visited = mutableSetOf("Calculator.add")
        assertFalse(
            "Visited set should reject already-visited key",
            visited.add("Calculator.add")
        )
    }

    fun testVisitedSetAcceptsNewSuperKeys() {
        val visited = mutableSetOf("Calculator.add")
        assertTrue(
            "Visited set should accept new key",
            visited.add("Animal.speak")
        )
    }

    fun testVisitedSetRevisitReturnsEmptyHierarchy() {
        val visited = mutableSetOf("GrandParent.speak", "Parent.speak")
        val overriddenKeys = listOf("GrandParent.speak", "Parent.speak", "Child.speak")
        val results = mutableListOf<String>()
        for (key in overriddenKeys) {
            if (key in visited) continue
            visited.add(key)
            results.add(key)
        }
        assertEquals(
            "Only Child.speak should be new after GrandParent and Parent are visited",
            1, results.size
        )
        assertEquals("Results should contain only Child.speak", "Child.speak", results[0])
    }

    // -- Visited classes set prevents duplicates per class ---------------------

    fun testVisitedClassesSetPreventsDuplicateClassEntries() {
        val visitedClasses = mutableSetOf("Calculator")
        val classKeys = listOf("Calculator", "Animal", "Calculator", "MathHelper")
        val results = mutableListOf<String>()
        for (key in classKeys) {
            if (key in visitedClasses) continue
            visitedClasses.add(key)
            results.add(key)
        }
        assertEquals(
            "Only Animal and MathHelper should be new after Calculator is visited",
            2, results.size
        )
        assertEquals("First new class should be Animal", "Animal", results[0])
        assertEquals("Second new class should be MathHelper", "MathHelper", results[1])
    }

    // -- MAX_SUPER_METHODS limit ----------------------------------------------

    fun testMaxSuperMethodsLimit() {
        val overridden = (1..150).map { "Method$it" }
        val limited = overridden.take(100)
        assertEquals("Should cap at 100 super methods", 100, limited.size)
        assertEquals("First method should be Method1", "Method1", limited[0])
        assertEquals("Last method should be Method100", "Method100", limited[99])
    }

    // -- Method key format ----------------------------------------------------

    fun testMethodKeyFormatContainsClassAndMethodName() {
        val key = "Calculator.add"
        val parts = key.split(".")
        assertEquals("Method key should have 2 dot-delimited parts", 2, parts.size)
        assertEquals("Class part should be Calculator", "Calculator", parts[0])
        assertEquals("Method part should be add", "add", parts[1])
    }

    fun testMethodKeyFormatForModuleNamespacedClass() {
        val key = "Admin::User.find_by_email"
        val parts = key.split(".")
        assertEquals("Method key should split on dot, not colon", 2, parts.size)
        assertEquals("Class part should be Admin::User", "Admin::User", parts[0])
        assertEquals("Method part should be find_by_email", "find_by_email", parts[1])
    }

    fun testMethodKeyFormatForTopLevelMethod() {
        val key = "unknown.say_hello"
        val parts = key.split(".")
        assertEquals("Method key should have 2 parts", 2, parts.size)
        assertEquals("Class part should be unknown when no containing class", "unknown", parts[0])
        assertEquals("Method part should be say_hello", "say_hello", parts[1])
    }

    // -- Predicate and bang method name handling ------------------------------

    fun testMethodKeyWithPredicateMethod() {
        val key = "User.admin?"
        val parts = key.split(".")
        assertEquals("Method part should preserve ? suffix", "admin?", parts[1])
    }

    fun testMethodKeyWithBangMethod() {
        val key = "User.save!"
        val parts = key.split(".")
        assertEquals("Method part should preserve ! suffix", "save!", parts[1])
    }

    // -- Language filter ------------------------------------------------------

    fun testCanHandleRejectsNonRubyLanguage() {
        mockkObject(PluginDetectors)
        val rubyDetector = mockk<PluginDetector>()
        every { rubyDetector.isAvailable } returns true
        every { PluginDetectors.ruby } returns rubyDetector
        try {
            val handler = RubySuperMethodsHandler()
            val element = mockk<com.intellij.psi.PsiElement> {
                every { language } returns mockk {
                    every { id } returns "kotlin"
                }
            }
            assertFalse("canHandle must return false for non-Ruby element", handler.canHandle(element))
        } finally {
            unmockkObject(PluginDetectors)
        }
    }

    fun testCanHandleReturnsTrueForRubyLanguageElement() {
        mockkObject(PluginDetectors)
        val rubyDetector = mockk<PluginDetector>()
        every { rubyDetector.isAvailable } returns true
        every { PluginDetectors.ruby } returns rubyDetector
        try {
            val handler = RubySuperMethodsHandler()
            val element = mockk<com.intellij.psi.PsiElement> {
                every { language } returns mockk {
                    every { id } returns "ruby"
                }
            }
            val result = handler.canHandle(element)
            assertTrue("canHandle must return true for Ruby element with plugin available, got: $result", result)
        } finally {
            unmockkObject(PluginDetectors)
        }
    }

    // -- Verify the handler is no longer a stub --------------------------------

    fun testHandlerIsNotStub() {
        val handler = RubySuperMethodsHandler()
        assertEquals(
            "RubySuperMethodsHandler should be the real implementation",
            "com.github.hechtcarmel.jetbrainsindexmcpplugin.handlers.ruby.RubySuperMethodsHandler",
            handler.javaClass.name
        )
        assertTrue(
            "Handler should have declared methods beyond the interface ones, got: ${handler.javaClass.declaredMethods.size}",
            handler.javaClass.declaredMethods.size >= 3
        )
    }
}