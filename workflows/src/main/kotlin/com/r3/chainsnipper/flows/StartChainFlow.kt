package com.r3.chainsnipper.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.chainsnipper.contracts.SimpleContract
import com.r3.chainsnipper.states.SimpleState
import com.r3.chainsnipper.states.newState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class StartChainFlow : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {
        val origin = serviceHub.myInfo.legalIdentities.first()
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        val state = SimpleState(0, listOf(origin))
        val command = Command(SimpleContract.Commands.Start(), listOf(origin.owningKey))

        val txBuilder = TransactionBuilder(notary)
                .addOutputState(state)
                .addCommand(command)
        txBuilder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(txBuilder)

        return subFlow(FinalityFlow(stx, emptyList()))
    }
}

@InitiatedBy(StartChainFlow::class)
class StartChainFlowResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {}
}
