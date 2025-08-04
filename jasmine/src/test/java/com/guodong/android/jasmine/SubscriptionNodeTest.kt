package com.guodong.android.jasmine

import com.guodong.android.jasmine.store.InMemorySubscriptionStore
import com.guodong.android.jasmine.store.subscription.Subscription
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Created by guodongAndroid on 2025/8/1
 */
internal class SubscriptionNodeTest {

    private val store = InMemorySubscriptionStore()

    private lateinit var callInfoStore: Subscription
    private lateinit var registerStore: Subscription
    private lateinit var registerPlusStore: Subscription
    private lateinit var deviceStore: Subscription
    private lateinit var deptHashStore: Subscription

    @Before
    fun init() {
        callInfoStore = Subscription("1", "ncs/9527/call_info", 0)
        registerStore = Subscription("1", "ncs/9527/register", 0)
        registerPlusStore = Subscription("2", "ncs/+/register", 0)
        deviceStore = Subscription("3", "ncs/9527/device", 0)
        deptHashStore = Subscription("4", "ncs/9527/#", 0)

        store.subscribe(callInfoStore)
        store.subscribe(registerStore)
        store.subscribe(registerPlusStore)
        store.subscribe(deviceStore)
        store.subscribe(deptHashStore)
    }

    @Test
    fun testMatch() {
        assertEquals(listOf(callInfoStore, deptHashStore), store.match("ncs/9527/call_info"))
        assertEquals(
            listOf(registerStore, deptHashStore, registerPlusStore),
            store.match("ncs/9527/register")
        )
        assertEquals(listOf(registerPlusStore), store.match("ncs/0000/register"))
        assertEquals(listOf(registerPlusStore), store.match("ncs/1231/register"))
        assertEquals(listOf(deviceStore, deptHashStore), store.match("ncs/9527/device"))
    }

    @Test
    fun testUnsubscribe() {
        store.unsubscribe(callInfoStore.clientId, callInfoStore.topic)
        store.unsubscribe(registerStore.clientId, registerStore.topic)

        assertEquals(listOf(deptHashStore), store.match("ncs/9527/call_info"))
        assertEquals(
            listOf(deptHashStore, registerPlusStore),
            store.match("ncs/9527/register")
        )
        assertEquals(listOf(registerPlusStore), store.match("ncs/0000/register"))
        assertEquals(listOf(registerPlusStore), store.match("ncs/1231/register"))
        assertEquals(listOf(deviceStore, deptHashStore), store.match("ncs/9527/device"))
    }

    @Test
    fun testUnsubscribeAll() {
        store.unsubscribe("1")

        val match = store.match("ncs/9527/call_info")

        assertEquals(true, match.any { it.clientId != "1" })
        assertEquals(listOf(deptHashStore), match)
        assertEquals(listOf(deptHashStore, registerPlusStore), store.match("ncs/9527/register"))
        assertEquals(listOf(registerPlusStore), store.match("ncs/0000/register"))
        assertEquals(listOf(registerPlusStore), store.match("ncs/1231/register"))
        assertEquals(listOf(deviceStore, deptHashStore), store.match("ncs/9527/device"))
    }
}