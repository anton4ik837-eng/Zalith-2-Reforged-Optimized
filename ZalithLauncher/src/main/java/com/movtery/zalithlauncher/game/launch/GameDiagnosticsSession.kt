/*
 * Zalith Launcher 2
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */

package com.movtery.zalithlauncher.game.launch

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects low-overhead device/process telemetry while the game JVM is running.
 * Samples are written to the launcher logs directory and included in the existing
 * "pack logs" archive. No account identifiers, tokens, command-line arguments or
 * environment variable values are recorded here.
 */
internal class GameDiagnosticsSession private constructor(
    private val context: Context,
    private val output: File,
    private val sourceGameLog: File,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sampler: Job? = null
    private var previousWallTime = SystemClock.elapsedRealtime()
    private var previousCpuTime = Process.getElapsedCpuTime()

    init {
        output.parentFile?.mkdirs()
        output.appendText("# Zalith game performance diagnostics\n")
        output.appendText("# Sampling interval: 5000 ms\n")
        writeDeviceInfo()
        writeSample("session_start")
        sampler = scope.launch {
            while (isActive) {
                delay(SAMPLE_INTERVAL_MS)
                writeSample("periodic")
            }
        }
        Logger.info(TAG, "Performance diagnostics started: ${output.name}")
    }

    private fun writeDeviceInfo() {
        val lines = listOf(
            "manufacturer=${Build.MANUFACTURER}",
            "model=${Build.MODEL}",
            "device=${Build.DEVICE}",
            "hardware=${Build.HARDWARE}",
            "soc_model=${if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "unavailable"}",
            "android_release=${Build.VERSION.RELEASE}",
            "sdk_int=${Build.VERSION.SDK_INT}",
            "supported_abis=${Build.SUPPORTED_ABIS.joinToString(",")}",
            "available_processors=${java.lang.Runtime.getRuntime().availableProcessors()}",
            "process_id=${Process.myPid()}",
            "java_version=${System.getProperty("java.version") ?: "unknown"}",
            "java_vm=${System.getProperty("java.vm.name") ?: "unknown"}",
        )
        synchronized(output) {
            output.appendText(lines.joinToString(separator = "\n", prefix = "[device]\n", postfix = "\n"))
        }
    }

    private fun writeSample(event: String) {
        runCatching {
            val now = SystemClock.elapsedRealtime()
            val cpuNow = Process.getElapsedCpuTime()
            val wallDelta = (now - previousWallTime).coerceAtLeast(1L)
            val cpuDelta = (cpuNow - previousCpuTime).coerceAtLeast(0L)
            val cpuPercent = cpuDelta.toDouble() / wallDelta.toDouble() * 100.0
            previousWallTime = now
            previousCpuTime = cpuNow

            val javaRuntime = Runtime.getRuntime()
            val javaUsed = javaRuntime.totalMemory() - javaRuntime.freeMemory()
            val javaCommitted = javaRuntime.totalMemory()
            val javaMax = javaRuntime.maxMemory()

            val processMemory = Debug.MemoryInfo()
            Debug.getMemoryInfo(processMemory)

            val systemMemory = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .getMemoryInfo(systemMemory)

            val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus.toString()
            } else {
                "unsupported"
            }

            val procStatus = readProcStatus()
            val line = buildString {
                append("elapsed_ms=").append(now)
                append(" event=").append(event)
                append(" process_cpu_percent=").append(String.format(Locale.US, "%.1f", cpuPercent))
                append(" process_cpu_time_ms=").append(cpuNow)
                append(" java_heap_used_mb=").append(javaUsed / MB)
                append(" java_heap_committed_mb=").append(javaCommitted / MB)
                append(" java_heap_max_mb=").append(javaMax / MB)
                append(" dalvik_pss_mb=").append(processMemory.dalvikPss / 1024)
                append(" native_pss_mb=").append(processMemory.nativePss / 1024)
                append(" total_pss_mb=").append(processMemory.totalPss / 1024)
                append(" native_heap_allocated_mb=").append(Debug.getNativeHeapAllocatedSize() / MB)
                append(" native_heap_free_mb=").append(Debug.getNativeHeapFreeSize() / MB)
                append(" system_avail_ram_mb=").append(systemMemory.availMem / MB)
                append(" system_total_ram_mb=").append(systemMemory.totalMem / MB)
                append(" system_low_memory=").append(systemMemory.lowMemory)
                append(" thermal_status=").append(thermalStatus)
                append(" vm_rss_kb=").append(procStatus["VmRSS"] ?: "na")
                append(" vm_hwm_kb=").append(procStatus["VmHWM"] ?: "na")
                append(" threads=").append(procStatus["Threads"] ?: "na")
            }
            synchronized(output) {
                output.appendText(line + "\n")
            }
        }.onFailure { error ->
            Logger.warning(TAG, "Unable to collect a performance sample", error)
        }
    }

    private fun readProcStatus(): Map<String, String> = runCatching {
        File("/proc/self/status").useLines { lines ->
            lines.mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) null
                else line.substring(0, separator) to line.substring(separator + 1).trim()
            }.filter { (key, _) -> key == "VmRSS" || key == "VmHWM" || key == "Threads" }
                .toMap()
        }
    }.getOrDefault(emptyMap())

    suspend fun stop(exitCode: Int) {
        sampler?.cancelAndJoin()
        writeSample("session_end")
        val copiedGameLog = runCatching {
            if (sourceGameLog.isFile) {
                val destination = File(output.parentFile, "game_output_${output.nameWithoutExtension.removePrefix("game_diagnostics_")}.log")
                sourceGameLog.copyTo(destination, overwrite = true)
                destination.name
            } else null
        }.getOrNull()
        synchronized(output) {
            output.appendText("# game_exit_code=$exitCode\n")
            output.appendText("# copied_game_log=${copiedGameLog ?: "not_available"}\n")
        }
        scope.coroutineContext[Job]?.cancel()
        Logger.info(TAG, "Performance diagnostics saved: ${output.absolutePath}")
    }

    companion object {
        private const val TAG = "GameDiagnostics"
        private const val SAMPLE_INTERVAL_MS = 5_000L
        private const val MB = 1024L * 1024L

        fun start(context: Context, gameLog: File): GameDiagnosticsSession {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(PathManager.DIR_LAUNCHER_LOGS, "game_diagnostics_$timestamp.log")
            return GameDiagnosticsSession(context.applicationContext, file, gameLog)
        }
    }
}
