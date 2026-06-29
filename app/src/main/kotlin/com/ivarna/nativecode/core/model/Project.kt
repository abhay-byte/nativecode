package com.ivarna.nativecode.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Project(
    val path: String,
    val name: String = path.substringAfterLast("/").takeIf { it.isNotEmpty() } ?: "Root",
    val category: String = "General",
    val gitRemoteUrl: String? = null
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonStr: String): Project = json.decodeFromString(jsonStr)
        fun listFromJson(jsonStr: String): List<Project> = json.decodeFromString(jsonStr)
    }

    fun toJson(): String = json.encodeToString(this)
}

enum class ProjectCategory(val displayName: String) {
    GENERAL("General"),
    ANDROID("Android"),
    WEB("Web"),
    AI_ML("AI / ML"),
    DESKTOP("Desktop"),
    EMBEDDED("Embedded"),
    GAME_DEV("Game Dev"),
    DATA_SCIENCE("Data Science"),
    CYBERSEC("CyberSec"),
    OTHER("Other");

    companion object {
        fun fromString(value: String): ProjectCategory =
            entries.find { it.displayName.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) } ?: GENERAL
    }
}

enum class BackgroundTaskStatus { RUNNING, SUCCESS, FAILED }

@Serializable
data class BackgroundTask(
    val id: String,
    val name: String,
    val status: BackgroundTaskStatus = BackgroundTaskStatus.RUNNING,
    val result: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
