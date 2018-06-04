package io.cryptoblk.corduroy

import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.KType

object DataModel {

    enum class ExampleEnum {
        ONE,
        TWO,
        THREE
    }

    data class ExampleState(val partyA: Party, val partyB: Party, val name: String, val xK: Float, val xL: Double,
        val yK: Int, val vL: Long, val t: Instant, val b: Boolean, val bla: Array<String>, val ble: List<String>) {
        companion object {
            val participants = listOf("partyA, partyB")
        }
    }

    object Party

    private const val spacing = "\t"

    private val dataMethods = setOf("copy", "equals", "hashCode", "toString")

    private fun getShortType(type: String): String = type.split(".").last()

    private fun getCordaType(type: KType): String {
        return if (type.arguments.isEmpty()) {
            getShortType(type.toString())
        } else {
            type.toString().split("<").joinToString("<") { getShortType(it) }
        }
    }

    private fun <T : Enum<*>> genCordaEnum(enum: KClass<T>): String {
        return "@CordaSerializable\n" +
            "enum class ${enum.simpleName} {\n" +
            "${enum.java.fields.joinToString(",\n") { "$spacing${it.name}" }}\n" +
            "}"
    }

    fun <T : Enum<*>> genCorda(enumDefs: List<KClass<T>>, stateDefs: List<KClass<*>>): String {
        val result = StringBuilder()
        enumDefs.forEach {
            result.appendln(genCordaEnum(it))
        }
        stateDefs.forEach {
            result.appendln("@CordaSerializable")
            result.appendln("data class ${it.simpleName}(")

            val fields = it.members.filter {
                !(it.name.startsWith("component") || it.name in dataMethods)
            }.joinToString(",\n") {
                "${spacing}val ${it.name}: ${getCordaType(it.returnType)}"
            }
            result.appendln(fields)

            val comp = it.nestedClasses.first { it.isCompanion }
            val participants = comp.members.first { it.name == "participants" }.call(comp.objectInstance) as List<String>
            result.appendln("${spacing}override val participants: List<AbstractParty> = listOf(${participants.joinToString(", ")})): ContractState")
        }
        return result.toString()
    }

    private fun getComposerType(type: KType): String {
        return if (type.arguments.isEmpty()) {
            val shortType = getShortType(type.toString())
            when (shortType) {
                "Int" -> "Integer"
                "Float" -> "Double"
                "Instant" -> "DateTime"
                "LocalDate" -> "DateTime"
                "LocalDateTime" -> "DateTime"
                else -> shortType
            }
        } else {
            "${getComposerType(type.arguments[0].type!!)}[]"
        }
    }

    private val composerPrimitives = Regex("(String|Double|Integer|Long|DateTime|Boolean)[\\[\\]]*")

    private fun getMarker(enums: Set<String>, composerType: String): String {
        return if (composerPrimitives.matches(composerType) || composerType in enums) "o"
        else "-->"
    }

    private fun <T : Enum<*>> genFabricEnum(enum: KClass<T>): String {
        return "enum ${enum.simpleName} {\n" +
            "${enum.java.fields.joinToString(",\n") { "${spacing}o ${it.name}" }}\n" +
            "}"
    }

    // for Composer
    fun <T : Enum<*>> genFabric(enumDefs: List<KClass<T>>, stateDefs: List<KClass<*>>): String {
        val enumNames = enumDefs.mapNotNull { it.simpleName }.toSet()
        val result = StringBuilder("participant Party identified by id {\n" +
            "${spacing}o String id\n" +
            "}\n")
        enumDefs.forEach {
            result.appendln(genFabricEnum(it))
        }
        stateDefs.forEach {
            result.appendln("asset ${it.simpleName} identified by id {\n" +
                "${spacing}o String id")

            val fields = it.members.filter {
                !(it.name.startsWith("component") || it.name in dataMethods)
            }.joinToString("\n") {
                val composerType = getComposerType(it.returnType)
                "$spacing${getMarker(enumNames, composerType)} $composerType ${it.name}"
            }
            result.appendln(fields)
            result.appendln("}")
        }
        return result.toString()
    }
}