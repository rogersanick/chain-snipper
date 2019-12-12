package com.r3.chainsnipper.flows

import com.r3.chainsnipper.states.SimpleState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis

class IntegrationTests {

    private val mockNetwork = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
            TestCordapp.findCordapp("com.r3.chainsnipper.states"),
            TestCordapp.findCordapp("com.r3.chainsnipper.contracts"),
            TestCordapp.findCordapp("com.r3.chainsnipper.flows"))))
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

    @Test
    fun testSingleParticipantNoChainSnipping() {
        val linearId = nodeA.setupChainWithoutSnipping()
        val time = nodeA.timeToTransactionResolution(linearId, partyB)
        println("Transaction resolution for a TX Chain Length of $CHAIN_LENGTH, took: $time")
    }

    @Test
    fun testSingeParticipantWithChainSnippingTest() {
        val linearId = nodeA.setupChainWithSnipping()
        val time = nodeA.timeToTransactionResolution(linearId, partyB)
        println("Transaction resolution for a TX Chain Length of $CHAIN_LENGTH, took: $time")
    }

    @Test
    fun testMultipleParticipantsNoChainSnipping() {
        val linearId = nodeA.setupChainWithoutSnipping()
        val time = nodeA.timeToTransactionResolution(linearId, partyB)
        println("Transaction resolution for a TX Chain Length of $CHAIN_LENGTH, took: $time")
    }

    @Test
    fun testMultipleParticipantsWithChainSnipping() {
        val linearId = nodeA.setupChainWithSnipping()
        val time = nodeA.timeToTransactionResolution(linearId, partyB)
        println("Transaction resolution for a TX Chain Length of $CHAIN_LENGTH, took: $time")
    }

    fun StartedMockNode.setupChainWithoutSnipping(): UniqueIdentifier {
        val startChainFuture = this.startFlow(StartChainFlow())
        mockNetwork.runNetwork()
        val stx = startChainFuture.getOrThrow()
        val linearId = stx.tx.outputsOfType<SimpleState>().single().linearId
        for (i in 1..CHAIN_LENGTH) {
            val addToChainFuture = this.startFlow(AddToChainFlow(linearId))
            mockNetwork.runNetwork()
            addToChainFuture.getOrThrow()
            println("Chain length is currently $i")
        }
        return linearId
    }

    fun StartedMockNode.setupChainWithSnipping(): UniqueIdentifier {
        val startChainFuture = this.startFlow(StartChainFlow())
        mockNetwork.runNetwork()
        val stx = startChainFuture.getOrThrow()
        val linearId = stx.tx.outputsOfType<SimpleState>().single().linearId

        var lengthOfSnip = 0
        for (i in 1..CHAIN_LENGTH) {
            val addToChainFuture = this.startFlow(AddToChainFlow(linearId))
            mockNetwork.runNetwork()
            addToChainFuture.getOrThrow()
            println("Chain length is currently $i")

            // Snip chain every 10 transactions
            if (i % 10 == 0) {
                lengthOfSnip += 10
                val reissueFuture = this.startFlow(ReissueFlow(linearId))
                mockNetwork.runNetwork()
                reissueFuture.getOrThrow()
                mockNetwork.waitQuiescent()
            }
        }
        return linearId
    }

    fun StartedMockNode.timeToTransactionResolution(linearIdOfStateHead: UniqueIdentifier, targetNode: Party): Long {
        return measureTimeMillis {
            this.addPartyToResolveBackchain(linearIdOfStateHead, targetNode)
        }
    }

    fun StartedMockNode.addPartyToResolveBackchain(linearIdOfStateHead: UniqueIdentifier, targetNode: Party) {
        val addParticipantFuture = nodeA.startFlow(AddParticipantFlow(linearIdOfStateHead, targetNode))
        mockNetwork.runNetwork()
        addParticipantFuture.getOrThrow()
    }


}