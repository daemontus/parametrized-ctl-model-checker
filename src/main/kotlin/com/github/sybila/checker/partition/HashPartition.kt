package com.github.sybila.checker.partition

import com.github.sybila.checker.Model
import com.github.sybila.checker.Partition


class HashPartition<Params : Any>(
        override val partitionId: Int,
        override val partitionCount: Int,
        model: Model<Params>
) : Partition<Params>, Model<Params> by model {

    override fun Int.owner(): Int = this % partitionCount

}

fun <Params : Any> List<Model<Params>>.asHashPartitions(): List<HashPartition<Params>> {
    return this.mapIndexed { i, model -> HashPartition(i, this.size, model) }
}