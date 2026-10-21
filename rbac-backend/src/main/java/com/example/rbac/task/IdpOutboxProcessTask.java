package com.example.rbac.task;

import com.example.rbac.outbound.IdpOutboxProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 外部通讯录 Outbox 的定时投递
 * <p>
 * 只负责调度，具体发送与失败退避在 {@link IdpOutboxProcessor}。
 * 定时任务集中放在 {@code task} 包，投递逻辑本身不感知调度。
 */
@Component
@RequiredArgsConstructor
public class IdpOutboxProcessTask {

    private final IdpOutboxProcessor idpOutboxProcessor;

    @Scheduled(fixedDelayString = "${idp.outbound.process-interval-ms:5000}")
    public void process() {
        idpOutboxProcessor.processPending();
    }
}
