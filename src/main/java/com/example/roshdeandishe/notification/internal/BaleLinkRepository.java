package com.example.roshdeandishe.notification.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface BaleLinkRepository extends JpaRepository<BaleLinkEntity, Long> {

    Optional<BaleLinkEntity> findByBotIdIsNullAndChatId(Long chatId);

    Optional<BaleLinkEntity> findByBotIdIsNullAndUserId(Long userId);

    Optional<BaleLinkEntity> findByBotIdAndChatId(Long botId, Long chatId);

    Optional<BaleLinkEntity> findByBotIdAndUserId(Long botId, Long userId);

    long countByBotId(Long botId);

    void deleteByBotId(Long botId);

    /** پیوندِ این chat در رباتِ داده‌شده (مشترک یا مدرسه). */
    default Optional<BaleLinkEntity> findByChat(BotRef bot, Long chatId) {
        return bot.isShared() ? findByBotIdIsNullAndChatId(chatId) : findByBotIdAndChatId(bot.botId(), chatId);
    }

    /** پیوندِ این کاربر در رباتِ داده‌شده (مشترک یا مدرسه). */
    default Optional<BaleLinkEntity> findByUser(BotRef bot, Long userId) {
        return bot.isShared() ? findByBotIdIsNullAndUserId(userId) : findByBotIdAndUserId(bot.botId(), userId);
    }
}
