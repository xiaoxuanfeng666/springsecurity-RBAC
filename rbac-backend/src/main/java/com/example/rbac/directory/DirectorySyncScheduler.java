package com.example.rbac.directory;

import com.example.rbac.config.DirectorySyncProperties;
import com.example.rbac.service.DirectorySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通讯录定时同步（enabled=true 时才真正执行）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DirectorySyncScheduler {

    private final DirectorySyncProperties properties;
    private final DirectorySyncService syncService;

    @Scheduled(cron = "${idp.directory-sync.cron:0 0 3 * * ?}")
    public void scheduledSync() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            syncService.sync();
        } catch (Exception e) {
            log.error("通讯录定时同步失败", e);
        }
    }
}
