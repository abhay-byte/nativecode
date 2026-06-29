package com.ivarna.nativecode.core.utils

import android.content.Context
import android.content.pm.PackageManager
import com.ivarna.nativecode.core.model.BackgroundTask
import com.ivarna.nativecode.core.model.BackgroundTaskStatus
import com.ivarna.nativecode.core.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages application state and package detection
 */
object StateManager {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    // UI Refresh Trigger
    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger.asStateFlow()
    
    // Project operation results
    private val _gitDiffResult = MutableStateFlow<String?>(null)
    val gitDiffResult: StateFlow<String?> = _gitDiffResult.asStateFlow()
    
    private val _apkListResult = MutableStateFlow<List<String>?>(null)
    val apkListResult: StateFlow<List<String>?> = _apkListResult.asStateFlow()
    
    private val _directoryListResult = MutableStateFlow<List<String>?>(null)
    val directoryListResult: StateFlow<List<String>?> = _directoryListResult.asStateFlow()
    
    private val _backgroundTasks = MutableStateFlow<List<BackgroundTask>>(emptyList())
    val backgroundTasks: StateFlow<List<BackgroundTask>> = _backgroundTasks.asStateFlow()
    
    private val _pendingSharedImageUri = MutableStateFlow<String?>(null)
    val pendingSharedImageUri: StateFlow<String?> = _pendingSharedImageUri.asStateFlow()
    
    fun triggerRefresh() {
        _refreshTrigger.value += 1
    }
    
    fun setPendingSharedImageUri(uri: String?) {
        _pendingSharedImageUri.value = uri
    }
    
    fun setGitDiffResult(result: String?) {
        _gitDiffResult.value = result
    }
    
    fun setApkListResult(result: List<String>?) {
        _apkListResult.value = result
    }
    
    fun setDirectoryListResult(result: List<String>?) {
        _directoryListResult.value = result
    }
    
    fun addBackgroundTask(task: BackgroundTask) {
        _backgroundTasks.value += task
    }
    
    fun updateBackgroundTask(taskId: String, status: BackgroundTaskStatus, result: String? = null) {
        _backgroundTasks.value = _backgroundTasks.value.map { 
            if (it.id == taskId) it.copy(status = status, result = result) else it 
        }
    }
    
    fun removeBackgroundTask(taskId: String) {
        _backgroundTasks.value = _backgroundTasks.value.filter { it.id != taskId }
    }
    
    /**
     * Check if a package is installed
     */
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            android.util.Log.d("StateManager", "Package $packageName is installed")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            android.util.Log.d("StateManager", "Package $packageName not found: ${e.message}")
            false
        } catch (e: Exception) {
            android.util.Log.e("StateManager", "Error checking package $packageName", e)
            false
        }
    }
    
    /**
     * Get installed package version
     */
    fun getPackageVersion(context: Context, packageName: String): String? {
        return try {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    /**
     * Check if Termux is installed with minimum version
     */
    fun isTermuxInstalled(context: Context): Boolean {
        return isPackageInstalled(context, "com.termux")
    }
    
    /**
     * Check if Termux:X11 is installed
     */
    fun isTermuxX11Installed(context: Context): Boolean {
        val result = isPackageInstalled(context, "com.termux.x11")
        android.util.Log.d("StateManager", "isTermuxX11Installed: $result")
        return result
    }
    
    /**
     * Get Termux version
     */
    fun getTermuxVersion(context: Context): String {
        return getPackageVersion(context, "com.termux") ?: "Not Installed"
    }
    
    /**
     * Get Termux:X11 version
     */
    fun getTermuxX11Version(context: Context): String {
        return getPackageVersion(context, "com.termux.x11") ?: "Not Installed"
    }
    
    /**
     * Check if Termux environment has been initialized
     */
    fun isTermuxInitialized(context: Context): Boolean {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("termux_initialized", false)
    }
    
    /**
     * Mark Termux environment as initialized
     */
    fun setTermuxInitialized(context: Context, initialized: Boolean) {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("termux_initialized", initialized).apply()
    }
    
    /**
     * Check if Termux tweaks have been applied
     */
    fun isTweaksApplied(context: Context): Boolean {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("tweaks_applied", false)
    }
    
    /**
     * Mark Termux tweaks as applied
     */
    fun setTweaksApplied(context: Context, applied: Boolean) {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("tweaks_applied", applied).apply()
    }
    
    /**
     * Check if a distro is installed
     */
    fun isDistroInstalled(context: Context, distroId: String): Boolean {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("distro_${distroId}_installed", false)
    }
    
    /**
     * Mark a distro as installed
     */
    fun setDistroInstalled(context: Context, distroId: String, installed: Boolean) {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("distro_${distroId}_installed", installed).apply()
        android.util.Log.d("StateManager", "Distro $distroId installation status set to: $installed")
    }
    
    /**
     * Get all installed distros
     */
    fun getInstalledDistros(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        return prefs.all.keys
            .filter { it.startsWith("distro_") && it.endsWith("_installed") }
            .filter { prefs.getBoolean(it, false) }
            .map { it.removePrefix("distro_").removeSuffix("_installed") }
            .toSet()
    }
        /**
     * Clear all state associated with a distro
     */
    fun clearDistroState(context: Context, distroId: String) {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Remove installation status
        editor.remove("distro_${distroId}_installed")
        
        // Remove GUI running status
        editor.remove("distro_${distroId}_gui_running")
        
        // Remove all component statuses for this distro
        val allKeys = prefs.all.keys
        val componentPrefix = "distro_${distroId}_component_"
        
        for (key in allKeys) {
            if (key.startsWith(componentPrefix)) {
                editor.remove(key)
            }
        }
        
        editor.apply()
        android.util.Log.d("StateManager", "Cleared all state for distro: $distroId")
    }

    /**
     * Check if onboarding has been completed
     */
    fun isOnboardingComplete(context: Context): Boolean {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("onboarding_complete", false)
    }
    
    /**
     * Mark onboarding as complete
     */
    fun setOnboardingComplete(context: Context, complete: Boolean) {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_complete", complete).apply()
        android.util.Log.d("StateManager", "Onboarding completion set to: $complete")
    }
    
    /**
     * Check if Termux connection fix has been applied
     */
    fun isConnectionFixed(context: Context): Boolean {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("connection_fixed", false)
    }
    
    /**
     * Mark connection fix as applied
     */
    fun setConnectionFixed(context: Context, fixed: Boolean) {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("connection_fixed", fixed).apply()
        android.util.Log.d("StateManager", "Connection fix status set to: $fixed")
    }

    /**
     * Get total package size including app, data, and cache
     */
    fun getPackageSize(context: Context, packageName: String): String {
        return try {
            
            // Fallback: Calculate via directory sizes (less accurate but works without permission)
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            var totalSize = 0L
            
            // Add APK size
            val apkFile = java.io.File(appInfo.publicSourceDir)
            totalSize += apkFile.length()
            
            // Try to estimate data directory size
            try {
                val dataDir = java.io.File(appInfo.dataDir)
                totalSize += getDirectorySize(dataDir)
            } catch (e: Exception) {
                android.util.Log.d("StateManager", "Could not access data dir for $packageName: ${e.message}")
            }
            
            val totalGb = totalSize / (1024.0 * 1024.0 * 1024.0)
            val totalMb = totalSize / (1024.0 * 1024.0)
            
            if (totalGb >= 1.0) {
                "%.2f GB".format(totalGb)
            } else {
                "%.0f MB".format(totalMb)
            }
        } catch (e: Exception) {
            android.util.Log.e("StateManager", "Error getting package size for $packageName", e)
            "Unknown"
        }
    }
    
    /**
     * Calculate directory size recursively
     */
    private fun getDirectorySize(directory: java.io.File): Long {
        var size = 0L
        try {
            if (directory.exists()) {
                val files = directory.listFiles()
                if (files != null) {
                    for (file in files) {
                        size += if (file.isDirectory) {
                            getDirectorySize(file)
                        } else {
                            file.length()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("StateManager", "Error calculating directory size: ${e.message}")
        }
        return size
    }

    /**
     * Check if a script has been successfully executed
     */
    fun getScriptStatus(context: Context, scriptName: String): Boolean {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("script_${scriptName}_success", false)
    }

    /**
     * Set script execution status
     */
    fun setScriptStatus(context: Context, scriptName: String, success: Boolean) {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("script_${scriptName}_success", success).apply()
        android.util.Log.d("StateManager", "Script $scriptName status set to: $success")
    }
    
    /**
     * Check if Unexpected Keyboard is installed
     */
    fun isUnexpectedKeyboardInstalled(context: Context): Boolean {
        return isPackageInstalled(context, "juloo.keyboard2")
    }
    
    /**
     * Get Unexpected Keyboard version
     */
    fun getUnexpectedKeyboardVersion(context: Context): String {
        return getPackageVersion(context, "juloo.keyboard2") ?: "Not Installed"
    }
    
    /**
     * Check if a distro component is installed
     */
    fun isComponentInstalled(context: Context, distroId: String, componentId: String): Boolean {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("distro_${distroId}_component_${componentId}", false)
    }

    /**
     * Mark a distro component as installed
     */
    fun setComponentInstalled(context: Context, distroId: String, componentId: String, installed: Boolean) {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("distro_${distroId}_component_${componentId}", installed).apply()
        android.util.Log.d("StateManager", "Distro $distroId component $componentId status set to: $installed")
    }
    
    /**
     * Check if GUI is running for a distro
     */
    fun isGuiRunning(context: Context, distroId: String): Boolean {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("distro_${distroId}_gui_running", false)
    }
    
    /**
     * Set GUI running state for a distro
     */
    fun setGuiRunning(context: Context, distroId: String, running: Boolean) {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("distro_${distroId}_gui_running", running).apply()
        android.util.Log.d("StateManager", "Distro $distroId GUI running status set to: $running")
        // Trigger UI refresh
        triggerRefresh()
    }
    
    /**
     * Get all distros with GUI running
     */
    fun getDistrosWithGuiRunning(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        return prefs.all.keys
            .filter { it.startsWith("distro_") && it.endsWith("_gui_running") }
            .filter { prefs.getBoolean(it, false) }
            .map { it.removePrefix("distro_").removeSuffix("_gui_running") }
            .toSet()
    }

    /**
     * Get which GUI type is running for a distro ("xfce4", "kde", or "")
     */
    fun getGuiRunningType(context: Context, distroId: String): String {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        return prefs.getString("distro_${distroId}_gui_type", "") ?: ""
    }

    /**
     * Set which GUI type is running for a distro ("xfce4", "kde", or "")
     */
    fun setGuiRunningType(context: Context, distroId: String, type: String) {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        prefs.edit().putString("distro_${distroId}_gui_type", type).apply()
        android.util.Log.d("StateManager", "Distro $distroId GUI type set to: $type")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROJECT MANAGEMENT (JSON-based with metadata)
    // ─────────────────────────────────────────────────────────────────────────

    private const val PROJECTS_PREFS = "nativecode_projects"
    private const val PROJECTS_JSON_KEY = "projects_json"

    /**
     * Get all saved projects with metadata
     */
    fun getProjects(context: Context): List<Project> {
        val prefs = context.getSharedPreferences(PROJECTS_PREFS, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(PROJECTS_JSON_KEY, null)
        return if (jsonStr != null) {
            try {
                json.decodeFromString<List<Project>>(jsonStr)
            } catch (e: Exception) {
                android.util.Log.e("StateManager", "Failed to parse projects JSON, falling back to paths", e)
                // Fallback to old path-based storage
                migrateProjectsFromPaths(context)
            }
        } else {
            // Try old path-based storage
            migrateProjectsFromPaths(context)
        }
    }

    /**
     * Migrate from old path-based storage to new JSON format
     */
    private fun migrateProjectsFromPaths(context: Context): List<Project> {
        val prefs = context.getSharedPreferences(PROJECTS_PREFS, Context.MODE_PRIVATE)
        val oldPaths = prefs.getStringSet("project_paths", emptySet()) ?: emptySet()
        val projects = oldPaths.map { path ->
            Project(
                path = path,
                name = path.substringAfterLast("/").takeIf { it.isNotEmpty() } ?: "Root",
                category = "General"
            )
        }
        if (projects.isNotEmpty()) {
            saveProjects(context, projects)
        }
        return projects
    }

    /**
     * Save all projects
     */
    fun saveProjects(context: Context, projects: List<Project>) {
        val prefs = context.getSharedPreferences(PROJECTS_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(PROJECTS_JSON_KEY, json.encodeToString(projects)).apply()
    }

    /**
     * Add or update a project
     */
    fun addProject(context: Context, project: Project) {
        val projects = getProjects(context).toMutableList()
        projects.removeAll { it.path == project.path }
        projects.add(project)
        saveProjects(context, projects)
    }

    /**
     * Remove a project by path
     */
    fun removeProject(context: Context, path: String) {
        val projects = getProjects(context).filter { it.path != path }
        saveProjects(context, projects)
    }

    /**
     * Update project category
     */
    fun setProjectCategory(context: Context, path: String, category: String) {
        val projects = getProjects(context).map { 
            if (it.path == path) it.copy(category = category) else it 
        }
        saveProjects(context, projects)
    }

    /**
     * Update project git remote URL
     */
    fun setProjectGitRemote(context: Context, path: String, gitRemoteUrl: String?) {
        val projects = getProjects(context).map { 
            if (it.path == path) it.copy(gitRemoteUrl = gitRemoteUrl) else it 
        }
        saveProjects(context, projects)
    }

    /**
     * Legacy: Get saved project paths (for backward compatibility)
     */
    fun getProjectPaths(context: Context): Set<String> {
        return getProjects(context).map { it.path }.toSet()
    }

    /**
     * Legacy: Add a project path (for backward compatibility)
     */
    fun addProjectPath(context: Context, path: String) {
        val projects = getProjects(context).toMutableList()
        if (projects.none { it.path == path }) {
            projects.add(Project(
                path = path,
                name = path.substringAfterLast("/").takeIf { it.isNotEmpty() } ?: "Root",
                category = "General"
            ))
            saveProjects(context, projects)
        }
    }

    /**
     * Legacy: Remove a project path (for backward compatibility)
     */
    fun removeProjectPath(context: Context, path: String) {
        removeProject(context, path)
    }

    /**
     * Get hardware acceleration type for a distro ("auto", "virgl", "turnip", "none")
     */
    fun getHardwareAccelType(context: Context, distroId: String): String {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        return prefs.getString("distro_${distroId}_hw_accel", "auto") ?: "auto"
    }

    /**
     * Set hardware acceleration type for a distro
     */
    fun setHardwareAccelType(context: Context, distroId: String, type: String) {
        val prefs = context.getSharedPreferences("nativecode_state", Context.MODE_PRIVATE)
        prefs.edit().putString("distro_${distroId}_hw_accel", type).apply()
        android.util.Log.d("StateManager", "Distro $distroId hardware accel set to: $type")
    }

}
