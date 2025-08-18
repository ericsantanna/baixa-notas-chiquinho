package com.ericsantanna.baixaNotasChiquinho.appConfig

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
class SpringContextHolder implements ApplicationContextAware {
    
    private static ApplicationContext context
    
    @Override
    void setApplicationContext(ApplicationContext applicationContext) {
        context = applicationContext
    }
    
    static ApplicationContext getApplicationContext() {
        return context
    }
    
    static <T> T getBean(Class<T> beanClass) {
        return context?.getBean(beanClass)
    }
    
    static <T> T getBean(String name, Class<T> beanClass) {
        return context?.getBean(name, beanClass)
    }
    
    static boolean isContextReady() {
        return context != null
    }
}