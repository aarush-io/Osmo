package com.music.bitchord

import com.my.kizzy.gateway.GatewayFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscordGatewayFrameTest {

    private fun scan(json: String) = GatewayFrame.scan(json.toByteArray(Charsets.UTF_8))

    @Test
    fun `hello gives the heartbeat interval`() {
        val frame = scan("""{"t":null,"s":null,"op":10,"d":{"heartbeat_interval":41250,"_trace":["gw"]}}""")!!
        assertEquals(10, frame.op)
        assertNull(frame.s)
        assertNull(frame.t)
        assertEquals(41250L, frame.heartbeatInterval)
    }

    @Test
    fun `ready gives its own session, not one from the sessions list`() {
        val json = """
            {"t":"READY","s":1,"op":0,"d":{
              "v":9,
              "sessions":[{"session_id":"other","status":"online","activities":[]}],
              "guilds":[{"id":"1","name":"Caf\u00e9 \"quotes\" \\ {not a brace}","channels":[[],{}]}],
              "user":{"id":"2","username":"a"},
              "session_id":"mine",
              "resume_gateway_url":"wss://gateway-us-east1-b.discord.gg"
            }}
        """.trimIndent()
        val frame = scan(json)!!
        assertEquals(0, frame.op)
        assertEquals(1, frame.s)
        assertEquals("READY", frame.t)
        assertEquals("mine", frame.sessionId)
        assertEquals("wss://gateway-us-east1-b.discord.gg", frame.resumeGatewayUrl)
    }

    @Test
    fun `keys in any order, d before op`() {
        val frame = scan("""{"d":{"session_id":"x"},"op":0,"t":"READY","s":7}""")!!
        assertEquals(0, frame.op)
        assertEquals(7, frame.s)
        assertEquals("x", frame.sessionId)
    }

    @Test
    fun `non object d and heartbeat ack`() {
        assertEquals(9, scan("""{"t":null,"s":null,"op":9,"d":false}""")!!.op)
        assertEquals(11, scan("""{"op":11}""")!!.op)
    }

    @Test
    fun `escaped strings are decoded`() {
        val frame = scan("""{"op":0,"t":"A\"B\u0041","d":{"session_id":"a\\b\/c"}}""")!!
        assertEquals("A\"BA", frame.t)
        assertEquals("a\\b/c", frame.sessionId)
    }

    @Test
    fun `a big ready is read without building it`() {
        val guild = """{"id":"123","name":"server","channels":[{"id":"1","name":"general","topic":"words words words"}],"members":[{"user":{"id":"9"}}]}"""
        val json = buildString {
            append("""{"t":"READY","s":1,"op":0,"d":{"guilds":[""")
            repeat(50_000) { if (it > 0) append(','); append(guild) }
            append("""],"session_id":"big"}}""")
        }
        assertEquals("big", scan(json)!!.sessionId)
    }

    @Test
    fun `broken frames give null`() {
        assertNull(scan(""))
        assertNull(scan("[1,2]"))
        assertNull(scan("""{"op":10,"d":{"heartbeat_interval":"""))
    }
}
