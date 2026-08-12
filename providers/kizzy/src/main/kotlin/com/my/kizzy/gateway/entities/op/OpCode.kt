// opcode kt
// the file functioned as op code

package com.my.kizzy.gateway.entities.op

import kotlinx.serialization.Serializable

@Serializable(OpCodeSerializer::class)
enum class OpCode(val value: Int) {
    // an event was dispatched
    DISPATCH(0),

    // fired periodically by the client to keep the connection alive
    HEARTBEAT(1),

    // starts a new session during the initial handshake
    IDENTIFY(2),

    // update the client s presence
    PRESENCE_UPDATE(3),

    // joins leaves or moves between voice channels
    VOICE_STATE(4),

    // resume a previous session that was disconnected
    RESUME(6),

    // you should attempt to reconnect and resume immediately
    RECONNECT(7),

    // request information about offline guild members in a large guild
    REQUEST_GUILD_MEMBERS(8),

    // the session has been invalidated you should reconnect and identify resume accordingly
    INVALID_SESSION(9),

    // sent immediately after connecting contains the heartbeat_interval to use
    HELLO(10),

    // sent in response to receiving a heartbeat to acknowledge that it has been received
    HEARTBEAT_ACK(11),

    // for future use or unknown opcodes
    UNKNOWN(-1);
}