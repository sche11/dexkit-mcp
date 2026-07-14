package org.luckypray.dexkit.mcp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * 注册所有 11 个 DexKit MCP 工具到 server。
 */
fun registerDexKitTools(server: Server, pool: BridgePool) {

    // ============ 1. dexkit_open ============
    server.addTool(
        name = "dexkit_open",
        description = "Create a DexKit session for analyzing APK or DEX bytes. Returns sessionId for subsequent calls.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("source", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("apk"))
                        add(JsonPrimitive("dex"))
                    })
                    put("description", "Input type: 'apk' for APK file path, 'dex' for base64-encoded DEX bytes array")
                })
                put("apkPath", buildJsonObject {
                    put("type", "string")
                    put("description", "Absolute path to APK file (required when source='apk')")
                })
                put("dexBytesArray", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "string") })
                    put("description", "Base64-encoded DEX bytes array (required when source='dex')")
                })
            },
            required = listOf("source")
        )
    ) { request ->
        val args = JsonParser.parseString(request.params.arguments?.toString() ?: "{}").asJsonObject
        val source = args["source"]?.asString ?: error("source is required")
        when (source) {
            "apk" -> {
                val path = args["apkPath"]?.asString ?: error("apkPath is required when source='apk'")
                val file = java.io.File(path)
                when {
                    !file.exists() -> return@addTool CallToolResult(
                        content = listOf(TextContent(errorResult("APK file not found: $path"))),
                        isError = true
                    )
                    file.isDirectory -> return@addTool CallToolResult(
                        content = listOf(TextContent(errorResult(
                            "DexKit does not support directory input. Please repack with apktool: 'apktool b <dir> -o out.apk' then call dexkit_open with source='apk' and path='out.apk'"
                        ))),
                        isError = true
                    )
                }
                val sid = pool.openApk(path)
                val bridge = pool.get(sid)
                val result = JsonObject().apply {
                    addProperty("sessionId", sid)
                    addProperty("dexCount", bridge.getDexNum())
                }.toString()
                CallToolResult(content = listOf(TextContent(result)))
            }
            "dex" -> {
                val arr = args["dexBytesArray"]?.asJsonArray ?: error("dexBytesArray is required when source='dex'")
                val bytes = arr.map { Base64.getDecoder().decode(it.asString) }
                val sid = pool.openDex(bytes)
                val bridge = pool.get(sid)
                val result = JsonObject().apply {
                    addProperty("sessionId", sid)
                    addProperty("dexCount", bridge.getDexNum())
                }.toString()
                CallToolResult(content = listOf(TextContent(result)))
            }
            else -> CallToolResult(
                content = listOf(TextContent(errorResult("Unknown source: $source (must be 'apk' or 'dex')"))),
                isError = true
            )
        }
    }

    // ============ 2. dexkit_close_session ============
    server.addTool(
        name = "dexkit_close_session",
        description = "Close a DexKit session and release native resources.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("sessionId", buildJsonObject { put("type", "string"); put("description", "Session ID returned by dexkit_open") })
            },
            required = listOf("sessionId")
        )
    ) { request ->
        val args = JsonParser.parseString(request.params.arguments?.toString() ?: "{}").asJsonObject
        val sid = args["sessionId"]?.asString ?: error("sessionId is required")
        val ok = pool.close(sid)
        val result = JsonObject().apply { addProperty("closed", ok) }.toString()
        CallToolResult(content = listOf(TextContent(result)))
    }

    // ============ 3. dexkit_find_class ============
    server.addTool(
        name = "dexkit_find_class",
        description = "Multi-condition class search using ClassMatcher. Supports className, usingStrings, fields, methods, annotations, superClass, interfaces.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("sessionId", buildJsonObject { put("type", "string") })
                put("matcher", buildJsonObject {
                    put("type", "object")
                    put("description", "ClassMatcher JSON: {className, descriptor, usingStrings, fields, methods, annotations, superClass, modifiers}")
                })
                put("searchPackages", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
                put("excludePackages", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
            },
            required = listOf("sessionId", "matcher")
        )
    ) { request ->
        val args = JsonParser.parseString(request.params.arguments?.toString() ?: "{}").asJsonObject
        val sid = args["sessionId"]?.asString ?: error("sessionId is required")
        val matcherJson = args["matcher"] ?: error("matcher is required")
        val findClass = Matchers.parseFindClass(args.toString())
        val bridge = pool.get(sid)
        val result = Results.classesJson(bridge.findClass(findClass))
        CallToolResult(content = listOf(TextContent(result)))
    }

    // ============ 4. dexkit_find_method ============
    server.addTool(
        name = "dexkit_find_method",
        description = "Multi-condition method search using MethodMatcher. Supports name, returnType, paramTypes, usingStrings, modifiers, invokeMethods, annotations.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("sessionId", buildJsonObject { put("type", "string") })
                put("matcher", buildJsonObject {
                    put("type", "object")
                    put("description", "MethodMatcher JSON: {name, returnType, paramTypes, usingStrings, modifiers, annotations, invokeMethods}")
                })
                put("searchPackages", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
                put("excludePackages", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
            },
            required = listOf("sessionId", "matcher")
        )
    ) { request ->
        val args = JsonParser.parseString(request.params.arguments?.toString() ?: "{}").asJsonObject
        val sid = args["sessionId"]?.asString ?: error("sessionId is required")
        args["matcher"] ?: error("matcher is required")
        val findMethod = Matchers.parseFindMethod(args.toString())
        val bridge = pool.get(sid)
        val result = Results.methodsJson(bridge.findMethod(findMethod))
        CallToolResult(content = listOf(TextContent(result)))
    }

    // ============ 5. dexkit_find_field ============
    server.addTool(
        name = "dexkit_find_field",
        description = "Multi-condition field search using FieldMatcher. Supports name, type, modifiers, annotations.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("sessionId", buildJsonObject { put("type", "string") })
                put("matcher", buildJsonObject {
                    put("type", "object")
                    put("description", "FieldMatcher JSON: {name, type, modifiers, annotations}")
                })
                put("searchPackages", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
                put("excludePackages", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
            },
            required = listOf("sessionId", "matcher")
        )
    ) { request ->
        val args = JsonParser.parseString(request.params.arguments?.toString() ?: "{}").asJsonObject
        val sid = args["sessionId"]?.asString ?: error("sessionId is required")
        args["matcher"] ?: error("matcher is required")
        val findField = Matchers.parseFindField(args.toString())
        val bridge = pool.get(sid)
        val result = Results.fieldsJson(bridge.findField(findField))
        CallToolResult(content = listOf(TextContent(result)))
    }

    // ============ 6. dexkit_batch_find_class_using_strings ============
    server.addTool(
        name = "dexkit_batch_find_class_using_strings",
        description = "Batch search classes using strings. DexKit's signature feature - much faster than iterating find_class.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("sessionId", buildJsonObject { put("type", "string") })
                put("matchers", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("key", buildJsonObject { put("type", "string") })
                            put("usingStrings", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
                        })
                    })
                })
                put("searchPackages", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
                put("excludePackages", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
            },
            required = listOf("sessionId", "matchers")
        )
    ) { request ->
        val args = JsonParser.parseString(request.params.arguments?.toString() ?: "{}").asJsonObject
        val sid = args["sessionId"]?.asString ?: error("sessionId is required")
        args["matchers"] ?: error("matchers is required")
        val batch = Matchers.parseBatchFindClass(args.toString())
        val bridge = pool.get(sid)
        val result = Results.batchClassesJson(bridge.batchFindClassUsingStrings(batch))
        CallToolResult(content = listOf(TextContent(result)))
    }

    // ============ 7. dexkit_batch_find_method_using_strings ============
    server.addTool(
        name = "dexkit_batch_find_method_using_strings",
        description = "Batch search methods using strings. Returns Map<key, MethodDataList>.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("sessionId", buildJsonObject { put("type", "string") })
                put("matchers", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("key", buildJsonObject { put("type", "string") })
                            put("usingStrings", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
                        })
                    })
                })
                put("searchPackages", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
                put("excludePackages", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
            },
            required = listOf("sessionId", "matchers")
        )
    ) { request ->
        val args = JsonParser.parseString(request.params.arguments?.toString() ?: "{}").asJsonObject
        val sid = args["sessionId"]?.asString ?: error("sessionId is required")
        args["matchers"] ?: error("matchers is required")
        val batch = Matchers.parseBatchFindMethod(args.toString())
        val bridge = pool.get(sid)
        val result = Results.batchMethodsJson(bridge.batchFindMethodUsingStrings(batch))
        CallToolResult(content = listOf(TextContent(result)))
    }

    // ============ 8. dexkit_get_class_data ============
    server.addTool(
        name = "dexkit_get_class_data",
        description = "Get class details by class name or descriptor (e.g. 'com.example.Foo' or 'Lcom/example/Foo;').",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("sessionId", buildJsonObject { put("type", "string") })
                put("identifier", buildJsonObject {
                    put("type", "string")
                    put("description", "Class name (com.example.Foo) or descriptor (Lcom/example/Foo;)")
                })
            },
            required = listOf("sessionId", "identifier")
        )
    ) { request ->
        val args = JsonParser.parseString(request.params.arguments?.toString() ?: "{}").asJsonObject
        val sid = args["sessionId"]?.asString ?: error("sessionId is required")
        val identifier = args["identifier"]?.asString ?: error("identifier is required")
        val bridge = pool.get(sid)
        val result = Results.classDataJson(bridge.getClassData(identifier))
        CallToolResult(content = listOf(TextContent(result)))
    }

    // ============ 9. dexkit_get_method_data ============
    server.addTool(
        name = "dexkit_get_method_data",
        description = "Get method details by method descriptor (e.g. 'Lcom/example/Foo;->bar()V').",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("sessionId", buildJsonObject { put("type", "string") })
                put("descriptor", buildJsonObject {
                    put("type", "string")
                    put("description", "Method descriptor: Lcom/example/Foo;->bar(Ljava/lang/String;)V")
                })
            },
            required = listOf("sessionId", "descriptor")
        )
    ) { request ->
        val args = JsonParser.parseString(request.params.arguments?.toString() ?: "{}").asJsonObject
        val sid = args["sessionId"]?.asString ?: error("sessionId is required")
        val descriptor = args["descriptor"]?.asString ?: error("descriptor is required")
        val bridge = pool.get(sid)
        val result = Results.methodDataJson(bridge.getMethodData(descriptor))
        CallToolResult(content = listOf(TextContent(result)))
    }

    // ============ 10. dexkit_get_field_data ============
    server.addTool(
        name = "dexkit_get_field_data",
        description = "Get field details by field descriptor (e.g. 'Lcom/example/Foo;->TAG:Ljava/lang/String;').",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("sessionId", buildJsonObject { put("type", "string") })
                put("descriptor", buildJsonObject {
                    put("type", "string")
                    put("description", "Field descriptor: Lcom/example/Foo;->TAG:Ljava/lang/String;")
                })
            },
            required = listOf("sessionId", "descriptor")
        )
    ) { request ->
        val args = JsonParser.parseString(request.params.arguments?.toString() ?: "{}").asJsonObject
        val sid = args["sessionId"]?.asString ?: error("sessionId is required")
        val descriptor = args["descriptor"]?.asString ?: error("descriptor is required")
        val bridge = pool.get(sid)
        val result = Results.fieldDataJson(bridge.getFieldData(descriptor))
        CallToolResult(content = listOf(TextContent(result)))
    }

    // ============ 11. dexkit_export_dex ============
    server.addTool(
        name = "dexkit_export_dex",
        description = "Export all dex files to a directory.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("sessionId", buildJsonObject { put("type", "string") })
                put("outDir", buildJsonObject {
                    put("type", "string")
                    put("description", "Output directory (must exist or be creatable)")
                })
            },
            required = listOf("sessionId", "outDir")
        )
    ) { request ->
        val args = JsonParser.parseString(request.params.arguments?.toString() ?: "{}").asJsonObject
        val sid = args["sessionId"]?.asString ?: error("sessionId is required")
        val outDir = args["outDir"]?.asString ?: error("outDir is required")
        val bridge = pool.get(sid)
        val dir = java.io.File(outDir).apply { mkdirs() }
        bridge.exportDexFile(dir.absolutePath)
        val exported = dir.listFiles()?.filter { it.name.endsWith(".dex") }?.map { it.absolutePath } ?: emptyList()
        val obj = JsonObject().apply {
            add("exported", com.google.gson.JsonArray().apply { exported.forEach { add(it) } })
        }
        CallToolResult(content = listOf(TextContent(obj.toString())))
    }
}

private fun errorResult(message: String): String =
    JsonObject().apply { addProperty("error", message) }.toString()
