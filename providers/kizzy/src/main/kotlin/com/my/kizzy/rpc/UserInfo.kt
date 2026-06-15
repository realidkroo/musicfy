// UserInfo.kt
// this thing is part of user info

package com.my.kizzy.rpc

/**
 * Created by Zion Huang
 * Modified by musicfy contributors
 */
data class UserInfo(
    val id: String,
    val username: String,
    val name: String,
    val avatar: String?,
)
