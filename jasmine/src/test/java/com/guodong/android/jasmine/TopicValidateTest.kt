package com.guodong.android.jasmine

import com.guodong.android.jasmine.util.validateTopicFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class TopicValidateTest {

    @Test
    fun testNormalTopic() {
        assertEquals(3, "ncs/9527/call_info".validateTopicFilter().size)
    }

    @Test
    fun testPlusTopic() {
        assertEquals(4, "ncs/9527/+/call_info".validateTopicFilter().size)
    }

    @Test
    fun testHashTopic() {
        assertEquals(4, "ncs/9527/call_info/#".validateTopicFilter().size)
    }

    @Test
    fun testIllegalTopic() {
        assertEquals(
            "非法主题：主题不能为空或者空白字符",
            assertThrows(IllegalArgumentException::class.java) {
                "".validateTopicFilter()
            }.message
        )

        assertEquals(
            "非法主题：主题不能为空或者空白字符",
            assertThrows(IllegalArgumentException::class.java) {
                " ".validateTopicFilter()
            }.message
        )

        assertEquals(
            "非法主题：主题不能以 '/' 开头",
            assertThrows(IllegalArgumentException::class.java) {
                "/ncs/9527".validateTopicFilter()
            }.message
        )

        assertEquals(
            "非法主题：主题不能以 '/' 结尾",
            assertThrows(IllegalArgumentException::class.java) {
                "ncs/9527/".validateTopicFilter()
            }.message
        )

        assertEquals(
            "'#' 只能在最后一层",
            assertThrows(IllegalArgumentException::class.java) {
                "ncs/#/9527".validateTopicFilter()
            }.message
        )

        assertEquals(
            "'#' 只能在最后一层",
            assertThrows(IllegalArgumentException::class.java) {
                "#/ncs/9527".validateTopicFilter()
            }.message
        )

        assertEquals(
            "非法使用 '#' 通配符：只能单独作为最后一层",
            assertThrows(IllegalArgumentException::class.java) {
                "ncs/a#b/9527".validateTopicFilter()
            }.message
        )

        assertEquals(
            "非法使用 '+' 通配符：只能单独作为一层",
            assertThrows(IllegalArgumentException::class.java) {
                "ncs/a+b/9527".validateTopicFilter()
            }.message
        )

        assertEquals(
            "非法使用 '+' 通配符：不能作为第一层",
            assertThrows(IllegalArgumentException::class.java) {
                "+/ncs/ab/9527".validateTopicFilter()
            }.message
        )

        assertEquals(
            "非法主题：主题层级不能为空或者空白字符",
            assertThrows(IllegalArgumentException::class.java) {
                "ncs//9527".validateTopicFilter()
            }.message
        )

        assertEquals(
            "非法主题：主题层级不能为空或者空白字符",
            assertThrows(IllegalArgumentException::class.java) {
                "ncs/ /9527".validateTopicFilter()
            }.message
        )
    }
}