package org.web3j.fabric.shim

import mu.KLogging
import org.hyperledger.fabric.shim.Chaincode
import org.hyperledger.fabric.shim.ChaincodeBase
import org.hyperledger.fabric.shim.ChaincodeStub
import org.web3j.fabric.shim.ChaincodeDsl.ChaincodeFunction
import org.web3j.fabric.shim.ChaincodeDsl.ChaincodeHandlerType
import org.web3j.fabric.shim.ChaincodeDsl.ChaincodeHandlerType.*
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Kotlin DSL for writing Fabric Chaincode using the Shim API.
 *
 * Simply declare a `chaincode` block to create a [ChaincodeBase] instance:
 * ```
 *  val myChaincode = chaincode {
 *      ...
 *  }
 * ```
 * You can then use it to declare a class implementing [Chaincode] by delegation:
 *  ```
 *  class MyChaincode: Chaincode by myChaincode {
 *      ...
 *  }
 * ```
 */
@ChaincodeMarker
class ChaincodeDsl : ChaincodeBase() {

    private val handlers: ChaincodeHandlers = mutableMapOf(
            INIT to mutableMapOf(), INVOKE to mutableMapOf()
    )

    override fun init(stub: ChaincodeStub) = invokeInternal(stub, INIT)

    override fun invoke(stub: ChaincodeStub) = invokeInternal(stub, INVOKE)

    /**
     * Starts an `init` handler block allowing nested `function` blocks.
     *
     * @receiver [ChaincodeHandler]
     * @see [Chaincode.init]
     */
    inline fun init(init: ChaincodeHandler.() -> Unit) {
        val handler = ChaincodeHandler(INIT)
        init.invoke(handler)
    }

    /**
     * Starts an `invoke` handler block allowing nested `function` blocks.
     *
     * @receiver [ChaincodeHandler]
     * @see [Chaincode.invoke]
     */
    inline fun invoke(invoke: ChaincodeHandler.() -> Unit) {
        val handler = ChaincodeHandler(INVOKE)
        invoke.invoke(handler)
    }

    private fun invokeInternal(stub: ChaincodeStub, handlerType: ChaincodeHandlerType): Chaincode.Response {

        val function: ChaincodeFunction = handlers[handlerType]!![stub.function]
                ?: return error("No ${handlerType.name.toLowerCase()} function '${stub.function}' defined")

        if (stub.args.size != function.numArgs) {
            error("Incorrect number of arguments. Expecting ${function.numArgs}")
        }

        return function.function.invoke(stub)
    }

    companion object : KLogging() {

        /**
         * Starts a `chaincode` block allowing nested `init` and `invoke` calls.
         * ```
         * chaincode {
         *      init {
         *          function("myInitFunction") {
         *              ... // this is a ChaincodeStub
         *          }
         *      }
         *      invoke {
         *          function("myInvokeFunction") {
         *              ... // this is a ChaincodeStub
         *          }
         *      }
         * }
         * ```
         *
         * @receiver [ChaincodeDsl]
         */
        inline fun chaincode(chaincode: ChaincodeDsl.() -> Unit): ChaincodeBase {
            val dsl = ChaincodeDsl()
            chaincode.invoke(dsl)
            return dsl
        }

        /**
         * Returns a Chaincode success response with the given message and result.
         */
        fun success(message: String? = null, result: Any? = null): Chaincode.Response {
            return newSuccessResponse(message, result?.toString()?.toByteArray())
        }

        /**
         * Returns a Chaincode error response with the given message and result.
         */
        fun error(message: String? = null, result: Any? = null): Chaincode.Response {
            return newErrorResponse(message, result?.toString()?.toByteArray())
        }

        /**
         * Returns a Chaincode error response with the given throwable.
         */
        fun error(throwable: Throwable): Chaincode.Response {
            return newErrorResponse(throwable)
        }
    }

    @ChaincodeMarker
    inner class ChaincodeHandler(private val type: ChaincodeHandlerType) {

        /**
         * Starts a function declaration inside of a handler block.
         *
         * @receiver [ChaincodeHandler]
         * @receiving [ChaincodeStub]
         */
        fun function(name: String, numArgs: Int = 0, function: ChaincodeStub.() -> Chaincode.Response) {
            requireNotNull(name) { "Function name cannot be null" }
            require(name.isNotBlank()) { "Function name cannot be blank" }
            require(0 <= numArgs) { "Function arguments have to be zero or more" }
            require(!this@ChaincodeDsl.handlers[type]!!.containsKey(name)) {
                "Function '$name' already defined"
            }

            this@ChaincodeDsl.handlers[type]!![name] = ChaincodeFunction(name, numArgs, function)
        }

    }

    data class ChaincodeFunction(
            internal val name: String,
            internal val numArgs: Int,
            internal val function: ChaincodeStub.() -> Chaincode.Response
    )

    enum class ChaincodeHandlerType {
        INIT, INVOKE
    }

}

/**
 * Extension method providing argument type conversion,
 * e.g. `val arg0 = getArg<Int>(0)`.
 *
 * @receiver [ChaincodeStub]
 */
inline fun <reified T> ChaincodeStub.getArg(index: Int): T {
    return when (T::class) {
        String::class -> stringArgs[index]

        Byte::class -> stringArgs[index].toByte()
        ByteArray::class -> stringArgs[index].toByteArray()

        Int::class -> stringArgs[index].toInt()
        Long::class -> stringArgs[index].toLong()
        BigInteger::class -> stringArgs[index].toBigInteger()

        Float::class -> stringArgs[index].toFloat()
        Double::class -> stringArgs[index].toDouble()
        BigDecimal::class -> stringArgs[index].toBigDecimal()

        Boolean::class -> stringArgs[index]?.toBoolean()
        else -> {
            ChaincodeDsl.logger.warn { "No conversion for ${T::class}" }
            throw IllegalArgumentException("No conversion for ${T::class}")
        }
    } as T
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
inline fun <reified T> ChaincodeStub.getState(key: String): T? {
    return when (T::class) {
        String::class -> getStringState(key)

        Byte::class -> getStringState(key).toByte()
        ByteArray::class -> getState(key)

        Int::class -> getStringState(key).toInt()
        Long::class -> getStringState(key).toLong()
        BigInteger::class -> getStringState(key).toBigInteger()

        Float::class -> getStringState(key).toFloat()
        Double::class -> getStringState(key).toDouble()
        BigDecimal::class -> getStringState(key).toBigDecimal()

        Boolean::class -> getStringState(key)?.toBoolean()
        else -> {
            ChaincodeDsl.logger.warn { "No conversion for ${T::class}" }
            throw IllegalArgumentException("No conversion for ${T::class}")
        }
    } as T?
}

fun ChaincodeStub.putState(key: String, value: Any) {
    putStringState(key, value.toString())
}

private typealias ChaincodeHandlers = MutableMap<ChaincodeHandlerType, ChaincodeFunctions>
private typealias ChaincodeFunctions = MutableMap<String, ChaincodeFunction>

@DslMarker
private annotation class ChaincodeMarker
