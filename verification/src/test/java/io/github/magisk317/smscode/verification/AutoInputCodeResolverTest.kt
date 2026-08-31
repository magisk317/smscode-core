package io.github.magisk317.smscode.verification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutoInputCodeResolverTest {
    @Test
    fun resolvesPrefixFromChinaMobileInstruction() {
        val body = "【验证码】123456，请于办理页面输入前四位 验证码数字。验证码有效期5分钟。"

        assertEquals("1234", AutoInputCodeResolver.resolve("123456", body))
    }

    @Test
    fun resolvesSuffixUsingArabicLength() {
        assertEquals(
            "3456",
            AutoInputCodeResolver.resolve("123456", "请填写后4位验证码"),
        )
    }

    @Test
    fun keepsFullCodeWhenInstructionIsAbsentOrInvalid() {
        assertEquals("123456", AutoInputCodeResolver.resolve("123456", "验证码123456"))
        assertEquals("123456", AutoInputCodeResolver.resolve("123456", "请输入前七位验证码"))
    }
}
