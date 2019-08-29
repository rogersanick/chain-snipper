package com.template.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

// ************
// * Contract *
// ************
class SimpleContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.contracts.SimpleContract"
    }

    override fun verify(tx: LedgerTransaction) = requireThat {

        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value) {
            is Commands.Start -> requireThat {
                "There should be no input state." using (tx.inputStates.isEmpty())
                "There should be one output state." using (tx.outputStates.size == 1)
            }
            is Commands.Add -> requireThat {
                "There should be one input state." using (tx.inputStates.size == 1)
                "There should be one output state." using (tx.outputStates.size == 1)
            }
            is Commands.Remove -> requireThat {
                "There should be one input state." using (tx.inputStates.size == 1)
                "There should be no output state." using (tx.outputStates.isEmpty())
            }
        }
    }

    interface Commands : CommandData {
        class Start : Commands
        class Add : Commands
        class Remove: Commands
    }
}