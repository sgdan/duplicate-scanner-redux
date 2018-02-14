package com.github.sgdan.duplicate

import com.github.sgdan.duplicate.ActionType.FOLDER_CHOSEN
import com.github.sgdan.webviewredux.Redux
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.web.WebView
import javafx.stage.Stage
import kotlinx.coroutines.experimental.javafx.JavaFx
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import mu.KotlinLogging
import tornadofx.chooseDirectory
import java.awt.Toolkit.getDefaultToolkit
import java.io.File
import java.lang.System.getProperty
import java.lang.System.setProperty
import com.apple.eawt.Application as MacApp

private val log = KotlinLogging.logger {}

const val MAX_GROUPS = 50

var redux: Redux<State>? = null
var webview: WebView? = null
var primaryStage: Stage? = null

private fun urlTo(resource: String) = DuplicateScanner::class.java.classLoader.getResource(resource)
        .toURI().toURL()

private fun linkTo(resource: String) = urlTo(resource).toExternalForm()

fun chooseFolder(state: State) = runBlocking {
    launch(JavaFx) {
        val chosen = chooseDirectory(
                initialDirectory = File(state.dir),
                owner = primaryStage
        )
        if (chosen != null) redux?.perform(FOLDER_CHOSEN, chosen.absolutePath)
    }
}

class DuplicateScanner : Application() {
    override fun start(stage: Stage) {
        primaryStage = stage
        webview = WebView()
        stage.scene = Scene(webview, 500.0, 600.0)
        stage.icons.add(Image(linkTo("files-empty.png")))
        stage.title = "Duplicate File Scanner"
        stage.show()

        redux = Redux(
                webview!!,
                State(),
                State::view,
                ::update
        )
    }
}

fun main(vararg args: String) {
    // Mac specific settings
    val os = getProperty("os.name").toLowerCase()
    if (os.startsWith("mac os x")) {
        // main menu, equivalent of -Xdock:name="Duplicate Scanner"
        setProperty("apple.awt.application.name", "Duplicate Scanner")

        val icon = getDefaultToolkit().getImage(urlTo("files-empty.png"))
        MacApp.getApplication().dockIconImage = icon
    }

    // launch JavaFX app
    Application.launch(DuplicateScanner::class.java)
}