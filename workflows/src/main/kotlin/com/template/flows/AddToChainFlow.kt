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
class AddToChainFlow(val linearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val origin = serviceHub.myInfo.legalIdentities.first()
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        val lastStateAndRef = serviceHub.vaultService.queryBy<SimpleState>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))).states.single()
        val command = Command(SimpleContract.Commands.Add(), listOf(origin.owningKey))

        val nextState = newState(lastStateAndRef.state.data)

        val txBuilder = TransactionBuilder(notary)
                .addInputState(lastStateAndRef)
                .addOutputState(nextState)
                .addCommand(command)
        txBuilder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(txBuilder)
        return subFlow(FinalityFlow(stx, emptyList()))
    }
}

@InitiatedBy(AddToChainFlow::class)
class AddToChainFlowResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {}
}
