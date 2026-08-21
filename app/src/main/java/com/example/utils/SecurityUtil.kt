package com.example.utils

import java.io.File
import java.io.IOException

object SecurityUtil {
    /**
     * Resolves a user-supplied or archive-relative path safely within the workspace root.
     * Prevents Path Traversal and prefix collision vulnerabilities (e.g. /workspace vs /workspace-evil).
     */
    fun safeResolve(workspaceRoot: File, userPath: String): File {
        val canonicalRoot = workspaceRoot.canonicalFile
        val resolved = File(canonicalRoot, userPath).canonicalFile
        
        val rootPath = canonicalRoot.path
        val rootPrefix = if (rootPath.endsWith(File.separator)) rootPath else rootPath + File.separator
        
        if (resolved.path != rootPath && !resolved.path.startsWith(rootPrefix)) {
            throw SecurityException("SECURITY ERROR: Path traversal attempt detected: '$userPath' resolved to '${resolved.path}' outside root '${canonicalRoot.path}'")
        }
        return resolved
    }
    
    /**
     * Escapes a string to be used safely as an argument in a shell command.
     */
    fun escapeShellArg(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }
}

