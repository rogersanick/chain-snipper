package com.r3.chainsnipper.flows

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef

fun StateAndRef<ContractState>.getDeterministicSalt(): ByteArray {
    return (this.ref.index.toBigInteger() xor this.ref.index.toBigInteger()).toByteArray()
}