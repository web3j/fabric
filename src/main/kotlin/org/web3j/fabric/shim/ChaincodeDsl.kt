package org.web3j.fabric.shim

import mu.KLogging
import org.hyperledger.fabric.shim.Chaincode
import org.hyperledger.fabric.shim.Chaincode.Response
import org.hyperledger.fabric.shim.ChaincodeBase
import org.hyperledger.fabric.shim.ChaincodeStub
import org.web3j.fabric.shim.ChaincodeDsl.ChaincodeHandlerType
import org.web3j.fabric.shim.ChaincodeDsl.ChaincodeHandlerType.INIT
import org.web3j.fabric.shim.ChaincodeDsl.ChaincodeHandlerType.INVOKE
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

    override fun init(stub: ChaincodeStub) = handlers[INIT]!![stub.function]?.invoke(stub)
            ?: error("No init function '${stub.function}' defined")

    override fun invoke(stub: ChaincodeStub) = handlers[INVOKE]!![stub.function]?.invoke(stub)
            ?: error("No invoke function '${stub.function}' defined")

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
        fun success(message: String? = null, result: Any? = null): Response {
            return newSuccessResponse(message, result?.toString()?.toByteArray())
        }

        /**
         * Returns a Chaincode error response with the given message and result.
         */
        fun error(message: String? = null, result: Any? = null): Response {
            return newErrorResponse(message, result?.toString()?.toByteArray())
        }

        /**
         * Returns a Chaincode error response with the given throwable.
         */
        fun error(throwable: Throwable): Response {
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
        fun function(name: String, function: ChaincodeStub.() -> Response) {
            requireNotNull(name) { "Function name cannot be null" }
            require(name.isNotBlank()) { "Function name cannot be blank" }
            require(!this@ChaincodeDsl.handlers[type]!!.containsKey(name)) {
                "Function '$name' already defined"
            }

            this@ChaincodeDsl.handlers[type]!![name] = function
        }

    }

    enum class ChaincodeHandlerType {
        INIT, INVOKE
    }

}

/**
 * Extension method providing argument type conversion,
 * e.g. `val arg0 = stub.typedArg<Int>(0)`.
 *
 * @receiver [ChaincodeStub]
 */
inline fun <reified T> ChaincodeStub.typedArg(index: Int): T {
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

private typealias ChaincodeHandlers = MutableMap<ChaincodeHandlerType, ChaincodeFunctions>
private typealias ChaincodeFunctions = MutableMap<String, (ChaincodeStub) -> Response>

@DslMarker
private annotation class ChaincodeMarker
