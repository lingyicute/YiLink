package com.v2ray.yilink.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.v2ray.yilink.YilinkApplication
import com.v2ray.yilink.AppConfig
import com.v2ray.yilink.AppConfig.YILINK_PACKAGE
import com.v2ray.yilink.R
import com.v2ray.yilink.dto.EConfigType
import com.v2ray.yilink.dto.ProfileItem
import com.v2ray.yilink.dto.ServerConfig
import com.v2ray.yilink.dto.ServersCache
import com.v2ray.yilink.dto.SubscriptionItem
import com.v2ray.yilink.dto.V2rayConfig
import com.v2ray.yilink.extension.toast
import com.v2ray.yilink.util.YilinkConfigManager
import com.v2ray.yilink.util.YilinkConfigManager.updateConfigViaSub
import com.v2ray.yilink.util.MessageUtil
import com.v2ray.yilink.util.MmkvManager
import com.v2ray.yilink.util.MmkvManager.KEY_YILINK_CONFIGS
import com.v2ray.yilink.util.MmkvManager.subStorage
import com.v2ray.yilink.util.SpeedtestUtil
import com.v2ray.yilink.util.Utils
import com.v2ray.yilink.util.V2rayConfigUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Collections

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private var serverList = MmkvManager.decodeServerList()
    var subscriptionId: String = MmkvManager.settingsStorage.decodeString(AppConfig.CACHE_SUBSCRIPTION_ID, "").orEmpty()

    //var keywordFilter: String = MmkvManager.settingsStorage.decodeString(AppConfig.CACHE_KEYWORD_FILTER, "")?:""
    var keywordFilter = ""
    val serversCache = mutableListOf<ServersCache>()
    val isRunning by lazy { MutableLiveData<Boolean>() }
    val updateListAction by lazy { MutableLiveData<Int>() }
    val updateTestResultAction by lazy { MutableLiveData<String>() }
    private val tcpingTestScope by lazy { CoroutineScope(Dispatchers.IO) }

    fun startListenBroadcast() {
        isRunning.value = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<YilinkApplication>().registerReceiver(
                mMsgReceiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
                Context.RECEIVER_EXPORTED
            )
        } else {
            getApplication<YilinkApplication>().registerReceiver(
                mMsgReceiver,
                IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY)
            )
        }
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    override fun onCleared() {
        getApplication<YilinkApplication>().unregisterReceiver(mMsgReceiver)
        tcpingTestScope.coroutineContext[Job]?.cancelChildren()
        SpeedtestUtil.closeAllTcpSockets()
        Log.i(YILINK_PACKAGE, "Main ViewModel is cleared")
        super.onCleared()
    }

    fun reloadServerList() {
        serverList = MmkvManager.decodeServerList()
        updateCache()
        updateListAction.value = -1
    }

    fun removeServer(guid: String) {
        serverList.remove(guid)
        MmkvManager.removeServer(guid)
        val index = getPosition(guid)
        if (index >= 0) {
            serversCache.removeAt(index)
        }
    }

    fun appendCustomConfigServer(server: String): Boolean {
        if (server.contains("inbounds")
            && server.contains("outbounds")
            && server.contains("routing")
        ) {
            try {
                val config = ServerConfig.create(EConfigType.CUSTOM)
                config.subscriptionId = subscriptionId
                config.fullConfig = Gson().fromJson(server, V2rayConfig::class.java)
                config.remarks = config.fullConfig?.remarks ?: System.currentTimeMillis().toString()
                val key = MmkvManager.encodeServerConfig("", config)
                MmkvManager.serverRawStorage?.encode(key, server)
                serverList.add(0, key)
                val profile = ProfileItem(
                    configType = config.configType,
                    subscriptionId = config.subscriptionId,
                    remarks = config.remarks,
                    server = config.getProxyOutbound()?.getServerAddress(),
                    serverPort = config.getProxyOutbound()?.getServerPort(),
                )
                serversCache.add(0, ServersCache(key, profile))
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    fun swapServer(fromPosition: Int, toPosition: Int) {
        Collections.swap(serverList, fromPosition, toPosition)
        Collections.swap(serversCache, fromPosition, toPosition)
        MmkvManager.mainStorage?.encode(KEY_YILINK_CONFIGS, Gson().toJson(serverList))
    }

    @Synchronized
    fun updateCache() {
        serversCache.clear()
        for (guid in serverList) {
            var profile = MmkvManager.decodeProfileConfig(guid)
            if (profile == null) {
                val config = MmkvManager.decodeServerConfig(guid) ?: continue
                profile = ProfileItem(
                    configType = config.configType,
                    subscriptionId = config.subscriptionId,
                    remarks = config.remarks,
                    server = config.getProxyOutbound()?.getServerAddress(),
                    serverPort = config.getProxyOutbound()?.getServerPort(),
                )
                MmkvManager.encodeServerConfig(guid, config)
            }

            if (subscriptionId.isNotEmpty() && subscriptionId != profile.subscriptionId) {
                continue
            }

            if (keywordFilter.isEmpty() || profile.remarks.contains(keywordFilter)) {
                serversCache.add(ServersCache(guid, profile))
            }
        }
    }

    fun updateConfigViaSubAll(): Int {
        if (subscriptionId.isNullOrEmpty()) {
            return YilinkConfigManager.updateConfigViaSubAll()
        } else {
            val json = subStorage?.decodeString(subscriptionId)
            if (!json.isNullOrBlank()) {
                return updateConfigViaSub(Pair(subscriptionId, Gson().fromJson(json, SubscriptionItem::class.java)))
            } else {
                return 0
            }
        }
    }

    fun exportAllServer(): Int {
        val serverListCopy =
            if (subscriptionId.isNullOrEmpty() && keywordFilter.isNullOrEmpty()) {
                serverList
            } else {
                serversCache.map { it.guid }.toList()
            }

        val ret = YilinkConfigManager.shareNonCustomConfigsToClipboard(
            getApplication<YilinkApplication>(),
            serverListCopy
        )
        return ret
    }


    fun testAllTcping() {
        tcpingTestScope.coroutineContext[Job]?.cancelChildren()
        SpeedtestUtil.closeAllTcpSockets()
        MmkvManager.clearAllTestDelayResults(serversCache.map { it.guid }.toList())
        updateListAction.value = -1 // update all

        getApplication<YilinkApplication>().toast(R.string.connection_test_testing)
        for (item in serversCache) {
            item.profile.let { outbound ->
                val serverAddress = outbound.server
                val serverPort = outbound.serverPort
                if (serverAddress != null && serverPort != null) {
                    tcpingTestScope.launch {
                        val testResult = SpeedtestUtil.tcping(serverAddress, serverPort)
                        launch(Dispatchers.Main) {
                            MmkvManager.encodeServerTestDelayMillis(item.guid, testResult)
                            updateListAction.value = getPosition(item.guid)
                        }
                    }
                }
            }
        }
    }

    fun testAllRealPing() {
        MessageUtil.sendMsg2TestService(getApplication(), AppConfig.MSG_MEASURE_CONFIG_CANCEL, "")
        MmkvManager.clearAllTestDelayResults(serversCache.map { it.guid }.toList())
        updateListAction.value = -1 // update all

        val serversCopy = serversCache.toList() // Create a copy of the list

        getApplication<YilinkApplication>().toast(R.string.connection_test_testing)
        viewModelScope.launch(Dispatchers.Default) { // without Dispatchers.Default viewModelScope will launch in main thread
            for (item in serversCopy) {
                val config = V2rayConfigUtil.getV2rayConfig(getApplication(), item.guid)
                if (config.status) {
                    MessageUtil.sendMsg2TestService(
                        getApplication(),
                        AppConfig.MSG_MEASURE_CONFIG,
                        Pair(item.guid, config.content)
                    )
                }
            }
        }
    }

    fun testCurrentServerRealPing() {
        MessageUtil.sendMsg2Service(getApplication(), AppConfig.MSG_MEASURE_DELAY, "")
    }

    fun subscriptionIdChanged(id: String) {
        if (subscriptionId != id) {
            subscriptionId = id
            MmkvManager.settingsStorage.encode(AppConfig.CACHE_SUBSCRIPTION_ID, subscriptionId)
            reloadServerList()
        }
    }

    fun getSubscriptions(context: Context): Pair<MutableList<String>?, MutableList<String>?> {
        val subscriptions = MmkvManager.decodeSubscriptions()
        if (subscriptionId.isNotEmpty()
            && !subscriptions.map { it.first }.contains(subscriptionId)
        ) {
            subscriptionIdChanged("")
        }
        if (subscriptions.isEmpty()) {
            return null to null
        }
        val listId = subscriptions.map { it.first }.toMutableList()
        listId.add(0, "")
        val listRemarks = subscriptions.map { it.second.remarks }.toMutableList()
        listRemarks.add(0, context.getString(R.string.filter_config_all))

        return listId to listRemarks
    }

    fun getPosition(guid: String): Int {
        serversCache.forEachIndexed { index, it ->
            if (it.guid == guid)
                return index
        }
        return -1
    }

    fun removeDuplicateServer(): Int {
        val serversCacheCopy = mutableListOf<Pair<String, ServerConfig>>()
        for (it in serversCache) {
            val config = MmkvManager.decodeServerConfig(it.guid) ?: continue
            serversCacheCopy.add(Pair(it.guid, config))
        }

        val deleteServer = mutableListOf<String>()
        serversCacheCopy.forEachIndexed { index, it ->
            val outbound = it.second.getProxyOutbound()
            serversCacheCopy.forEachIndexed { index2, it2 ->
                if (index2 > index) {
                    val outbound2 = it2.second.getProxyOutbound()
                    if (outbound == outbound2 && !deleteServer.contains(it2.first)) {
                        deleteServer.add(it2.first)
                    }
                }
            }
        }
        for (it in deleteServer) {
            MmkvManager.removeServer(it)
        }

        return deleteServer.count()
    }

    fun removeAllServer() {
        if (subscriptionId.isNullOrEmpty() && keywordFilter.isNullOrEmpty()) {
            MmkvManager.removeAllServer()
        } else {
            val serversCopy = serversCache.toList()
            for (item in serversCopy) {
                MmkvManager.removeServer(item.guid)
            }
        }
    }

    fun removeInvalidServer() {
        if (subscriptionId.isNullOrEmpty() && keywordFilter.isNullOrEmpty()) {
            MmkvManager.removeInvalidServer("")
        } else {
            val serversCopy = serversCache.toList()
            for (item in serversCopy) {
                MmkvManager.removeInvalidServer(item.guid)
            }
        }
    }

    fun sortByTestResults() {
        MmkvManager.sortByTestResults()
    }


    fun copyAssets(assets: AssetManager) {
        val extFolder = Utils.userAssetPath(getApplication<YilinkApplication>())
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val geo = arrayOf("geosite.dat", "geoip.dat")
                assets.list("")
                    ?.filter { geo.contains(it) }
                    ?.filter { !File(extFolder, it).exists() }
                    ?.forEach {
                        val target = File(extFolder, it)
                        assets.open(it).use { input ->
                            FileOutputStream(target).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.i(
                            YILINK_PACKAGE,
                            "Copied from apk assets folder to ${target.absolutePath}"
                        )
                    }
            } catch (e: Exception) {
                Log.e(YILINK_PACKAGE, "asset copy failed", e)
            }
        }
    }

    fun filterConfig(keyword: String) {
        if (keyword == keywordFilter) {
            return
        }
        keywordFilter = keyword
        MmkvManager.settingsStorage.encode(AppConfig.CACHE_KEYWORD_FILTER, keywordFilter)
        reloadServerList()
    }

    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    isRunning.value = true
                }

                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    isRunning.value = false
                }

                AppConfig.MSG_STATE_START_SUCCESS -> {
                    getApplication<YilinkApplication>().toast(R.string.toast_services_success)
                    isRunning.value = true
                }

                AppConfig.MSG_STATE_START_FAILURE -> {
                    getApplication<YilinkApplication>().toast(R.string.toast_services_failure)
                    isRunning.value = false
                }

                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    isRunning.value = false
                }

                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                    updateTestResultAction.value = intent.getStringExtra("content")
                }

                AppConfig.MSG_MEASURE_CONFIG_SUCCESS -> {
                    val resultPair: Pair<String, Long> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getSerializableExtra("content", Pair::class.java) as Pair<String, Long>
                    } else {
                        intent.getSerializableExtra("content") as Pair<String, Long>
                    }
                    MmkvManager.encodeServerTestDelayMillis(resultPair.first, resultPair.second)
                    updateListAction.value = getPosition(resultPair.first)
                }
            }
        }
    }
}