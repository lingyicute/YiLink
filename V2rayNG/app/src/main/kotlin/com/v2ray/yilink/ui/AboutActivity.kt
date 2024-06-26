package com.v2ray.yilink.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.tbruyelle.rxpermissions.RxPermissions
import com.tencent.mmkv.MMKV
import com.v2ray.yilink.AppConfig
import com.v2ray.yilink.BuildConfig
import com.v2ray.yilink.R
import com.v2ray.yilink.databinding.ActivityAboutBinding
import com.v2ray.yilink.extension.toast
import com.v2ray.yilink.util.SpeedtestUtil
import com.v2ray.yilink.util.Utils
import com.v2ray.yilink.util.ZipUtil
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class AboutActivity : BaseActivity() {
    private lateinit var binding: ActivityAboutBinding
    private val extDir by lazy { File(Utils.backupPath(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        title = getString(R.string.title_about)

        binding.tvBackupSummary.text = this.getString(R.string.summary_configuration_backup, extDir)
        binding.layoutBackup.setOnClickListener {
            backupMMKV()
        }

        binding.layoutRestore.setOnClickListener {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            RxPermissions(this)
                .request(permission)
                .subscribe {
                    if (it) {
                        try {
                            showFileChooser()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else
                        toast(R.string.toast_permission_denied)
                }
        }

        binding.layoutSoureCcode.setOnClickListener {
            Utils.openUri(this, AppConfig.YiLinkUrl)
        }

        binding.layoutFeedback.setOnClickListener {
            Utils.openUri(this, AppConfig.YiLinkIssues)
        }

        binding.layoutTgChannel.setOnClickListener {
            Utils.openUri(this, AppConfig.TgChannelUrl)
        }

        binding.layoutPrivacyPolicy.setOnClickListener {
            Utils.openUri(this, AppConfig.YiLinkPrivacyPolicy)
        }

        "v${BuildConfig.VERSION_NAME} (${SpeedtestUtil.getLibVersion()})".also {
            binding.tvVersion.text = it
        }
    }

    fun backupMMKV() {
        val dateFormated = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val folderName = "${getString(R.string.app_name)}_${dateFormated}"
        val backupDir = this.cacheDir.absolutePath + "/$folderName"
        val outputZipFilePath = extDir.absolutePath + "/$folderName.zip"

        val count = MMKV.backupAllToDirectory(backupDir)
        if (count <= 0) {
            toast(R.string.toast_failure)
        }

        if (ZipUtil.zipFromFolder(backupDir, outputZipFilePath)) {
            toast(R.string.toast_success)
        } else {
            toast(R.string.toast_failure)
        }
    }

    fun restoreMMKV(zipFile: File) {
        val backupDir = this.cacheDir.absolutePath + "/${System.currentTimeMillis()}"

        if (!ZipUtil.unzipToFolder(zipFile, backupDir)) {
            toast(R.string.toast_failure)
        }

        val count = MMKV.restoreAllFromDirectory(backupDir)
        if (count > 0) {
            toast(R.string.toast_success)
        } else {
            toast(R.string.toast_failure)
        }
    }

    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            chooseFile.launch(Intent.createChooser(intent, getString(R.string.title_file_chooser)))
        } catch (ex: android.content.ActivityNotFoundException) {
            toast(R.string.toast_require_file_manager)
        }
    }

    private val chooseFile =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val uri = it.data?.data
            if (it.resultCode == RESULT_OK && uri != null) {
                try {
                    try {
                        val targetFile =
                            File(this.cacheDir.absolutePath, "${System.currentTimeMillis()}.zip")
                        contentResolver.openInputStream(uri).use { input ->
                            targetFile.outputStream().use { fileOut ->
                                input?.copyTo(fileOut)
                            }
                        }

                        restoreMMKV(targetFile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    toast(e.message.toString())
                }
            }
        }
}