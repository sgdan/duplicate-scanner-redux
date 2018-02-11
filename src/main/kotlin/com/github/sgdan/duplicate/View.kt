package com.github.sgdan.duplicate

import com.github.sgdan.duplicate.ActionType.*
import com.github.sgdan.webviewredux.createDoc
import io.vavr.kotlin.list
import kotlinx.html.*
import kotlinx.html.dom.create
import mu.KotlinLogging
import org.w3c.dom.Node
import java.lang.Runtime.getRuntime
import java.io.File as IOFile

private val log = KotlinLogging.logger {}

private val doc = createDoc()

private fun linkTo(resource: String) = DuplicateScanner::class.java.classLoader.getResource(resource)
        .toURI().toURL().toExternalForm()

fun State.view(): Node = when (currentHash) {
    null -> folders()
    else -> group()
}

fun State.group(): Node = doc.create.html {
    header()
    body {
        div("rowHolder") {
            div("row") {
                iconButton("left", CLEAR_GROUP.name)
                div { +"Back" }
                div("grow center") {
                    +"Identical files of size ${sizeToString(currentGroup.first().size)}"
                }
                div { +"Safe Mode" }
                val safeIcon = if (safeMode) "checked" else "unchecked"
                iconButton(safeIcon, TOGGLE_SAFE.name)
            }

            // files that have been hashed
            val n = currentGroup.size()
            currentGroup.forEachIndexed { i, file ->
                fileRow(this@group, file, position(i, n), remaining < 2)
            }
        }
    }
}

fun position(i: Int, n: Int) = when {
    n == 1 -> "only"
    i == 0 -> "first"
    i == n - 1 -> "last"
    else -> "middle"
}

fun DIV.fileRow(state: State, file: File, style: String, lock: Boolean) {
    div("row $style") {
        icon("file")
        div("grow pad") {
            val f = IOFile(file.path)
            div("trunc") { +f.name }
            div("path trunc") { +(f.parentFile?.canonicalPath ?: "-") }
        }
        icon("open")
        when {
            state.hashing.contains(file.path) -> icon("spinner")
            state.deleted.contains(file) -> icon("cross")
            state.safeMode && lock -> icon("lock")
            else -> iconButton("delete", DELETE.name, file.path)
        }
    }
}

fun State.folders() = doc.create.html {
    header()
    body {
        div("row") {
            iconButton("delete", CLEAR.name)
            div { +"Clear" }
            div("grow center") {
                +"Folders"
                /*
                button {
                    +"debug"
                    onClick = "performAction('DEBUG')"
                }
                */
            }
            div { +"Open" }
            iconButton("open", OPEN.name)
        }
        br

        // show the folders that have been scanned
        if (folders.isEmpty) {
            div("row pad") {
                +"No folders selected, please click 'Open'!"
            }
        } else {
            div {
                folders.forEach { folder ->
                    div("row") {
                        icon("folder")
                        div("grow pad") {
                            val f = IOFile(folder)
                            div("trunc") { +f.name }
                            div("path trunc") { +(f.parentFile?.absolutePath ?: "-") }
                        }
                    }
                }
            }
            br
            val mem: Double = getRuntime().run { totalMemory() - freeMemory() } / 1073741824.0
            div("path") {
                +list("${paths.size()} files",
                        "${pathToFile.size()} scanned",
                        "${groups.size()} groups",
                        "${sizeToString(minSize)} minimum",
                        "%.1fG ram".format(mem)
                ).joinToString(", ")
            }

            // show groups of identical files
            div("rowHolder") {
                groups.forEach { files ->
                    val first = files.first()!!
                    val n = files.size()
                    val remaining = files.removeAll(deleted).size()
                    div("row") {
                        icon("files")
                        div("grow pad") {
                            div { +sizeToString(first.size) }
                            div("path") {
                                +"$n files"
                                if (remaining < n) +", $remaining remaining"
                            }
                        }
                        iconButton("right", SELECT_GROUP.name, first.md5)
                    }
                }
            }
        }
    }
}

fun sizeToString(size: Long) = when {
    size > 1000000000 -> "%.1f GB".format(size / 1000000000.0)
    size > 1000000 -> "%.1f MB".format(size / 1000000.0)
    size > 1000 -> "%.1f KB".format(size / 1000.0)
    else -> "%d B".format(size)
}

fun DIV.icon(name: String) {
    div("pad fixed") {
        div("${name}Icon icon")
    }
}

fun DIV.iconButton(name: String, vararg action: String) {
    div("pad fixed") {
        button(classes = "${name}Icon icon") {
            val actions = action.joinToString("','", "'", "'") { escape(it) }
            onClick = "performAction($actions)"
        }
    }
}

fun HTML.header() {
    head {
        link {
            rel = "stylesheet"
            href = linkTo("style.css")
        }
        //script { src = "${linkTo("firebug-lite.js")}#startOpened" }
    }
}

fun escape(value: String) = value.replace("'", "\\'")
        .replace("\"", "\\\"")
        .replace("\\", "\\\\")