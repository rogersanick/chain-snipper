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
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class ReissueFlow(val linearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val origin = serviceHub.myInfo.legalIdentities.first()
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // GET HEAD OF CHAIN
        val stateAndRef = serviceHub.vaultService.queryBy<SimpleState>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))).states.single()

        // REMOVE FROM LEDGER
        val txBuilderRemove = TransactionBuilder(notary)
                .addInputState(stateAndRef)
                .addCommand(Command(SimpleContract.Commands.Remove(), listOf(origin.owningKey)))
        txBuilderRemove.verify(serviceHub)
        val txRemove = serviceHub.signInitialTransaction(txBuilderRemove)
        subFlow(FinalityFlow(txRemove, emptyList()))

        // REISSUE ONTO LEDGER
        val txBuilderStart = TransactionBuilder(notary)
                .addOutputState(stateAndRef.state.data)
                .addCommand(Command(SimpleContract.Commands.Start(), listOf(origin.owningKey)))
        txBuilderStart.verify(serviceHub)
        val txStart = serviceHub.signInitialTransaction(txBuilderStart)
        return subFlow(FinalityFlow(txStart, emptyList()))
    }
}

@InitiatedBy(ReissueFlow::class)
class ReissueFlowResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {}
}
