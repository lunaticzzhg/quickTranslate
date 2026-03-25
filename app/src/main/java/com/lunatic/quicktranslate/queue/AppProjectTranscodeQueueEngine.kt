package com.lunatic.quicktranslate.queue

import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeQueueEngine
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskExecutor
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppProjectTranscodeQueueEngine(
    private val appScope: CoroutineScope,
    private val taskRepository: ProjectTranscodeTaskRepository,
    private val taskExecutor: ProjectTranscodeTaskExecutor
) : ProjectTranscodeQueueEngine {
    private val started = AtomicBoolean(false)
    private val signalChannel = Channel<Unit>(Channel.CONFLATED)
    private val drainMutex = Mutex()

    override fun signal() {
        ensureStarted()
        signalChannel.trySend(Unit)
    }

    override suspend fun restoreAndSignal() {
        taskRepository.restoreRunningTasksToPending(
            updatedAtEpochMs = System.currentTimeMillis()
        )
        signal()
    }

    private fun ensureStarted() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        appScope.launch {
            for (signal in signalChannel) {
                drainQueue()
            }
        }
    }

    private suspend fun drainQueue() {
        drainMutex.withLock {
            while (true) {
                val now = System.currentTimeMillis()
                val task = taskRepository.claimNextPendingTask(now) ?: break
                val result = taskExecutor.execute(task)
                if (result.isSuccess) {
                    taskRepository.markTaskSucceeded(
                        taskId = task.id,
                        finishedAtEpochMs = System.currentTimeMillis()
                    )
                } else {
                    taskRepository.markTaskFailed(
                        taskId = task.id,
                        message = result.exceptionOrNull()?.message,
                        finishedAtEpochMs = System.currentTimeMillis()
                    )
                }
            }
        }
    }
}
