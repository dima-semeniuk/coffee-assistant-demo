package org.example.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CoffeeAssistantFactory {
    private final ChatLanguageModel chatLanguageModel;
    private final FileReader fileReader;

    public CoffeeAssistant createCoffeeAssistant() {
        return new CoffeeAssistant(chatLanguageModel, fileReader);
    }
}
