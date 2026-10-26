package com.example.catering

import com.example.catering.database.DatabaseFactory
import com.example.catering.security.JwtService
import com.example.catering.security.configureAuthentication
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.Route
import kotlin.time.Duration.Companion.minutes

fun Application.rootModule() {

    install(XForwardedHeaders)

    install(RateLimit) {
        register(RateLimitName("login")) {
            rateLimiter(
                limit = 10,
                refillPeriod = 1.minutes
            )

            requestKey { call ->
                call.request.origin.remoteHost
            }
        }
        register(RateLimitName("register")) {
            rateLimiter(
                limit = 5,
                refillPeriod = 1.minutes
            )

            requestKey { call ->
                call.request.origin.remoteHost
            }
        }
    }
    DatabaseFactory.migrate()

    DatabaseFactory.init()
    DatabaseFactory.checkConnection()

    val jwtService = JwtService()

    configureSerialization()
    configureAuthentication(jwtService)
    configureRouting(jwtService)
}