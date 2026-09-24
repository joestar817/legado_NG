package io.legado.app.help.glide

import java.io.File

/** 同路径覆盖后令 Glide 的内存和磁盘缓存一起失效，稳定文件仍可复用缓存。 */
internal fun localImageFileVersion(file: File): String = "${file.lastModified()}:${file.length()}"
