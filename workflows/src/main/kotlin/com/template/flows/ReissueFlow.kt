package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.SimpleContract
import com.template.states.SimpleState
import com.template.states.newState
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
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {
        val origin = serviceHub.myInfo.legalIdentities.first()
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // GET HEAD OF CHAIN
        val lastStateAndRef = serviceHub.vaultService.queryBy<SimpleState>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))).states.single()

        // REMOVE FROM LEDGER
        val commandRemove = Command(SimpleContract.Commands.Remove(), listOf(origin.owningKey))
        val txBuilder1 = TransactionBuilder(notary)
                .addInputState(lastStateAndRef)
                .addCommand(commandRemove)
        txBuilder1.verify(serviceHub)
        val stx1 = serviceHub.signInitialTransaction(txBuilder1)
        subFlow(FinalityFlow(stx1, emptyList()))

        // REISSUE ONTO LEDGER
        val commandStart = Command(SimpleContract.Commands.Start(), listOf(origin.owningKey))
        val txBuilder2 = TransactionBuilder(notary)
                .addOutputState(lastStateAndRef.state.data)
                .addCommand(commandStart)
        txBuilder2.verify(serviceHub)
        val stx2 = serviceHub.signInitialTransaction(txBuilder2)
        return subFlow(FinalityFlow(stx2, emptyList()))
    }
}

@InitiatedBy(ReissueFlow::class)
class ReissueFlowResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {}
}
