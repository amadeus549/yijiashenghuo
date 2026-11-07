package com.yijia.config;

import com.yijia.service.IProviderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 启动时把服务商坐标灌到 Redis GEO 里，否则"附近服务商"查不到数据。
 * 已经存在的类型不会重复写，所以重启不会覆盖运行期间新增的服务商。
 */
@Slf4j
@Component
public class ProviderGeoInitializer implements ApplicationRunner {

    @Resource
    private IProviderService providerService;

    @Override
    public void run(ApplicationArguments args) {
        providerService.loadProviderGeoData();
        log.info("服务商坐标初始化检查完成");
    }
}
