package com.myfitnessmeals.app.observability

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

interface OperationalMetricsRecorder {
    fun increment(counter: String, delta: Long = 1)

    fun value(counter: String): Long
}

class InMemoryOperationalMetricsRecorder : OperationalMetricsRecorder {
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    override fun increment(counter: String, delta: Long) {
        counters.computeIfAbsent(counter) { AtomicLong(0) }.addAndGet(delta)
    }

    override fun value(counter: String): Long {
        return counters[counter]?.get() ?: 0L
    }
}
