package org.web3j.fabric.shim

import org.web3j.fabric.shim.ChaincodeDsl.Companion.chaincode
import org.web3j.fabric.shim.ChaincodeDsl.Companion.error
import org.web3j.fabric.shim.ChaincodeDsl.Companion.success
import org.assertj.core.api.Assertions.assertThat
import org.hyperledger.fabric.shim.Chaincode.Response.Status.INTERNAL_SERVER_ERROR
import org.hyperledger.fabric.shim.Chaincode.Response.Status.SUCCESS
import org.hyperledger.fabric.shim.ChaincodeStub
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class ChaincodeDslTest {

    private lateinit var stub: ChaincodeStub

    private val result = chaincode {

        init {
            function("success") {
                success("message")
            }

            function("error") {
                error("message")
            }

            function("throwable") {
                error(Exception("message"))
            }
        }

        invoke {
            function("negate") {
                success("OK", typedArg<Boolean>(0).not())

            }

            function("sum") {
                var sum = 0
                for (i in stringArgs.indices) {
                    sum += typedArg<Int>(i)
                }
                success("OK", sum)
            }
        }

    }

    @Before
    fun setUp() {
        stub = mock(ChaincodeStub::class.java)
    }

    @Test
    fun testSuccess() {
        `when`(stub.function).thenReturn("success")

        val response = result.init(stub)
        assertThat(response.status).isEqualTo(SUCCESS)
        assertThat(response.message).isEqualTo("message")
    }

    @Test
    fun testError() {
        `when`(stub.function).thenReturn("error")

        val response = result.init(stub)
        assertThat(response.status).isEqualTo(INTERNAL_SERVER_ERROR)
        assertThat(response.message).isEqualTo("message")
    }

    @Test
    fun testException() {
        `when`(stub.function).thenReturn("throwable")

        val response = result.init(stub)
        assertThat(response.status).isEqualTo(INTERNAL_SERVER_ERROR)
        assertThat(response.message).isEqualTo("message")
    }

    @Test
    fun testNegate() {
        `when`(stub.function).thenReturn("negate")
        `when`(stub.stringArgs).thenReturn(listOf("true"))

        val response = result.invoke(stub)
        assertThat(response.status).isEqualTo(SUCCESS)
        assertThat(response.payload).isEqualTo(false.toString().toByteArray())
    }

    @Test
    fun testSum() {
        `when`(stub.function).thenReturn("sum")
        `when`(stub.stringArgs).thenReturn(listOf(1, 2, 3).map { it.toString() })

        val response = result.invoke(stub)
        assertThat(response.status).isEqualTo(SUCCESS)
        assertThat(String(response.payload).toInt()).isEqualTo(6)
    }

}