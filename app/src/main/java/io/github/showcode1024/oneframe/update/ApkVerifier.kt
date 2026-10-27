package io.github.showcode1024.oneframe.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest

object ApkVerifier {
    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): Set<String> {
        val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return signers?.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }?.toSet() ?: emptySet()
    }

    @Suppress("DEPRECATION")
    fun verify(context: Context, file: File, release: UpdateRelease) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = pm.getPackageInfo(context.packageName, flags)
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: throw IOException("下载文件不是有效的 Android 安装包")
        val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        val archiveCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        ApkIdentityPolicy.verify(context.packageName, installedCode, signatures(installed), release.version,
            archive.packageName, archive.versionName, archiveCode, signatures(archive))
    }
}
