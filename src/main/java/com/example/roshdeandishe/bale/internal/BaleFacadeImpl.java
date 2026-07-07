package com.example.roshdeandishe.bale.internal;

import com.example.roshdeandishe.bale.BaleFacade;
import org.springframework.stereotype.Service;

@Service
class BaleFacadeImpl implements BaleFacade {

    private final BaleLinkRepository baleLinkRepository;
    private final BaleClient baleClient;
    private final BaleWebhookService webhookService;

    BaleFacadeImpl(BaleLinkRepository baleLinkRepository, BaleClient baleClient, BaleWebhookService webhookService) {
        this.baleLinkRepository = baleLinkRepository;
        this.baleClient = baleClient;
        this.webhookService = webhookService;
    }

    @Override
    public boolean sendMessageToUser(Long userId, String text) {
        return baleLinkRepository.findByUserId(userId)
                .map(link -> baleClient.sendMessage(link.getChatId(), text))
                .orElse(false);
    }

    @Override
    public void processIncomingUpdate(String rawJson) {
        webhookService.processRawUpdate(rawJson);
    }
}
