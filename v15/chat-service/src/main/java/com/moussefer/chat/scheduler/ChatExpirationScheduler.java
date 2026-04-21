package com.moussefer.chat.scheduler;

import com.moussefer.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatExpirationScheduler {

    private final ChatService chatService;

    @Scheduled(fixedDelay = 60000) // chaque minute
    @SchedulerLock(name = "chat_expiration_scheduler", lockAtLeastFor = "PT30S", lockAtMostFor = "PT2M")
    public void expireSessions() {
        chatService.deactivateExpiredSessions();
    }
}