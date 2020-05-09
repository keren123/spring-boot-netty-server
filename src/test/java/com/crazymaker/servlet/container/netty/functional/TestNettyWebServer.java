package com.crazymaker.servlet.container.netty.functional;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.concurrent.Callable;

@Controller
@EnableAutoConfiguration(exclude = WebMvcAutoConfiguration.class)
@ComponentScan(basePackages = {"com.crazymaker.servlet.container.netty"})
@EnableWebMvc
public class TestNettyWebServer extends WebMvcConfigurationSupport
{
    private Logger log = LoggerFactory.getLogger(getClass());

    private static final String HELLO_WORLD = "Hello, World!";

    /**
     * 测试普通的字符串
     */
    @RequestMapping(value = "/string", produces = "text/plain; chartset=UTF-8")
    @ResponseBody
    public String string()
    {
        return HELLO_WORLD;
    }
    /**
     * 测试异步调用
     */
    @RequestMapping(value = "/async", produces = "text/plain")
    @ResponseBody
    public Callable<String> async()
    {
        return () -> HELLO_WORLD;
    }
    /**
     * 测试JSON请求
     */
    @RequestMapping(value = "/json")
    @ResponseBody
    public RestOut json(@RequestParam String msg)
    {
        return new RestOut(HELLO_WORLD + ". msg=" + msg);
    }
    /**
     * 测试HTTP协议：请求头  会话
     */
    @RequestMapping(value = "/echo")
    @ResponseBody
    public RestOut echo(@RequestParam String msg, HttpSession session, HttpServletRequest req)
    {
        log.info("=======HttpServletRequest info as follow:=======");
        log.info("req.getPathInfo() :{}", req.getPathInfo());
        log.info("req.getServletPath() :{}", req.getServletPath());
        log.info("req.getContextPath() :{}", req.getContextPath());
        log.info("req.getRequestURI() :{}", req.getRequestURI());
        log.info("req.getRequestURL() :{}", req.getRequestURL());
        log.info("req.getScheme() :{}", req.getScheme());
        log.info("req.getProtocol() :{}", req.getProtocol());
        log.info("req.getServerName() :{}", req.getServerName());
        log.info("=======HttpSession info as follow:=======");
        log.info("req.getRequestedSessionId() :{}", req.getRequestedSessionId());
        log.info("req.isRequestedSessionIdFromURL() :{}", req.isRequestedSessionIdFromURL());
        log.info("req.isRequestedSessionIdFromCookie() :{}", req.isRequestedSessionIdFromCookie());
        log.info("req.isRequestedSessionIdValid() :{}", req.isRequestedSessionIdValid());

        if (session.getAttribute("aaa") == null)
        {
            session.setAttribute("aaa", msg);
            log.info("sessionId={}, setAttribute aaa={}", session.getId(), msg);
        } else
        {
            String oldMsg = (String) session.getAttribute("aaa");
            log.info("sessionId={} is old Session, aaa={}, from Cookie:{}, from URL:{}, valid:{}", session.getId(), oldMsg, req.isRequestedSessionIdFromCookie(), req.isRequestedSessionIdFromURL(), req.isRequestedSessionIdValid());
        }
        return new RestOut(HELLO_WORLD + ". msg=" + msg);
    }



    /**
     * 封装响应内容的 POJO
     */
    @Data
    private static class RestOut
    {
        private final String msg;

        public RestOut(String msg)
        {
            this.msg = msg;
        }


    }


    public static void main(String[] args)
    {
        SpringApplication.run(TestNettyWebServer.class, args);
    }
}
