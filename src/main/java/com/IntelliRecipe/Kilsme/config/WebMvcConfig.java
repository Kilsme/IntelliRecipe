package com.IntelliRecipe.Kilsme.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private AuthRateLimitInterceptor authRateLimitInterceptor;

    @Value("${image.storage-dir:${user.dir}/data/recipe-images}")
    private String imageStorageDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authRateLimitInterceptor)
                .addPathPatterns("/api/auth/login", "/api/auth/register", "/api/auth/session", "/api/auth/logout")
                .order(0);

        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/error",
                        "/favicon.ico",
                        "/",
                        "/login.html",
                        "/**/*.css",
                        "/**/*.js",
                        "/**/*.png",
                        "/**/*.jpg",
                        "/**/*.jpeg",
                        "/**/*.svg",
                        "/**/*.ico"
                )
                .order(1);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path imageDir = Paths.get(imageStorageDir).toAbsolutePath().normalize();
        String location = imageDir.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/recipe-images/**")
                .addResourceLocations(location, "classpath:/static/recipe-images/");
    }
}
