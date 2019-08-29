package com.test

import com.template.flows.*
import com.template.states.SimpleState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.system.measureTimeMillis

class IntegrationTests {

    private val mockNetwork = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.template.states"),
            TestCordapp.findCordapp("com.template.contracts"),
            TestCordapp.findCordapp("com.template.flows"))))
    private lateinit var nodeA: StartedMockNode
    private lateinit var nodeB: StartedMockNode
    private lateinit var partyA: Party
    private lateinit var partyB: Party

    val CHAIN_LENGTH = 500

    @Before
    fun setup() {
        nodeA = mockNetwork.createNode(MockNodeParameters())
        nodeB = mockNetwork.createNode(MockNodeParameters())
        listOf(nodeA, nodeB).forEach {
            it.registerInitiatedFlow(AddParticipantFlowResponder::class.java)
        }
        partyA = nodeA.info.chooseIdentity()
        partyB = nodeB.info.chooseIdentity()
    }

    @After
    fun tearDown() = mockNetwork.stopNodes()

    fun setupChainWithoutSnipping(): UniqueIdentifier {
        val startChainFuture = nodeA.startFlow(StartChainFlow())
        mockNetwork.runNetwork()
        val stx = startChainFuture.getOrThrow()
        val linearId = stx.tx.outputsOfType<SimpleState>().single().linearId
        for (i in 1..CHAIN_LENGTH) {
            val addToChainFuture = nodeA.startFlow(AddToChainFlow(linearId))
            mockNetwork.runNetwork()
            addToChainFuture.getOrThrow()
        }
        return linearId
    }

    fun setupChainWithSnipping(): UniqueIdentifier {
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
        return linearId
    }

    fun timeTransactionResolution(linearId: UniqueIdentifier): Long {
        return measureTimeMillis {
            val addParticipantFuture = nodeA.startFlow(AddParticipantFlow(linearId, partyB))
            mockNetwork.runNetwork()
            addParticipantFuture.getOrThrow()
        }
    }

    @Test
    fun NoSnippingTest() {
        val linearId = setupChainWithoutSnipping()
        val time = timeTransactionResolution(linearId)
        println(time)
    }

    @Test
    fun SnippingTest() {
        val linearId = setupChainWithSnipping()
        val time = timeTransactionResolution(linearId)
        println(time)
    }



}