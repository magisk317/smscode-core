package io.github.magisk317.forward.sender

import android.content.Context
import io.github.magisk317.forward.engine.model.MsgInfo
import io.github.magisk317.forward.sender.config.MatrixSetting

object MatrixE2eeUtils {
    @Suppress("UNUSED_PARAMETER")
    suspend fun sendMsg(context: Context, setting: MatrixSetting, msgInfo: MsgInfo) {
        MatrixE2eeSenderProvider.getOrNull()?.let { sender ->
            sender.sendMsg(context, setting, msgInfo)
            return
        }
        MatrixUtils.sendMsg(setting, msgInfo)
    }
}
