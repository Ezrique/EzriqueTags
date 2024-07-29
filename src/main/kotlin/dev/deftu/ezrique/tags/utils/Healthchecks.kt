package dev.deftu.ezrique.tags.utils

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

object Healthchecks {

    private var server: NettyApplicationEngine? = null

    fun start() {
        if (server != null) {
            close()
        }

        server = embeddedServer(Netty, port = 6139) {
            routing {
                get("/health") {
                    call.respondText("OK")
                }
            }
        }.start(wait = false)
    }

    fun close() {
        server?.stop(1000, 1000)
        server = null
    }

}
