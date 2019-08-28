package com.test

import com.template.flows.*
import com.template.states.SimpleState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.lang.IllegalArgumentException
import kotlin.system.measureTimeMillis
import kotlin.test.fail

class IntegrationTests {

    private val mockNetwork = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.template.states"),
            TestCordapp.findCordapp("com.template.contracts"),
            TestCordapp.findCordapp("com.template.flows"))))
    private lateinit var nodeA: StartedMockNode
    private lateinit var nodeB: StartedMockNode

    @Before
    fun setup() {
        nodeA = mockNetwork.createNode(MockNodeParameters())
        nodeB = mockNetwork.createNode(MockNodeParameters())
        listOf(nodeA, nodeB).forEach {
            it.registerInitiatedFlow(TestBackchainFlowResponder::class.java)
        }
    }

    @After
    fun tearDown() = mockNetwork.stopNodes()

    final val CHAIN_LENGTH = 300

    @Test
    fun `Test with Snipping`() {

        val partyA = nodeA.info.chooseIdentity()
        val partyB = nodeB.info.chooseIdentity()

        val startChainFuture = nodeA.startFlow(StartChainFlow())
        mockNetwork.runNetwork()
        val stx = startChainFuture.getOrThrow()
        val linearId = stx.tx.outputsOfType<SimpleState>().single().linearId

        for (i in 1..CHAIN_LENGTH) {
            val addToChainFuture = nodeA.startFlow(AddToChainFlow(linearId))
            mockNetwork.runNetwork()
            addToChainFuture.getOrThrow()

            // Snip chain every 10 transactions
            if (i % 10 == 0) {
                val reissueFuture = nodeA.startFlow(ReissueFlow(linearId))
                mockNetwork.runNetwork()
                reissueFuture.getOrThrow()
            }
        }

        val backChainResolutionTime = measureTimeMillis {
            val testBackchainFuture = nodeA.startFlow(AddParticipantFlow(linearId, partyB))
            mockNetwork.runNetwork()
            testBackchainFuture.getOrThrow()
        }
        println(backChainResolutionTime)

    }

    @Test
    fun `Test no snipping`() {

        val partyA = nodeA.info.chooseIdentity()
        val partyB = nodeB.info.chooseIdentity()

        val startChainFuture = nodeA.startFlow(StartChainFlow())
        mockNetwork.runNetwork()
        val stx = startChainFuture.getOrThrow()
        val linearId = stx.tx.outputsOfType<SimpleState>().single().linearId

        for (i in 1..CHAIN_LENGTH) {
            val addToChainFuture = nodeA.startFlow(AddToChainFlow(linearId))
            mockNetwork.runNetwork()
            addToChainFuture.getOrThrow()
        }

        val backChainResolutionTime = measureTimeMillis {
            val testBackchainFuture = nodeA.startFlow(AddParticipantFlow(linearId, partyB))
            mockNetwork.runNetwork()
            testBackchainFuture.getOrThrow()
        }
        println(backChainResolutionTime)

    }








}