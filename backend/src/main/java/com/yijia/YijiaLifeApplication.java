package com.yijia;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.yijia.mapper")
@SpringBootApplication
public class YijiaLifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(YijiaLifeApplication.class, args);
    }

}
