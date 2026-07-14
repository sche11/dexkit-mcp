package org.luckypray.dexkit.mcp

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import org.luckypray.dexkit.result.AnnotationData
import org.luckypray.dexkit.result.AnnotationElementData
import org.luckypray.dexkit.result.AnnotationEncodeValue
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.ClassDataList
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.FieldDataList
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.result.MethodDataList
import java.lang.reflect.Modifier

/**
 * 把 DexKit 的查询结果转为 JSON 字符串。
 * 用于 MCP 工具的 CallToolResult.content。
 */
object Results {

    val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /** 将 ClassDataList 序列化为 JSON 字符串 */
    fun classesJson(list: ClassDataList): String {
        val arr = JsonArray()
        list.forEach { arr.add(classJson(it)) }
        val obj = JsonObject()
        obj.add("classes", arr)
        return gson.toJson(obj)
    }

    /** 将 MethodDataList 序列化为 JSON 字符串 */
    fun methodsJson(list: MethodDataList): String {
        val arr = JsonArray()
        list.forEach { arr.add(methodJson(it)) }
        val obj = JsonObject()
        obj.add("methods", arr)
        return gson.toJson(obj)
    }

    /** 将 FieldDataList 序列化为 JSON 字符串 */
    fun fieldsJson(list: FieldDataList): String {
        val arr = JsonArray()
        list.forEach { arr.add(fieldJson(it)) }
        val obj = JsonObject()
        obj.add("fields", arr)
        return gson.toJson(obj)
    }

    fun classJson(c: ClassData): JsonObject = JsonObject().apply {
        addProperty("descriptor", c.descriptor)
        addProperty("name", c.name)
        addProperty("simpleName", c.simpleName)
        addProperty("modifiers", modifiersToString(c.modifiers))
        addProperty("sourceFile", c.sourceFile)
        c.superClass?.let { addProperty("superclass", it.name) }
        val ifaces = JsonArray()
        c.interfaces.forEach { ifaces.add(it.name) }
        add("interfaces", ifaces)
        addProperty("fieldsCount", c.fields.size)
        addProperty("methodsCount", c.methods.size)
    }

    fun methodJson(m: MethodData): JsonObject = JsonObject().apply {
        addProperty("descriptor", m.descriptor)
        addProperty("className", m.className)
        addProperty("name", m.name)
        addProperty("returnType", m.returnTypeName)
        val params = JsonArray()
        m.paramTypeNames.forEach { params.add(it) }
        add("paramTypes", params)
        addProperty("paramCount", m.paramCount)
        addProperty("modifiers", modifiersToString(m.modifiers))
    }

    fun fieldJson(f: FieldData): JsonObject = JsonObject().apply {
        addProperty("descriptor", f.descriptor)
        addProperty("className", f.className)
        addProperty("name", f.name)
        addProperty("type", f.typeName)
        addProperty("modifiers", modifiersToString(f.modifiers))
    }

    /** 批量查询结果：Map<String, ClassDataList> */
    fun batchClassesJson(results: Map<String, ClassDataList>): String {
        val obj = JsonObject()
        val resultsObj = JsonObject()
        results.forEach { (key, list) ->
            val arr = JsonArray()
            list.forEach { arr.add(classJson(it)) }
            resultsObj.add(key, arr)
        }
        obj.add("results", resultsObj)
        return gson.toJson(obj)
    }

    fun batchMethodsJson(results: Map<String, MethodDataList>): String {
        val obj = JsonObject()
        val resultsObj = JsonObject()
        results.forEach { (key, list) ->
            val arr = JsonArray()
            list.forEach { arr.add(methodJson(it)) }
            resultsObj.add(key, arr)
        }
        obj.add("results", resultsObj)
        return gson.toJson(obj)
    }

    /** class data 单值返回 */
    fun classDataJson(c: ClassData?): String {
        val obj = JsonObject()
        if (c == null) {
            obj.addProperty("classData", "null")
        } else {
            obj.add("classData", classJson(c))
        }
        return gson.toJson(obj)
    }

    fun methodDataJson(m: MethodData?): String {
        val obj = JsonObject()
        if (m == null) {
            obj.addProperty("methodData", "null")
        } else {
            obj.add("methodData", methodJson(m))
        }
        return gson.toJson(obj)
    }

    fun fieldDataJson(f: FieldData?): String {
        val obj = JsonObject()
        if (f == null) {
            obj.addProperty("fieldData", "null")
        } else {
            obj.add("fieldData", fieldJson(f))
        }
        return gson.toJson(obj)
    }

    private fun modifiersToString(modifiers: Int): String {
        val list = mutableListOf<String>()
        if (Modifier.isPublic(modifiers)) list += "public"
        if (Modifier.isPrivate(modifiers)) list += "private"
        if (Modifier.isProtected(modifiers)) list += "protected"
        if (Modifier.isStatic(modifiers)) list += "static"
        if (Modifier.isFinal(modifiers)) list += "final"
        if (Modifier.isSynchronized(modifiers)) list += "synchronized"
        if (Modifier.isVolatile(modifiers)) list += "volatile"
        if (Modifier.isTransient(modifiers)) list += "transient"
        if (Modifier.isNative(modifiers)) list += "native"
        if (Modifier.isInterface(modifiers)) list += "interface"
        if (Modifier.isAbstract(modifiers)) list += "abstract"
        if (Modifier.isStrict(modifiers)) list += "strict"
        return list.joinToString(",")
    }
}
