package com.hmx.harness.ui.chat.components

import com.hmx.harness.core.ChatMessage
import com.hmx.harness.core.ToolCallData

/** Completed results take precedence over delayed running-state updates. */
internal fun activeToolIds(
    calls: List<ToolCallData>,
    results: Map<String, ChatMessage?>,
    runningIds: Set<String>,
): Set<String> = calls.mapNotNull { call ->
    call.id.takeIf { it in runningIds && results[it] == null }
}.toSet()
