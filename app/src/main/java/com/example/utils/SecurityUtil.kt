package com.example.utils
import java.io.File
import java.io.IOException

object SecurityUtil {
    fun safeResolve(workspaceRoot: File, userPath: String): File {
        val rootPath = workspaceRoot.canonicalPath
        val resolved = File(workspaceRoot, userPath).canonicalFile
        if (!resolved.path.startsWith(rootPath)) {
            throw SecurityException("SECURITY ERROR: Path traversal attempt detected: $userPath")
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
