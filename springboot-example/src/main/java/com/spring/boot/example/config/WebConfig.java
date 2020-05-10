package com.spring.boot.example.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import java.io.IOException;
import java.util.List;

//@Configuration
public class WebConfig extends WebMvcConfigurationSupport
{

    @Bean
    public MappingJackson2HttpMessageConverter customJackson2HttpMessageConverter() {
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.getSerializerProvider().setNullValueSerializer(
                new JsonSerializer<Object>() {
                    @Override
                    public void serialize(Object value, JsonGenerator jgen,
                            SerializerProvider provider) throws IOException,
                            JsonProcessingException
                    {
                        jgen.writeString("");
                    }
                });
        // 进行HTML解码
        objectMapper.registerModule(new SimpleModule().addSerializer(
                String.class, new JsonSerializer<String>() {
                    @Override
                    public void serialize(String value, JsonGenerator jgen,
                            SerializerProvider provider) throws IOException,
                            JsonProcessingException {
                        jgen.writeString(StringEscapeUtils.unescapeHtml4(value));
                    }
                }));
        jsonConverter.setObjectMapper(objectMapper);
        return jsonConverter;
    }

    @Override
    public void configureMessageConverters(
            List<HttpMessageConverter<?>> converters) {
        converters.add(customJackson2HttpMessageConverter());
        super.addDefaultHttpMessageConverters(converters);
    }

    /**
     * 发现如果继承了WebMvcConfigurationSupport，则在yml中配置的相关内容会失效。 需要重新指定静态资源
     * 
     * @param registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations(
                "classpath:/static/");
        registry.addResourceHandler("swagger-ui.html").addResourceLocations(
                "classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations(
                "classpath:/META-INF/resources/webjars/");
        super.addResourceHandlers(registry);
    }


    /**
     * 配置servlet处理
     */
    @Override
    public void configureDefaultServletHandling(
            DefaultServletHandlerConfigurer configurer) {
        configurer.enable();
    }

}