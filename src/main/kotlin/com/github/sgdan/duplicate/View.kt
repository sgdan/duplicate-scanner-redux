package com.github.sgdan.duplicate

import com.github.sgdan.duplicate.ActionType.*
import com.github.sgdan.webviewredux.createDoc
import io.vavr.kotlin.list
import kotlinx.html.*
import kotlinx.html.dom.create
import mu.KotlinLogging
import org.w3c.dom.Node
import java.io.File
import java.lang.Runtime.getRuntime

private val log = KotlinLogging.logger {}

private val doc = createDoc()

private fun linkTo(resource: String) = DuplicateScanner::class.java.classLoader.getResource(resource)
        .toURI().toURL().toExternalForm()

fun view(state: State): Node = when {
    state.selectedSize == null -> folders(state)
    else -> bySize(state)
}

fun bySize(state: State) = doc.create.html {
    header()
    body {
        div("rowHolder") {
            div("row") {
                iconButton("left", SELECT_SIZE.name, "-") // select null size to go back
                div { +"Back" }
                div("grow center") {
                    +"Files of size ${sizeToString(state.selectedSize!!.toLong())}"
                }
                div { +"Safe Mode" }
                val safeIcon = if (state.safeMode) "checked" else "unchecked"
                iconButton(safeIcon, TOGGLE_SAFE.name)
            }

            // files that have been hashed
            state.selectedHashes().map { state.hashToPaths.apply(it) }.forEach { paths ->
                val n = paths.size()
                val remaining = paths.removeAll(state.deleted).size()
                paths.forEachIndexed { i, path ->
                    fileRow(state, path, position(i, n), remaining < 2)
                }
            }

            // also display files still being hashed
            state.hashing.filter { state.pathToSize.apply(it) == state.selectedSize }.forEach { path ->
                fileRow(state, path, "only", true)
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

fun DIV.fileRow(state: State, path: String, style: String, lock: Boolean) {
    div("row $style") {
        icon("file")
        div("grow pad") {
            val f = File(path)
            div("trunc") { +f.name }
            div("path trunc") { +(f.parentFile?.absolutePath ?: "-") }
        }
        when {
            state.hashing.contains(path) -> icon("spinner")
            state.deleted.contains(path) -> icon("cross")
            state.safeMode && lock -> icon("lock")
            else -> iconButton("delete", DELETE.name, path)
        }
    }
}

fun folders(state: State) = doc.create.html {
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
        if (state.folders.isEmpty()) {
            div("row pad") {
                +"No folders selected, please click 'Open'!"
            }
        } else {
            div {
                state.folders.forEach { folder ->
                    div("row") {
                        icon("folder")
                        div("grow pad") {
                            val f = File(folder)
                            div("trunc") { +f.name }
                            div("path trunc") { +(f.parentFile?.absolutePath ?: "-") }
                        }
                    }
                }
            }
            br
            val mem = getRuntime().run { totalMemory() - freeMemory() }.div(1048576)
            div("path") { +"Checked ${state.pathToSize.size()} files. Using ${mem}M memory." }

            // show groups of 2 or more files of the same size
            div("rowHolder") {
                state.sizeToPaths.toStream().filter { it._2.size() > 1 }.take(100).forEach {
                    val size = it._1
                    val n = it._2.size()
                    div("row") {
                        icon("files")
                        div("grow pad") {
                            div { +sizeToString(size) }
                            div("path") {
                                +"$n files"
                            }
                        }
                        iconButton("right", SELECT_SIZE.name, size.toString())
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
        button(classes = "${name}Button icon") {
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

fun escape(value: String) = value.replace("'", "\\'").replace("\"", "\\\"").replace("\\", "\\\\")