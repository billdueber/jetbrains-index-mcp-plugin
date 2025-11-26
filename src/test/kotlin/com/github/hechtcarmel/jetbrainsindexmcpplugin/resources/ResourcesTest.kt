package com.github.hechtcarmel.jetbrainsindexmcpplugin.resources

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Platform-dependent tests that require IntelliJ Platform indexing.
 * For registry and metadata tests that don't need the platform, see ResourcesUnitTest.
 */
class ResourcesTest : BasePlatformTestCase() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun testIndexStatusResourceRead() = runBlocking {
        val resource = IndexStatusResource()

        val content = resource.read(project)

        assertEquals("index://status", content.uri)
        assertNotNull(content.text)

        val resultJson = json.parseToJsonElement(content.text!!).jsonObject
        assertNotNull("Result should have isDumbMode", resultJson["isDumbMode"])
        assertNotNull("Result should have isSmartMode", resultJson["isSmartMode"])
    }

    fun testProjectStructureResourceRead() = runBlocking {
        val resource = ProjectStructureResource()

        val content = resource.read(project)

        assertEquals("project://structure", content.uri)
        assertNotNull(content.text)

        val resultJson = json.parseToJsonElement(content.text!!).jsonObject
        assertNotNull("Result should have projectName", resultJson["projectName"])
        assertNotNull("Result should have basePath", resultJson["basePath"])
        assertNotNull("Result should have modules", resultJson["modules"])
    }

    // Phase 2: New Resource Tests

    fun testFileContentResourceReadBase() = runBlocking {
        val resource = FileContentResource()

        // Base read should return error since no path
        val content = resource.read(project)

        assertEquals("file://content/{path}", content.uri)
        assertTrue("Should indicate error", content.text?.contains("error") == true)
    }

    fun testFileContentResourceReadWithInvalidPath() = runBlocking {
        val resource = FileContentResource()

        val content = resource.readWithPath(project, "nonexistent/file.kt")

        assertTrue("Should indicate error for invalid path", content.text?.contains("error") == true || content.text?.contains("not found") == true)
    }

    fun testSymbolInfoResourceReadBase() = runBlocking {
        val resource = SymbolInfoResource()

        // Base read should return error since no FQN
        val content = resource.read(project)

        assertEquals("symbol://info/{fqn}", content.uri)
        assertTrue("Should indicate error", content.text?.contains("error") == true)
    }

    fun testSymbolInfoResourceReadWithInvalidFqn() = runBlocking {
        val resource = SymbolInfoResource()

        val content = resource.readWithFqn(project, "com.nonexistent.Class")

        assertTrue("Should indicate symbol not found", content.text?.contains("not found") == true || content.text?.contains("error") == true)
    }

    fun testResourceRegistryHasNewResources() {
        val registry = ResourceRegistry()
        registry.registerBuiltInResources()

        val definitions = registry.getResourceDefinitions()

        assertTrue("Should have file content resource",
            definitions.any { it.uri.startsWith("file://content") })
        assertTrue("Should have symbol info resource",
            definitions.any { it.uri.startsWith("symbol://info") })
    }
}
