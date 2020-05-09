package com.crazymaker.servlet.container.netty.bootstrap;

import io.netty.bootstrap.Bootstrap;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
/**
 * Servlet 容器工厂配置类：配置内置Netty容器Bean。
 * 最早是直接将EmbeddedNettyFactory加@Component注解，这样集成在任何环境中都会加载，可能引起端口冲突。
 * 所以通过这个配置类，配置在当前上下文缺少 EmbeddedServletContainerFactory接口实现类时（即缺少内置Servlet容器），
 * 加载EmbeddedNettyFactory
 * 这样SpringBoot项目在引入这个maven依赖，并且排除了内置tomcat依赖、且没引入其他servlet容器（如jetty）时，
 * 就可以通过工厂类加载并启动netty容器了。
 */
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@Configuration
@ConditionalOnWebApplication // 在Web环境下才会起作用
public class NettyWebServerAutoConfiguration
{
    // Netty的Bootstrap类必须在classloader中存在，才能启动Netty容器
    @Configuration
    @ConditionalOnClass({Bootstrap.class})
    @ConditionalOnMissingBean(value = ServletWebServerFactory.class,
            search = SearchStrategy.CURRENT)
    public static class NettyWebServerFactoryConfig
    {
        //上述条件注解成立的场景
        // 使用此NettyWebServer容器工厂
        @Bean
        public NettyWebServerFactory nettyWebServerFactory()
        {
            return new NettyWebServerFactory();
        }
    }
}
