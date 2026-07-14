package org.luckypray.dexkit.mcp

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import org.luckypray.dexkit.query.BatchFindClassUsingStrings
import org.luckypray.dexkit.query.BatchFindMethodUsingStrings
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindField
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.AnnotationMatcher
import org.luckypray.dexkit.query.matchers.AnnotationsMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.FieldMatcher
import org.luckypray.dexkit.query.matchers.FieldsMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.query.matchers.MethodsMatcher
import java.lang.reflect.Modifier

/**
 * JSON → DexKit FindClass/FindMethod/FindField/BatchFind* 转换器。
 *
 * 设计：
 * - 用 Gson 解析 JSON 为 JsonElement 树
 * - 用 builder 风格调用 DexKit 的 setter 方法
 * - 支持常用字段，未识别字段忽略
 * - 不依赖反射，全部显式映射
 */
object Matchers {

    private val gson = Gson()

    // ============ FindClass ============

    fun parseFindClass(json: String): FindClass {
        val obj = JsonParser.parseString(json).asJsonObject
        return FindClass().apply {
            obj.getAsJsonArray("searchPackages")?.let { arr ->
                arr.map { it.asString }.also { list -> searchPackages(list) }
            }
            obj.getAsJsonArray("excludePackages")?.let { arr ->
                arr.map { it.asString }.also { list -> excludePackages(list) }
            }
            obj.get("ignorePackagesCase")?.takeIf { !it.isJsonNull }?.let {
                ignorePackagesCase(it.asBoolean)
            }
            obj.get("findFirst")?.takeIf { !it.isJsonNull }?.let {
                // FindClass.findFirst 是 var，通过 set:JvmSynthetic
                // 这里用反射设置，避免依赖 Kotlin synthetic setter
                if (it.asBoolean) {
                    val field = FindClass::class.java.getDeclaredField("findFirst")
                    field.isAccessible = true
                    field.setBoolean(this, true)
                }
            }
            obj.get("matcher")?.takeIf { !it.isJsonNull }?.let { matcherEl ->
                matcher(parseClassMatcher(matcherEl.asJsonObject))
            }
        }
    }

    private fun parseClassMatcher(obj: JsonObject): ClassMatcher = ClassMatcher().apply {
        obj.get("className")?.takeIf { !it.isJsonNull }?.let { className(it.asString) }
        obj.get("descriptor")?.takeIf { !it.isJsonNull }?.let { descriptor(it.asString) }
        obj.get("source")?.takeIf { !it.isJsonNull }?.let { source(it.asString) }
        obj.get("modifiers")?.takeIf { !it.isJsonNull }?.let { modifiers(parseModifiers(it)) }
        obj.get("usingStrings")?.takeIf { !it.isJsonNull }?.let { stringsEl ->
            val strings = stringsEl.asJsonArray.map { it.asString }
            if (strings.isNotEmpty()) usingStrings(strings)
        }
        obj.get("fields")?.takeIf { !it.isJsonNull }?.let { fieldsEl ->
            fields(parseFieldsMatcher(fieldsEl.asJsonObject))
        }
        obj.get("methods")?.takeIf { !it.isJsonNull }?.let { methodsEl ->
            methods(parseMethodsMatcher(methodsEl.asJsonObject))
        }
        obj.get("annotations")?.takeIf { !it.isJsonNull }?.let { annEl ->
            annotations(parseAnnotationsMatcher(annEl.asJsonObject))
        }
        obj.get("superClass")?.takeIf { !it.isJsonNull }?.let { scEl ->
            superClass(parseClassMatcher(scEl.asJsonObject))
        }
    }

    // ============ FindMethod ============

    fun parseFindMethod(json: String): FindMethod {
        val obj = JsonParser.parseString(json).asJsonObject
        return FindMethod().apply {
            obj.getAsJsonArray("searchPackages")?.let { arr ->
                searchPackages(arr.map { it.asString })
            }
            obj.getAsJsonArray("excludePackages")?.let { arr ->
                excludePackages(arr.map { it.asString })
            }
            obj.get("matcher")?.takeIf { !it.isJsonNull }?.let { matcherEl ->
                matcher(parseMethodMatcher(matcherEl.asJsonObject))
            }
        }
    }

    private fun parseMethodMatcher(obj: JsonObject): MethodMatcher = MethodMatcher().apply {
        obj.get("name")?.takeIf { !it.isJsonNull }?.let { name(it.asString) }
        obj.get("modifiers")?.takeIf { !it.isJsonNull }?.let { modifiers(parseModifiers(it)) }
        obj.get("returnType")?.takeIf { !it.isJsonNull }?.let { returnType(it.asString) }
        obj.get("paramTypes")?.takeIf { !it.isJsonNull }?.let { paramsEl ->
            paramTypes(paramsEl.asJsonArray.map { it.asString })
        }
        obj.get("usingStrings")?.takeIf { !it.isJsonNull }?.let { stringsEl ->
            val strings = stringsEl.asJsonArray.map { it.asString }
            if (strings.isNotEmpty()) usingStrings(strings)
        }
        obj.get("annotations")?.takeIf { !it.isJsonNull }?.let { annEl ->
            annotations(parseAnnotationsMatcher(annEl.asJsonObject))
        }
    }

    // ============ FindField ============

    fun parseFindField(json: String): FindField {
        val obj = JsonParser.parseString(json).asJsonObject
        return FindField().apply {
            obj.getAsJsonArray("searchPackages")?.let { arr ->
                searchPackages(arr.map { it.asString })
            }
            obj.getAsJsonArray("excludePackages")?.let { arr ->
                excludePackages(arr.map { it.asString })
            }
            obj.get("matcher")?.takeIf { !it.isJsonNull }?.let { matcherEl ->
                matcher(parseFieldMatcher(matcherEl.asJsonObject))
            }
        }
    }

    private fun parseFieldMatcher(obj: JsonObject): FieldMatcher = FieldMatcher().apply {
        obj.get("name")?.takeIf { !it.isJsonNull }?.let { name(it.asString) }
        obj.get("type")?.takeIf { !it.isJsonNull }?.let { type(it.asString) }
        obj.get("modifiers")?.takeIf { !it.isJsonNull }?.let { modifiers(parseModifiers(it)) }
    }

    // ============ FieldsMatcher / MethodsMatcher / AnnotationsMatcher ============

    private fun parseFieldsMatcher(obj: JsonObject): FieldsMatcher = FieldsMatcher().apply {
        obj.getAsJsonArray("add")?.forEach { el ->
            add(parseFieldMatcher(el.asJsonObject))
        }
        obj.getAsJsonArray("addForType")?.forEach { el ->
            addForType(el.asString)
        }
        obj.get("count")?.takeIf { !it.isJsonNull }?.let { countEl ->
            if (countEl.isJsonObject) {
                val range = countEl.asJsonObject
                count(range.get("min").asInt..range.get("max").asInt)
            } else {
                count(countEl.asInt)
            }
        }
    }

    private fun parseMethodsMatcher(obj: JsonObject): MethodsMatcher = MethodsMatcher().apply {
        obj.getAsJsonArray("add")?.forEach { el ->
            add(parseMethodMatcher(el.asJsonObject))
        }
        obj.get("count")?.takeIf { !it.isJsonNull }?.let { countEl ->
            if (countEl.isJsonObject) {
                val range = countEl.asJsonObject
                count(range.get("min").asInt..range.get("max").asInt)
            } else {
                count(countEl.asInt)
            }
        }
    }

    private fun parseAnnotationsMatcher(obj: JsonObject): AnnotationsMatcher = AnnotationsMatcher().apply {
        obj.getAsJsonArray("add")?.forEach { el ->
            add(parseAnnotationMatcher(el.asJsonObject))
        }
    }

    private fun parseAnnotationMatcher(obj: JsonObject): AnnotationMatcher = AnnotationMatcher().apply {
        obj.get("type")?.takeIf { !it.isJsonNull }?.let { type(it.asString) }
        obj.getAsJsonArray("elements")?.forEach { elEl ->
            val el = elEl.asJsonObject
            addElement {
                name = el.get("name")?.asString ?: ""
                el.get("stringValue")?.takeIf { !it.isJsonNull }?.let { stringValue(it.asString) }
            }
        }
    }

    // ============ BatchFindClassUsingStrings / BatchFindMethodUsingStrings ============

    fun parseBatchFindClass(json: String): BatchFindClassUsingStrings {
        val obj = JsonParser.parseString(json).asJsonObject
        return BatchFindClassUsingStrings().apply {
            obj.getAsJsonArray("searchPackages")?.let { arr ->
                searchPackages(arr.map { it.asString })
            }
            obj.getAsJsonArray("excludePackages")?.let { arr ->
                excludePackages(arr.map { it.asString })
            }
            obj.getAsJsonArray("matchers")?.forEach { mEl ->
                val m = mEl.asJsonObject
                val key = m.get("key").asString
                val strings = m.getAsJsonArray("usingStrings").map { it.asString }
                addSearchGroup(key, strings)
            }
        }
    }

    fun parseBatchFindMethod(json: String): BatchFindMethodUsingStrings {
        val obj = JsonParser.parseString(json).asJsonObject
        return BatchFindMethodUsingStrings().apply {
            obj.getAsJsonArray("searchPackages")?.let { arr ->
                searchPackages(arr.map { it.asString })
            }
            obj.getAsJsonArray("excludePackages")?.let { arr ->
                excludePackages(arr.map { it.asString })
            }
            obj.getAsJsonArray("matchers")?.forEach { mEl ->
                val m = mEl.asJsonObject
                val key = m.get("key").asString
                val strings = m.getAsJsonArray("usingStrings").map { it.asString }
                addSearchGroup(key, strings)
            }
        }
    }

    // ============ Helpers ============

    /**
     * 把 modifiers JSON（可能是字符串数组或 int）解析为 Java Modifier 位掩码。
     */
    private fun parseModifiers(el: JsonElement): Int {
        return when {
            el.isJsonPrimitive && el.asJsonPrimitive.isNumber -> el.asInt
            el.isJsonArray -> {
                var mask = 0
                el.asJsonArray.forEach { modEl ->
                    val name = modEl.asString
                    mask = mask or when (name.lowercase()) {
                        "public" -> Modifier.PUBLIC
                        "private" -> Modifier.PRIVATE
                        "protected" -> Modifier.PROTECTED
                        "static" -> Modifier.STATIC
                        "final" -> Modifier.FINAL
                        "synchronized" -> Modifier.SYNCHRONIZED
                        "volatile" -> Modifier.VOLATILE
                        "transient" -> Modifier.TRANSIENT
                        "native" -> Modifier.NATIVE
                        "interface" -> Modifier.INTERFACE
                        "abstract" -> Modifier.ABSTRACT
                        "strict" -> Modifier.STRICT
                        else -> 0
                    }
                }
                mask
            }
            else -> 0
        }
    }
}
