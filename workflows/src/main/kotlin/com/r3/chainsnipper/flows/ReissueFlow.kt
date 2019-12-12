package com.r3.chainsnipper.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.chainsnipper.contracts.SimpleContract
import com.r3.chainsnipper.states.SimpleState
import net.corda.core.contracts.Command
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.security.MessageDigest

// *********
// * Flows *
// *********
@StartableByRPC
class ReissueFlow(val linearId: UniqueIdentifier) : FlowLogic<List<SignedTransaction>>() {
    @Suspendable
    override fun call(): List<SignedTransaction> {
        val origin = serviceHub.myInfo.legalIdentities.first()
        val notary = serviceHub.networkMapCache.notaryIdentities.single()

        // GET HEAD OF CHAIN
        val stateAndRef = serviceHub.vaultService.queryBy<SimpleState>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))).states.single()
        val simpleState = stateAndRef.state.data
        val otherPartySessions = (simpleState.participants - ourIdentity).map { initiateFlow(it) }

        // BUILD AND SIGN TX TO REMOVE FROM LEDGER
        val txBuilderRemove = TransactionBuilder(notary)
                .addInputState(stateAndRef)
                .addCommand(Command(SimpleContract.Commands.Remove(), listOf(origin.owningKey)))
        txBuilderRemove.verify(serviceHub)
        val extinguishPtx = serviceHub.signInitialTransaction(txBuilderRemove)
        val extinguishFtx = subFlow(CollectSignaturesFlow(extinguishPtx, otherPartySessions))

        // BUILD AND SIGN TX TO REISSUE ONTO LEDGER
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val deterministicSaltHash = messageDigest.digest(stateAndRef.getDeterministicSalt())

        val txBuilderStart = TransactionBuilder(notary)
                .addOutputState(stateAndRef.state.data)
                .addCommand(Command(SimpleContract.Commands.Start(), listOf(origin.owningKey)))
                .setPrivacySalt(PrivacySalt(deterministicSaltHash))
        txBuilderStart.verify(serviceHub)
        val reissueTx = serviceHub.signInitialTransaction(txBuilderStart)
        val reissuePtx = subFlow(CollectSignaturesFlow(reissueTx, (simpleState.participants - ourIdentity).map { initiateFlow(it) }))

        // NOTARISE THE ISSUANCE TRANSACTION ONLY
        val notarySignatures = subFlow(NotaryFlow.Client(reissuePtx))
        val reissueFtx = reissuePtx + notarySignatures

        // DISTRIBUTE THE TXs SIMULTANEOUSLY
        serviceHub.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(reissueFtx, extinguishFtx))

        // UPDATE THE COUNTERPARTY
        otherPartySessions.forEach { SendTransactionFlow(it, extinguishFtx) }
        otherPartySessions.forEach { SendTransactionFlow(it, reissueFtx) }

        return listOf(extinguishFtx, reissueFtx)
    }

    private fun needsNotarySignature(stx: SignedTransaction): Boolean {
        val wtx = stx.tx
        val needsNotarisation = wtx.inputs.isNotEmpty() || wtx.references.isNotEmpty() || wtx.timeWindow != null
        return needsNotarisation && hasNoNotarySignature(stx)
    }

    private fun hasNoNotarySignature(stx: SignedTransaction): Boolean {
        val notaryKey = stx.tx.notary?.owningKey
        val signers = stx.sigs.asSequence().map { it.by }.toSet()
        return notaryKey?.isFulfilledBy(signers) != true
    }
}
