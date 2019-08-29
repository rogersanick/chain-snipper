package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.SimpleContract
import com.template.states.SimpleState
import com.template.states.newState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import javax.annotation.Signed

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class AddParticipantFlow(val linearId: UniqueIdentifier, val newParty: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val origin = serviceHub.myInfo.legalIdentities.first()
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        val stateAndRef = serviceHub.vaultService.queryBy<SimpleState>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))).states.single()
        val command = Command(SimpleContract.Commands.Add(), listOf(origin.owningKey, newParty.owningKey))

        val nextState = newState(stateAndRef.state.data).copy(participants = listOf(origin, newParty))

        val txBuilder = TransactionBuilder(notary)
                .addInputState(stateAndRef)
                .addOutputState(nextState)
                .addCommand(command)
        txBuilder.verify(serviceHub)

        val ptx = serviceHub.signInitialTransaction(txBuilder)
        val session = initiateFlow(newParty)
        val stx = subFlow(CollectSignaturesFlow(ptx, listOf(session)))
        return subFlow(FinalityFlow(stx, session))
    }
}

@InitiatedBy(AddParticipantFlow::class)
class AddParticipantFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat{}
        }
        val txWeJustSigned = subFlow(signedTransactionFlow)
        return subFlow(ReceiveFinalityFlow(counterpartySession, txWeJustSigned.id))
    }
}
