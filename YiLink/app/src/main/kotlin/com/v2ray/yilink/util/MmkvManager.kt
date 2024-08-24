package com.v2ray.yilink.util

import com.google.gson.Gson
import com.tencent.mmkv.MMKV
import com.v2ray.yilink.dto.AssetUrlItem
import com.v2ray.yilink.dto.ProfileItem
import com.v2ray.yilink.dto.ServerAffiliationInfo
import com.v2ray.yilink.dto.ServerConfig
import com.v2ray.yilink.dto.SubscriptionItem
import java.net.URI

object MmkvManager {
    const val ID_MAIN = "MAIN"
    const val ID_SERVER_CONFIG = "SERVER_CONFIG"
    const val ID_PROFILE_CONFIG = "PROFILE_CONFIG"
    const val ID_SERVER_RAW = "SERVER_RAW"
    const val ID_SERVER_AFF = "SERVER_AFF"
    const val ID_SUB = "SUB"
    const val ID_ASSET = "ASSET"
    const val ID_SETTING = "SETTING"
    const val KEY_SELECTED_SERVER = "SELECTED_SERVER"
    const val KEY_YILINK_CONFIGS = "YILINK_CONFIGS"

    val mainStorage by lazy { MMKV.mmkvWithID(ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    val settingsStorage by lazy { MMKV.mmkvWithID(ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private val serverStorage by lazy { MMKV.mmkvWithID(ID_SERVER_CONFIG, MMKV.MULTI_PROCESS_MODE) }
    private val profileStorage by lazy { MMKV.mmkvWithID(ID_PROFILE_CONFIG, MMKV.MULTI_PROCESS_MODE) }
    private val serverAffStorage by lazy { MMKV.mmkvWithID(ID_SERVER_AFF, MMKV.MULTI_PROCESS_MODE) }
    val subStorage by lazy { MMKV.mmkvWithID(ID_SUB, MMKV.MULTI_PROCESS_MODE) }
    private val assetStorage by lazy { MMKV.mmkvWithID(ID_ASSET, MMKV.MULTI_PROCESS_MODE) }
    val serverRawStorage by lazy { MMKV.mmkvWithID(ID_SERVER_RAW, MMKV.MULTI_PROCESS_MODE) }

    fun decodeServerList(): MutableList<String> {
        val json = mainStorage?.decodeString(KEY_YILINK_CONFIGS)
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            Gson().fromJson(json, Array<String>::class.java).toMutableList()
        }
    }

    fun decodeServerConfig(guid: String): ServerConfig? {
        if (guid.isBlank()) {
            return null
        }
        val json = serverStorage?.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return Gson().fromJson(json, ServerConfig::class.java)
    }

    fun decodeProfileConfig(guid: String): ProfileItem? {
        if (guid.isBlank()) {
            return null
        }
        val json = profileStorage?.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return Gson().fromJson(json, ProfileItem::class.java)
    }

    fun encodeServerConfig(guid: String, config: ServerConfig): String {
        val key = guid.ifBlank { Utils.getUuid() }
        serverStorage?.encode(key, Gson().toJson(config))
        val serverList = decodeServerList()
        if (!serverList.contains(key)) {
            serverList.add(0, key)
            mainStorage?.encode(KEY_YILINK_CONFIGS, Gson().toJson(serverList))
            if (mainStorage?.decodeString(KEY_SELECTED_SERVER).isNullOrBlank()) {
                mainStorage?.encode(KEY_SELECTED_SERVER, key)
            }
        }
        val profile = ProfileItem(
            configType = config.configType,
            subscriptionId = config.subscriptionId,
            remarks = config.remarks,
            server = config.getProxyOutbound()?.getServerAddress(),
            serverPort = config.getProxyOutbound()?.getServerPort(),
        )
        profileStorage?.encode(key, Gson().toJson(profile))
        return key
    }

    fun removeServer(guid: String) {
        if (guid.isBlank()) {
            return
        }
        if (mainStorage?.decodeString(KEY_SELECTED_SERVER) == guid) {
            mainStorage?.remove(KEY_SELECTED_SERVER)
        }
        val serverList = decodeServerList()
        serverList.remove(guid)
        mainStorage?.encode(KEY_YILINK_CONFIGS, Gson().toJson(serverList))
        serverStorage?.remove(guid)
        profileStorage?.remove(guid)
        serverAffStorage?.remove(guid)
    }

    fun removeServerViaSubid(subid: String) {
        if (subid.isBlank()) {
            return
        }
        serverStorage?.allKeys()?.forEach { key ->
            decodeServerConfig(key)?.let { config ->
                if (config.subscriptionId == subid) {
                    removeServer(key)
                }
            }
        }
    }

    fun decodeServerAffiliationInfo(guid: String): ServerAffiliationInfo? {
        if (guid.isBlank()) {
            return null
        }
        val json = serverAffStorage?.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return Gson().fromJson(json, ServerAffiliationInfo::class.java)
    }

    fun encodeServerTestDelayMillis(guid: String, testResult: Long) {
        if (guid.isBlank()) {
            return
        }
        val aff = decodeServerAffiliationInfo(guid) ?: ServerAffiliationInfo()
        aff.testDelayMillis = testResult
        serverAffStorage?.encode(guid, Gson().toJson(aff))
    }

    fun clearAllTestDelayResults(keys: List<String>?) {
        keys?.forEach { key ->
            decodeServerAffiliationInfo(key)?.let { aff ->
                aff.testDelayMillis = 0
                serverAffStorage?.encode(key, Gson().toJson(aff))
            }
        }
    }

    fun importUrlAsSubscription(url: String): Int {
        val subscriptions = decodeSubscriptions()
        subscriptions.forEach {
            if (it.second.url == url) {
                return 0
            }
        }
        val uri = URI(Utils.fixIllegalUrl(url))
        val subItem = SubscriptionItem()
        subItem.remarks = uri.fragment ?: "import sub"
        subItem.url = url
        subStorage?.encode(Utils.getUuid(), Gson().toJson(subItem))
        return 1
    }

    fun decodeSubscriptions(): List<Pair<String, SubscriptionItem>> {
        val subscriptions = mutableListOf<Pair<String, SubscriptionItem>>()
        subStorage?.allKeys()?.forEach { key ->
            val json = subStorage?.decodeString(key)
            if (!json.isNullOrBlank()) {
                subscriptions.add(Pair(key, Gson().fromJson(json, SubscriptionItem::class.java)))
            }
        }
        return subscriptions.sortedBy { (_, value) -> value.addedTime }
    }

    fun removeSubscription(subid: String) {
        subStorage?.remove(subid)
        removeServerViaSubid(subid)
    }

    fun decodeAssetUrls(): List<Pair<String, AssetUrlItem>> {
        val assetUrlItems = mutableListOf<Pair<String, AssetUrlItem>>()
        assetStorage?.allKeys()?.forEach { key ->
            val json = assetStorage?.decodeString(key)
            if (!json.isNullOrBlank()) {
                assetUrlItems.add(Pair(key, Gson().fromJson(json, AssetUrlItem::class.java)))
            }
        }
        return assetUrlItems.sortedBy { (_, value) -> value.addedTime }
    }

    fun removeAssetUrl(assetid: String) {
        assetStorage?.remove(assetid)
    }

    fun removeAllServer() {
        mainStorage?.clearAll()
        serverStorage?.clearAll()
        profileStorage?.clearAll()
        serverAffStorage?.clearAll()
    }

    fun removeInvalidServer(guid: String) {
        if (guid.isNotEmpty()) {
            decodeServerAffiliationInfo(guid)?.let { aff ->
                if (aff.testDelayMillis < 0L) {
                    removeServer(guid)
                }
            }
        } else {
            serverAffStorage?.allKeys()?.forEach { key ->
                decodeServerAffiliationInfo(key)?.let { aff ->
                    if (aff.testDelayMillis < 0L) {
                        removeServer(key)
                    }
                }
            }
        }
    }

    fun sortByTestResults() {
        data class ServerDelay(var guid: String, var testDelayMillis: Long)

        val serverDelays = mutableListOf<ServerDelay>()
        val serverList = decodeServerList()
        serverList.forEach { key ->
            val delay = decodeServerAffiliationInfo(key)?.testDelayMillis ?: 0L
            serverDelays.add(ServerDelay(key, if (delay <= 0L) 999999 else delay))
        }
        serverDelays.sortBy { it.testDelayMillis }

        serverDelays.forEach {
            serverList.remove(it.guid)
            serverList.add(it.guid)
        }

        mainStorage?.encode(KEY_YILINK_CONFIGS, Gson().toJson(serverList))
    }
}