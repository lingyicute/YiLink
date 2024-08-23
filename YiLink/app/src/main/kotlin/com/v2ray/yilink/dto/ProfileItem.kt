package com.v2ray.yilink.dto

data class ProfileItem(
    val configType: EConfigType,
    var subscriptionId: String = "",
    var remarks: String = "",
    var server: String?,
    var serverPort: Int?,
)