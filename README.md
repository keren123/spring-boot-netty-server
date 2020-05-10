# 疯狂创客圈 spring-boot-starter-netty 
愿景：基于Netty和RxJava，实现全网第一个具备自我保护能力的、高性能WebServer。

## 简介
一个基于Netty(4.1.12.Final)实现的SpringBoot(或者SpringCloud）内置WebServer服务器。 
具体的依赖版本如下： 

| Spring Cloud        |    netty      |
| ------------------- | ------------- |
| 2.0.8.RELEASE       | 4.1.31.Final  |

## Maven依赖
1. 使用的时候，在SpringBoot(或者SpringCloud）项目中加入以下依赖：  
```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
			<exclusions>
				<!-- 排除自带的内置Tomcat -->
				<exclusion>
					<groupId>org.springframework.boot</groupId>
					<artifactId>spring-boot-starter-tomcat</artifactId>
				</exclusion>
			</exclusions>
		</dependency>
		<!-- 引入疯狂创客圈 netty server -->
		<dependency>
			<groupId>com.crazymaker</groupId>
			<artifactId>spring-boot-netty-server</artifactId>
			<version>1.0</version>
		</dependency>
```

2. 启动SpringBoot(或者SpringCloud）。

## Roadmap
1. v1.0  版本（finished）
完成了Servlet、静态资源的请求处理，可以作为具体基础的SpringCloud 内嵌式WebServer 能力.
能够支持swagger、或者其他Jar包内资源的请求处理。其中，有关静态资源的请求处理的代码，来自于tomcat.

2. v2.0  版本（donging）
使用RxJava，参考Hystrix,实现在HTTP请求数、错误数达到设定上限时，能够进行熔断保护、请求降级、选择性的放行，从而避免服务器的彻底崩溃。

3. v3.0  版本（规划中）
实现服务器的状态管理（green、yellow、red），并且在状态变化时，能够及时发出预警
