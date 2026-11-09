package com.claudiocarige.backendcreditengine.config;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

@Configuration
public class OpenApiConfig {

    @Bean
    @ConditionalOnMissingBean
    public SpringDocConfiguration springDocConfiguration() {
        return new SpringDocConfiguration();
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringDocConfigProperties springDocConfigProperties() {
        return new SpringDocConfigProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapperProvider springDocObjectMapperProvider(SpringDocConfigProperties springDocConfigProperties) {
        return new ObjectMapperProvider(springDocConfigProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SwaggerIndexTransformer indexPageTransformer(
            SwaggerUiConfigProperties swaggerUiConfig,
            SwaggerUiOAuthProperties swaggerUiOAuthProperties,
            SwaggerWelcomeCommon swaggerWelcomeCommon,
            ObjectMapperProvider objectMapperProvider
    ) {
        return new SwaggerIndexPageTransformer(
                swaggerUiConfig,
                swaggerUiOAuthProperties,
                swaggerWelcomeCommon,
                objectMapperProvider
        ) {
            @Override
            public Resource transform(
                    HttpServletRequest request,
                    Resource resource,
                    ResourceTransformerChain transformerChain
            ) throws IOException {
                Resource transformed = super.transform(request, resource, transformerChain);
                if (resource.getFilename() != null
                        && resource.getFilename().equals("swagger-initializer.js")
                        && swaggerUiConfig.getUrl() != null) {
                    String content = new String(transformed.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    String updated = content
                            .replace("url: \"\"", "url: \"" + swaggerUiConfig.getUrl() + "\"")
                            .replaceAll("(?m)^\\s*\"configUrl\"\\s*:\\s*\"[^\"]*\",?\\R?", "");
                    return new TransformedResource(transformed, updated.getBytes(StandardCharsets.UTF_8));
                }
                return transformed;
            }
        };
    }
}
