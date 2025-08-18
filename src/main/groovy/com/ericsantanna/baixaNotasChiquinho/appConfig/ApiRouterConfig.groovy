package com.ericsantanna.baixaNotasChiquinho.appConfig

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

@Configuration
class ApiRouterConfig {
    
    @Bean
    RouterFunction<ServerResponse> apiRoutes() {
        return RouterFunctions.route()
            // Status endpoint with /api prefix
            .GET("/api/status", { request ->
                Mono.just([
                    status: "running",
                    timestamp: System.currentTimeMillis()
                ])
                .flatMap { status ->
                    ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(status)
                }
            })
            // Add more API endpoints as needed
            .GET("/api/configuracoes", { request ->
                // Handler for configurations
                // ...
            })
            .POST("/api/executar", { request ->
                // Handler for execution
                // ...
            })
            .build()
    }
}
