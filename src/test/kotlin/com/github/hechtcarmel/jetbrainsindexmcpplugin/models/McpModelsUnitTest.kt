package com.github.hechtcarmel.jetbrainsindexmcpplugin.models

import com.github.hechtcarmel.jetbrainsindexmcpplugin.server.models.*
import junit.framework.TestCase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class McpModelsUnitTest : TestCase() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ContentBlock serialization tests

    fun testTextContentBlockSerialization() {
        val textBlock = ContentBlock.Text("Hello, World!")

        val serialized = json.encodeToString<ContentBlock>(textBlock)
        val deserialized = json.decodeFromString<ContentBlock>(serialized)

        assertTrue("Should deserialize to Text block", deserialized is ContentBlock.Text)
        assertEquals("Hello, World!", (deserialized as ContentBlock.Text).text)
        assertTrue("Serialized should contain type discriminator", serialized.contains("\"type\":\"text\""))
    }

    fun testImageContentBlockSerialization() {
        val imageBlock = ContentBlock.Image("base64data==", "image/png")

        val serialized = json.encodeToString<ContentBlock>(imageBlock)
        val deserialized = json.decodeFromString<ContentBlock>(serialized)

        assertTrue("Should deserialize to Image block", deserialized is ContentBlock.Image)
        val image = deserialized as ContentBlock.Image
        assertEquals("base64data==", image.data)
        assertEquals("image/png", image.mimeType)
        assertTrue("Serialized should contain type discriminator", serialized.contains("\"type\":\"image\""))
    }

    fun testContentBlockPolymorphicDeserialization() {
        val textJson = """{"type":"text","text":"test content"}"""
        val imageJson = """{"type":"image","data":"abc123","mimeType":"image/jpeg"}"""

        val textBlock = json.decodeFromString<ContentBlock>(textJson)
        val imageBlock = json.decodeFromString<ContentBlock>(imageJson)

        assertTrue("Text JSON should deserialize to Text", textBlock is ContentBlock.Text)
        assertTrue("Image JSON should deserialize to Image", imageBlock is ContentBlock.Image)
    }

    // ToolCallResult tests

    fun testToolCallResultWithTextContent() {
        val result = ToolCallResult(
            content = listOf(ContentBlock.Text("Result text")),
            isError = false
        )

        val serialized = json.encodeToString(result)
        val deserialized = json.decodeFromString<ToolCallResult>(serialized)

        assertFalse("isError should be false", deserialized.isError)
        assertEquals(1, deserialized.content.size)
        assertTrue(deserialized.content[0] is ContentBlock.Text)
    }

    fun testToolCallResultWithError() {
        val result = ToolCallResult(
            content = listOf(ContentBlock.Text("Error message")),
            isError = true
        )

        val serialized = json.encodeToString(result)
        val deserialized = json.decodeFromString<ToolCallResult>(serialized)

        assertTrue("isError should be true", deserialized.isError)
    }

    fun testToolCallResultWithMultipleContentBlocks() {
        val result = ToolCallResult(
            content = listOf(
                ContentBlock.Text("Text content"),
                ContentBlock.Image("imagedata", "image/png")
            ),
            isError = false
        )

        val serialized = json.encodeToString(result)
        val deserialized = json.decodeFromString<ToolCallResult>(serialized)

        assertEquals(2, deserialized.content.size)
        assertTrue(deserialized.content[0] is ContentBlock.Text)
        assertTrue(deserialized.content[1] is ContentBlock.Image)
    }

    // ToolDefinition tests

    fun testToolDefinitionSerialization() {
        val schema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("file", buildJsonObject { put("type", "string") })
            })
        }

        val definition = ToolDefinition(
            name = "test_tool",
            description = "A test tool",
            inputSchema = schema
        )

        val serialized = json.encodeToString(definition)
        val deserialized = json.decodeFromString<ToolDefinition>(serialized)

        assertEquals("test_tool", deserialized.name)
        assertEquals("A test tool", deserialized.description)
        assertNotNull(deserialized.inputSchema)
    }

    // ResourceDefinition tests

    fun testResourceDefinitionSerialization() {
        val resource = ResourceDefinition(
            uri = "file://content/src/Main.kt",
            name = "Main.kt",
            description = "Source file",
            mimeType = "text/x-kotlin"
        )

        val serialized = json.encodeToString(resource)
        val deserialized = json.decodeFromString<ResourceDefinition>(serialized)

        assertEquals("file://content/src/Main.kt", deserialized.uri)
        assertEquals("Main.kt", deserialized.name)
        assertEquals("Source file", deserialized.description)
        assertEquals("text/x-kotlin", deserialized.mimeType)
    }

    // ResourceContent tests

    fun testResourceContentWithText() {
        val content = ResourceContent(
            uri = "index://status",
            mimeType = "application/json",
            text = """{"isDumbMode":false}"""
        )

        val serialized = json.encodeToString(content)
        val deserialized = json.decodeFromString<ResourceContent>(serialized)

        assertEquals("index://status", deserialized.uri)
        assertEquals("application/json", deserialized.mimeType)
        assertNotNull(deserialized.text)
        assertNull(deserialized.blob)
    }

    fun testResourceContentWithBlob() {
        val content = ResourceContent(
            uri = "file://binary/image.png",
            mimeType = "image/png",
            blob = "base64encodeddata=="
        )

        val serialized = json.encodeToString(content)
        val deserialized = json.decodeFromString<ResourceContent>(serialized)

        assertNull(deserialized.text)
        assertNotNull(deserialized.blob)
    }

    // ServerInfo tests

    fun testServerInfoSerialization() {
        val info = ServerInfo(
            name = "intellij-index-mcp",
            version = "1.0.0",
            description = "IDE index MCP server"
        )

        val serialized = json.encodeToString(info)
        val deserialized = json.decodeFromString<ServerInfo>(serialized)

        assertEquals("intellij-index-mcp", deserialized.name)
        assertEquals("1.0.0", deserialized.version)
        assertEquals("IDE index MCP server", deserialized.description)
    }

    fun testServerInfoWithoutDescription() {
        val info = ServerInfo(
            name = "test-server",
            version = "0.1.0"
        )

        val serialized = json.encodeToString(info)
        val deserialized = json.decodeFromString<ServerInfo>(serialized)

        assertNull(deserialized.description)
    }

    // ServerCapabilities tests

    fun testServerCapabilitiesDefaults() {
        val capabilities = ServerCapabilities()

        val serialized = json.encodeToString(capabilities)
        val deserialized = json.decodeFromString<ServerCapabilities>(serialized)

        assertNotNull(deserialized.tools)
        assertNotNull(deserialized.resources)
        assertFalse(deserialized.tools!!.listChanged)
        assertFalse(deserialized.resources!!.subscribe)
        assertFalse(deserialized.resources!!.listChanged)
    }

    // InitializeResult tests

    fun testInitializeResultSerialization() {
        val result = InitializeResult(
            protocolVersion = "2024-11-05",
            capabilities = ServerCapabilities(),
            serverInfo = ServerInfo("test", "1.0")
        )

        val serialized = json.encodeToString(result)
        val deserialized = json.decodeFromString<InitializeResult>(serialized)

        assertEquals("2024-11-05", deserialized.protocolVersion)
        assertNotNull(deserialized.capabilities)
        assertEquals("test", deserialized.serverInfo.name)
    }

    // ToolsListResult tests

    fun testToolsListResultSerialization() {
        val schema = buildJsonObject { put("type", "object") }
        val result = ToolsListResult(
            tools = listOf(
                ToolDefinition("tool1", "First tool", schema),
                ToolDefinition("tool2", "Second tool", schema)
            )
        )

        val serialized = json.encodeToString(result)
        val deserialized = json.decodeFromString<ToolsListResult>(serialized)

        assertEquals(2, deserialized.tools.size)
        assertEquals("tool1", deserialized.tools[0].name)
        assertEquals("tool2", deserialized.tools[1].name)
    }

    // ToolCallParams tests

    fun testToolCallParamsSerialization() {
        val params = ToolCallParams(
            name = "ide_find_references",
            arguments = buildJsonObject {
                put("file", "src/Main.kt")
                put("line", 10)
                put("column", 5)
            }
        )

        val serialized = json.encodeToString(params)
        val deserialized = json.decodeFromString<ToolCallParams>(serialized)

        assertEquals("ide_find_references", deserialized.name)
        assertNotNull(deserialized.arguments)
    }

    fun testToolCallParamsWithoutArguments() {
        val params = ToolCallParams(name = "ide_index_status")

        val serialized = json.encodeToString(params)
        val deserialized = json.decodeFromString<ToolCallParams>(serialized)

        assertEquals("ide_index_status", deserialized.name)
        assertNull(deserialized.arguments)
    }

    // ResourcesListResult tests

    fun testResourcesListResultSerialization() {
        val result = ResourcesListResult(
            resources = listOf(
                ResourceDefinition("index://status", "Index Status", "IDE index status", "application/json"),
                ResourceDefinition("project://structure", "Project Structure", "Module tree", "application/json")
            )
        )

        val serialized = json.encodeToString(result)
        val deserialized = json.decodeFromString<ResourcesListResult>(serialized)

        assertEquals(2, deserialized.resources.size)
    }

    // ResourceReadParams tests

    fun testResourceReadParamsSerialization() {
        val params = ResourceReadParams(uri = "index://status")

        val serialized = json.encodeToString(params)
        val deserialized = json.decodeFromString<ResourceReadParams>(serialized)

        assertEquals("index://status", deserialized.uri)
    }

    // ResourceReadResult tests

    fun testResourceReadResultSerialization() {
        val result = ResourceReadResult(
            contents = listOf(
                ResourceContent("index://status", "application/json", """{"status":"ready"}""")
            )
        )

        val serialized = json.encodeToString(result)
        val deserialized = json.decodeFromString<ResourceReadResult>(serialized)

        assertEquals(1, deserialized.contents.size)
        assertEquals("index://status", deserialized.contents[0].uri)
    }
}
