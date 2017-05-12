package com.github.sybila.fixedpoint

import com.github.sybila.model.Model
import com.github.sybila.model.MutableStateMap
import com.github.sybila.model.StateMap
import com.sun.org.apache.xpath.internal.operations.Mod
import io.reactivex.Observable
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias State = Int

internal fun <Param : Any> Observable<StateMap<Param>>.fixedPoint(
        model: Model<Param>,
        executor: ExecutorService,
        stepFunction: Model<Param>.(MutableStateMap<Param>, State) -> Iterable<State>
) : Observable<StateMap<Param>> = this.map { initial ->

    val result = MutableStateMap<Param>(model.stateCount)
    val queue = BfsStateQueue<State>()

    // copy initial states
    initial.entries.forEach { (k, v) -> result.increaseKey(k, v, model) }
    queue.add(initial.states, null)

    // iterate while something needs to be recomputed
    while (queue.isNotEmpty()) {
        queue.remove().map { state ->
            state to executor.submit<Iterable<State>> { model.stepFunction(result, state) }
        }.forEach { (from, future) ->
            queue.add(future.get(), from)
        }
    }

    result
}

open class FixedPointAlgorithm<Param : Any>(
        protected val model: Model<Param>,
        protected val executor: ExecutorService
) {

    fun Observable<StateMap<Param>>.next(
            stepFunction: Model<Param>.(StateMap<Param>, State) -> Param?,
            future: Boolean = true
    ) : Observable<StateMap<Param>> = this.map { initial ->

        // find dependencies
        val recompute: Set<Int> = initial.states
                .flatMap { model.run { it.step(!future).map { it.first } } }
                .toSet()

        val result = MutableStateMap<Param>(model.stateCount)

        recompute.map { state ->
            state to executor.submit<Param?> { model.stepFunction(initial, state) }
        }.forEach { (state, future) ->
            future.get()?.let { params ->
                result.increaseKey(state, params, model)
            }
        }

        result
    }

}

class HUCTLpAlgorithm<Param : Any>(model: Model<Param>, executor: ExecutorService)
    : FixedPointAlgorithm<Param>(model, executor) {

    fun Observable<StateMap<Param>>.existsNext(timeFlow: Boolean = true): Observable<StateMap<Param>>
            = this.next(future = timeFlow, stepFunction = { initial, state ->
        state.step(timeFlow).fold<Pair<Int, Param>, Param?>(null) { witness, (successor, bound) ->
            witness or (initial[successor] and bound)
        }
    })

    fun Observable<StateMap<Param>>.allNext(timeFlow: Boolean = true): Observable<StateMap<Param>>
            = this.next(future = timeFlow, stepFunction = { initial, state ->
        state.step(timeFlow).fold<Pair<Int, Param>, Param?>(universe) { witness, (successor, bound) ->
            witness and (initial[successor] or bound.not())
        }
    })

}