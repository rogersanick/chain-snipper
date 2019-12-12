package com.r3.chainsnipper.states

import com.r3.chainsnipper.contracts.SimpleContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party

// *********
// * State *
// *********
@BelongsToContract(SimpleContract::class)
data class SimpleState(
        val index: Int,
        override val participants: List<Party>,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState

fun newState(state: SimpleState): SimpleState {
    return state.copy(index = state.index+1)
}