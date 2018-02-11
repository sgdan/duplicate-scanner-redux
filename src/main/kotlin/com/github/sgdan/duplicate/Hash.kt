package com.github.sgdan.duplicate

import com.github.sgdan.duplicate.ActionType.ADD_HASH
import mu.KotlinLogging
import java.io.File
import java.io.FileInputStream
import java.math.BigInteger
import java.security.DigestInputStream
import java.security.MessageDigest

private val log = KotlinLogging.logger {}

/**
 * @return md5sum calculated on the contents of the specified file
 */
fun hash(path: String): String {
    val md = MessageDigest.getInstance("MD5")
    DigestInputStream(FileInputStream(path), md).use {
        val buf = ByteArray(1024)
        while (it.read(buf) != -1) {
        }
    }
    return BigInteger(1, md.digest()).toString(16)
}

fun hashFile(path: String) {
    val file = File(path)
    redux?.perform(ADD_HASH, path, file.length(), hash(path), file.parentFile?.path)
}