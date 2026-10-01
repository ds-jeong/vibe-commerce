package com.example.ecommerce.global;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling // ✨ 스프링 부트의 백그라운드 자동 스케줄러 통제 엔진 활성화
public class SchedulerConfig {
}
