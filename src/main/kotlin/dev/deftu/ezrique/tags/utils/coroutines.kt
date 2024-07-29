package dev.deftu.ezrique.tags.utils

import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

fun CoroutineScope.scheduleAtFixedRate(
    timeUnit: TimeUnit,
    initialDelay: Long = 0L,
    period: Long,
    action: () -> Unit
) = launch {
    delay(timeUnit.toMillis(initialDelay))
    while (isActive) {
        val startTime = System.currentTimeMillis()
        action()
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val delayTime = timeUnit.toMillis(period) - duration
        if (delayTime > 0) {
            delay(delayTime)
        }
    }
}

fun CoroutineContext.scheduleAtFixedRate(
    timeUnit: TimeUnit,
    initialDelay: Long = 0L,
    period: Long,
    action: () -> Unit
) = CoroutineScope(this).launch {
    delay(timeUnit.toMillis(initialDelay))
    while (isActive) {
        val startTime = System.currentTimeMillis()
        action()
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val delayTime = timeUnit.toMillis(period) - duration
        if (delayTime > 0) {
            delay(delayTime)
        }
    }
}
