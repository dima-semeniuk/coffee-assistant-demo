package org.example.telegram;

import lombok.RequiredArgsConstructor;
import org.example.service.CoffeeAssistant;
import org.example.service.CoffeeAssistantFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class CoffeeTelegramBot extends TelegramLongPollingBot {
    private static final Logger log
            = LoggerFactory.getLogger(CoffeeTelegramBot.class);
    private final Map<Long, CoffeeAssistant> userServices = new ConcurrentHashMap<>();
    private final CoffeeAssistantFactory assistantFactory;

    @Value("${bot.name}")
    private String botName;

    @Value("${bot.token}")
    private String botToken;

    @Override
    public String getBotUsername() {
        return botName;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();
            CoffeeAssistant service = getOrCreateAssistant(chatId); // Отримуємо або створюємо асистент для цього чату

            switch (messageText) {
                case "/start":
                    sendCoffeeAssistantKeyboard(chatId);
                    break;
                case "Coffee Assistant":
                    sendMessageWithButtons(chatId);
                    break;
                case "Start conversation":
//                    service.startConversation(chatId, this);
                    break;
                case "Exit":
                    sendCoffeeAssistantKeyboard(chatId);
                    break;
                default:
//                    service.processUserMessage(chatId, messageText, this);
                    break;
            }
        }
    }

    public void sendMessage(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(text);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error occurred: " + e.getMessage());
        }
    }

    private void sendMessageWithButtons(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Hello! Choose one option:");

        // Створення кнопок
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Start conversation"));

        KeyboardRow row2 = new KeyboardRow();
        row1.add(new KeyboardButton("Exit"));

        keyboardMarkup.setKeyboard(List.of(row1, row2));
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message); // Відправка повідомлення з кнопками
        } catch (TelegramApiException e) {
            log.error("Error occurred: " + e.getMessage());
        }
    }

    private void sendCoffeeAssistantKeyboard(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Press 'Coffee Assistant' to choose the best coffee for you ☕");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        row.add(new KeyboardButton("Coffee Assistant")); // Кнопка "Старт"

        keyboardMarkup.setKeyboard(List.of(row));
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending 'Coffee Assistant' keyboard: {}", e.getMessage());
        }
    }

    private CoffeeAssistant getOrCreateAssistant(Long chatId) {
        return userServices.computeIfAbsent(chatId, id -> assistantFactory.createCoffeeAssistant());
    }
}
