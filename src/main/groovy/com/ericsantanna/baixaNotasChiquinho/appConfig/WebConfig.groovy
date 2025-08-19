package com.ericsantanna.baixaNotasChiquinho.appConfig

import org.springframework.boot.web.server.ErrorPage
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig implements WebMvcConfigurer {

    @Override
    void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html")
        registry.addViewController("/execucao/**").setViewName("forward:/index.html")
        registry.addViewController("/configuracoes/**").setViewName("forward:/index.html")
        registry.addViewController("/logs/**").setViewName("forward:/index.html")
    }

    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> containerCustomizer() {
        return { container ->
            // This is where we handle routes with a 404 status
            // It will catch any routes that aren't explicitly mapped by controllers or static resources
            // but will not interfere with API or WebSocket endpoints
            container.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/index.html"))
        }
    }

    @Override
    void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Make sure static resources are handled normally
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
    }

}
