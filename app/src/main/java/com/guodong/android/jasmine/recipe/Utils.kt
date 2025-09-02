package com.guodong.android.jasmine.recipe

import android.content.Context
import java.io.File

/**
 * Created by guodongAndroid on 2025/8/4
 */
fun String.safeToInt(default: Int = -1): Int {
    return try {
        toInt()
    } catch (_: NumberFormatException) {
        default
    }
}

fun File.deleteDir(include: Boolean = true): Boolean {
    if (!exists()) {
        return true
    }
    if (!isDirectory) {
        return delete()
    }

    val children = list() ?: return false
    for (child in children) {
        File(this, child).deleteDir()
    }

    if (include) {
        return delete()
    }

    return true
}

fun Context.copyAssetSSL(): Boolean = try {
    val sslDirName = "ssl"

    val destDir = File(filesDir, sslDirName)
    if (destDir.exists()) {
        destDir.deleteDir()
    }

    if (!destDir.mkdirs()) {
        throw RuntimeException("Failed to create directory: ${destDir.absolutePath}")
    }

    val files = assets.list(sslDirName)
    if (files == null) {
        throw RuntimeException("Failed to list asset directory")
    }

    if (files.isEmpty()) {
        throw RuntimeException("Asset dir `$sslDirName` is empty")
    }

    var success = true
    for (file in files) {
        success = copyAssetFile("$sslDirName/$file", "$destDir/$file")
    }

    success
} catch (_: Exception) {
    false
}

fun Context.copyAssetFile(assetFilePath: String, destFilePath: String): Boolean = try {

    assets.open(assetFilePath).copyTo(File(destFilePath).outputStream())

    true
} catch (_: Exception) {
    false
}