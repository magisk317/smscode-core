package io.github.magisk317.smscode.verification

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

object AutoInputAccessibilityNodeHelper {
    data class Result(
        val success: Boolean,
        val strategy: String,
        val reason: String,
        val windowPackage: String = "",
    )

    fun performAutoInput(
        root: AccessibilityNodeInfo?,
        code: String,
        autoEnter: Boolean,
    ): Result {
        val activeRoot = root ?: return Result(false, "none", "no_active_window")
        val windowPackage = activeRoot.packageName?.toString().orEmpty()
        XLog.w(
            "Accessibility active window: pkg=%s class=%s",
            windowPackage.ifBlank { "<unknown>" },
            activeRoot.className?.toString().orEmpty().ifBlank { "<unknown>" },
        )

        val focusedNode = findFocusedEditableNode(activeRoot)
        if (focusedNode != null) {
            val focusedResult = setNodeText(focusedNode, code, autoEnter)
            if (focusedResult.success) {
                return focusedResult.copy(strategy = "focused_node", windowPackage = windowPackage)
            }
            XLog.w("Accessibility focused-node input failed: reason=%s", focusedResult.reason)
        }

        val editableNodes = collectEditableNodes(activeRoot)
        if (editableNodes.isEmpty()) {
            return Result(false, "none", "no_editable_node", windowPackage)
        }

        val groupedResult = fillEditableGroup(editableNodes, code, autoEnter)
        if (groupedResult.success) {
            return groupedResult.copy(windowPackage = windowPackage)
        }

        val singleResult = setNodeText(editableNodes.first(), code, autoEnter)
        if (singleResult.success) {
            return singleResult.copy(strategy = "best_editable_node", windowPackage = windowPackage)
        }

        return groupedResult.copy(windowPackage = windowPackage)
    }

    private fun findFocusedEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val inputFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (inputFocus != null && canSetText(inputFocus)) {
            return inputFocus
        }
        val accessibilityFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (accessibilityFocus != null && canSetText(accessibilityFocus)) {
            return accessibilityFocus
        }
        return null
    }

    private fun collectEditableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val result = ArrayList<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (canSetText(node)) {
                result += node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return result
            .distinctBy { buildNodeIdentity(it) }
            .sortedWith(
                compareByDescending<AccessibilityNodeInfo> { it.isFocused }
                    .thenBy { nodeTop(it) }
                    .thenBy { nodeLeft(it) },
            )
    }

    private fun fillEditableGroup(
        nodes: List<AccessibilityNodeInfo>,
        code: String,
        autoEnter: Boolean,
    ): Result {
        if (code.length !in 4..8) {
            return Result(false, "group_nodes", "group_strategy_not_applicable")
        }
        val charNodes = nodes.filter { nodeTextLength(it) <= 1 }
        if (charNodes.size < code.length) {
            return Result(false, "group_nodes", "not_enough_editable_nodes")
        }

        for ((index, char) in code.withIndex()) {
            val result = setNodeText(
                node = charNodes[index],
                text = char.toString(),
                autoEnter = autoEnter && index == code.lastIndex,
            )
            if (!result.success) {
                XLog.w("Accessibility grouped input failed at index=%d reason=%s", index, result.reason)
                return Result(false, "group_nodes", "group_set_text_failed")
            }
        }
        return Result(true, "group_nodes", "ok")
    }

    private fun setNodeText(
        node: AccessibilityNodeInfo,
        text: String,
        autoEnter: Boolean,
    ): Result {
        return runCatching {
            if (!node.isFocused) {
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                node.refresh()
            }
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val setTextSucceeded = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (!setTextSucceeded) {
                return Result(false, "set_text", "action_set_text_failed")
            }
            if (autoEnter) {
                tryImeEnter(node)
            }
            Result(true, "set_text", "ok")
        }.getOrElse { throwable ->
            XLog.w("Accessibility set text failed: %s", throwable.message ?: throwable.javaClass.simpleName)
            Result(false, "set_text", "set_text_exception")
        }
    }

    private fun tryImeEnter(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            XLog.w("Accessibility auto enter skipped: android version too low")
            return
        }
        val imeEnterActionId = runCatching {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
        }.getOrNull() ?: return
        if (node.actionList.none { it.id == imeEnterActionId }) {
            XLog.w("Accessibility auto enter skipped: ime action unsupported")
            return
        }
        val result = node.performAction(imeEnterActionId)
        XLog.w("Accessibility auto enter invoked: success=%s", result)
    }

    private fun canSetText(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        if (!node.isEnabled) return false
        if (node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }) return true
        if (node.isEditable) return true
        val className = node.className?.toString().orEmpty()
        return className.contains("EditText", ignoreCase = true)
    }

    private fun buildNodeIdentity(node: AccessibilityNodeInfo): String {
        val bounds = Rect().also(node::getBoundsInScreen)
        return listOf(
            node.viewIdResourceName.orEmpty(),
            node.className?.toString().orEmpty(),
            node.packageName?.toString().orEmpty(),
            bounds.left.toString(),
            bounds.top.toString(),
            bounds.right.toString(),
            bounds.bottom.toString(),
        ).joinToString("|")
    }

    private fun nodeTop(node: AccessibilityNodeInfo): Int = Rect().also(node::getBoundsInScreen).top

    private fun nodeLeft(node: AccessibilityNodeInfo): Int = Rect().also(node::getBoundsInScreen).left

    private fun nodeTextLength(node: AccessibilityNodeInfo): Int {
        val hintLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.length
        } else {
            null
        }
        return node.text?.length ?: hintLength ?: 0
    }
}
