package com.ericsantanna.baixaNotasChiquinho.appConfig

import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.context.annotation.Configuration

@Configuration
@EnableAutoConfiguration(exclude = [WebMvcAutoConfiguration.class])
class WebConfig {
    // This class purposely left empty
    // Its only job is to disable MVC auto-configuration
}
