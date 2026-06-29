package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.project.Project
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves the current user's name for stamping completed TODOs.
 *
 * Prefers the project's configured `git config user.name`; falls back to the OS user name.
 * Results are cached per project base path. [userName] may run a short-lived `git` subprocess,
 * so warm the cache from a background thread (see [io.github.j4sp3rtm.todomanager.TodoToolWindowPanel]).
 */
object GitUser {

    private val cache = ConcurrentHashMap<String, String>()

    /** Human-readable completion stamp, e.g. "done by Jasper on 2026-06-29". */
    fun doneSignature(project: Project): String =
        "done by ${userName(project)} on ${LocalDate.now()}"

    fun userName(project: Project): String {
        val base = project.basePath
        if (base != null) {
            cache[base]?.let { return it }
            gitUserName(base)?.let { name ->
                cache[base] = name
                return name
            }
        }
        return System.getProperty("user.name") ?: "unknown"
    }

    private fun gitUserName(dir: String): String? {
        return try {
            val process = ProcessBuilder("git", "config", "user.name")
                .directory(File(dir))
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy()
                return null
            }
            if (process.exitValue() == 0) output.ifBlank { null } else null
        } catch (_: Exception) {
            null
        }
    }
}
