/**
 *  File Name: AsyncTaskCompat.kt
 *  Project Name: DashCamera
 *  Copyright @ Hanlin Hu 2019
 */

package com.example.dashcamera

import android.os.AsyncTask

/**
 * Helper for accessing features in [AsyncTask]
 * introduced after API level 4 in a backwards compatible fashion.
 */

object AsyncTaskCompat {
    /**
     * Executes the task with the specified parameters, allowing multiple tasks to run in parallel
     * on a pool of threads managed by [AsyncTask].
     *
     * @param task   The [AsyncTask] to execute.
     * @param params The parameters of the task.
     * @return the instance of AsyncTask.
     */
    fun <Params, Progress, Result> executeParallel(
        task: AsyncTask<Params, Progress, Result>?, vararg params: Params
    ): AsyncTask<Params, Progress, Result> {
        requireNotNull(task) { "task can not be null" }

        // From API 11 onwards, we need to manually select the THREAD_POOL_EXECUTOR
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, *params)

        return task
    }
}
