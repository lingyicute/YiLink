package com.v2ray.yilink.dto

import com.v2ray.yilink.AppConfig


enum class EConfigType(val value: Int, val protocolScheme: String) {
    VMESS(1, AppConfig.VMESS),
    CUSTOM(2, AppConfig.CUSTOM),
    SHADOWSOCKS(3, AppConfig.SHADOWSOCKS),
    SOCKS(4, AppConfig.SOCKS),
    VLESS(5, AppConfig.VLESS),
    TROJAN(6, AppConfig.TROJAN),
    WIREGUARD(7, AppConfig.WIREGUARD);

    companion object {
        fun fromInt(value: Int) = values().firstOrNull { it.value == value }
    }
}
