package com.freeform.unbounded

/** 先重启 producer，最后重启消费者 SystemUI；所有目标共用一次 ROOT 会话。 */
internal object RestartPlan {
    fun ordered(scopes: List<RestartScope>): List<RestartScope> =
        scopes.distinctBy(RestartScope::id).sortedBy { it == RestartScopes.SYSTEM_UI }

    fun script(scopes: List<RestartScope>): String {
        val targets = ordered(scopes)
        require(targets.all { it in RestartScopes.allStatic })
        return """
            find_pids() {
                ps -A -o PID,NAME | awk -v pkg="${'$'}1" 'NR > 1 && (${'$'}2 == pkg || index(${'$'}2, pkg ":") == 1) {print ${'$'}1}'
            }
            restart_target() {
                id="${'$'}1"
                pkg="${'$'}2"
                pids="${'$'}(find_pids "${'$'}pkg")"
                if [ -z "${'$'}pids" ]; then
                    echo "FREEFORM_RESULT ${'$'}id inactive"
                    return
                fi
                kill -TERM ${'$'}pids 2>/dev/null
                attempts=0
                while [ "${'$'}attempts" -lt 20 ]; do
                    remaining=""
                    for pid in ${'$'}pids; do
                        [ -d "/proc/${'$'}pid" ] && remaining="${'$'}remaining ${'$'}pid"
                    done
                    [ -z "${'$'}remaining" ] && break
                    sleep 0.1
                    attempts=${'$'}((attempts + 1))
                done
                [ -n "${'$'}remaining" ] && kill -KILL ${'$'}remaining 2>/dev/null
                sleep 0.1
                for pid in ${'$'}pids; do
                    if [ -d "/proc/${'$'}pid" ]; then
                        echo "FREEFORM_RESULT ${'$'}id failed"
                        return
                    fi
                done
                echo "FREEFORM_RESULT ${'$'}id restarted"
            }
            if [ "${'$'}(id -u)" != 0 ]; then exit 1; fi
        """.trimIndent() + "\n" + targets.joinToString("\n") { "restart_target '${it.id}' '${it.packageName}'" }
    }

    /** 只有完整结果行才能认定执行完成，进程退出码不能代替九项结果。 */
    fun results(output: String, scopes: List<RestartScope>): Map<RestartScope, String> {
        val lines = output.lineSequence().mapNotNull {
            Regex("^FREEFORM_RESULT ([a-z_]+) (restarted|inactive|failed)$").matchEntire(it.trim())
        }.associate { it.groupValues[1] to it.groupValues[2] }
        return ordered(scopes).associateWith { lines[it.id] ?: "failed" }
    }
}
