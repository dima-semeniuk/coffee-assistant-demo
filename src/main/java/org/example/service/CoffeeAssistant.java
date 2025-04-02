package org.example.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.example.telegram.CoffeeTelegramBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class CoffeeAssistant {
    private static final Logger logger = LoggerFactory.getLogger(CoffeeAssistant.class);

    private static final String RDF_FILE_PATH = "src/main/resources/Kulturra_Coffee.rdf";
    private static final String PROMPT_RDF = "src/main/resources/prompts/prompt-for-rds-search.txt";
    private static final String PROMPT_RESPONSE_EXAMPLE = "src/main/resources/prompts/prompt-response-example.txt";
    private static final String PROMPT_MORE_PARAM = "src/main/resources/prompts/prompt-for-question-for-more-param.txt";
    private static final String PROMPT_CHOOSE_ONE = "src/main/resources/prompts/prompt-for-question-to-choose-one-coffee.txt";

    private final ChatLanguageModel chatLanguageModel;
    private final FileReader fileReader;
    private ConversationState state = ConversationState.INITIAL;
    private final List<String> memoryAnswers = new ArrayList<>();
    private List<String> currentCoffees = new ArrayList<>();
    private List<String> questionsForUser = new ArrayList<>();


    public void startConversation(Long chatId, CoffeeTelegramBot bot) {
        bot.sendMessage(chatId, "Hello! I am your coffee assistant. What kind of coffee are you looking for?");
        state = ConversationState.WAITING_FOR_FIRST_QUERY;
    }

    public void processUserMessage(Long chatId, String messageText, CoffeeTelegramBot bot) {
        switch (state) {
            case INITIAL -> startConversation(chatId, bot);
            case WAITING_FOR_FIRST_QUERY -> handleFirstQuery(chatId, messageText, bot);
            case WAITING_FOR_QNA_ANSWER -> handleQnaAnswer(chatId, messageText, bot);
            case CHOOSING_BETWEEN_COFFEES -> handleChoosingCoffee(chatId, messageText, bot);
            case ASKING_MORE_PARAMETERS -> handleMoreParameters(chatId, messageText, bot);
            default -> bot.sendMessage(chatId, "I don't understand. Let's start again. What coffee are you looking for?");
        }
    }

    private void handleFirstQuery(Long chatId, String messageText, CoffeeTelegramBot bot) {
        String response = getResponseFromOntology(RDF_FILE_PATH, PROMPT_RDF, messageText);

        if (response.contains("answer")) {
            handleQna(chatId, bot, response);
            logger.info(response);
            return;
        }

        memoryAnswers.add(response);
        currentCoffees = JsonUtil.parseCoffeeList(response, "response");

        if (currentCoffees.isEmpty()) {
            bot.sendMessage(chatId, "No coffee found. Please specify more general parameters like 'taste', 'acidity', 'roasting method', 'processing type', etc.");
            return;
        }

        handleCoffeeOptions(chatId, bot);
        logger.info(response);
    }

    private void handleCoffeeOptions(Long chatId, CoffeeTelegramBot bot) {
        if (currentCoffees.size() == 1) {
            bot.sendMessage(chatId, "Your coffee ☕: " + currentCoffees.get(0));
            resetState();
        } else if (currentCoffees.size() == 2) {
            bot.sendMessage(chatId, "I found two options. Please choose one: ");
            bot.sendMessage(chatId, currentCoffees.get(0) + System.lineSeparator() + currentCoffees.get(1));
            String questionForUser = makeQuestion(PROMPT_CHOOSE_ONE);
            questionsForUser.add(questionForUser);
            bot.sendMessage(chatId, questionForUser);
            state = ConversationState.CHOOSING_BETWEEN_COFFEES;
        } else {
            bot.sendMessage(chatId, "I found multiple options. Could you specify more preferences?");
            String questionForUser = makeQuestion(PROMPT_MORE_PARAM);
            questionsForUser.add(questionForUser);
            bot.sendMessage(chatId, questionForUser);
            state = ConversationState.ASKING_MORE_PARAMETERS;
        }
    }

    private void handleMoreParameters(Long chatId, String messageText, CoffeeTelegramBot bot) {
        String response = refineSelection(PROMPT_RESPONSE_EXAMPLE, messageText);

        if (response.contains("answer")) {
            handleQna(chatId, bot, response);
            logger.info(response);
            return;
        }

        memoryAnswers.add(response);

        currentCoffees = JsonUtil.parseCoffeeList(response, "response");

        if (currentCoffees.isEmpty()) {
            bot.sendMessage(chatId, "No coffee found. Try specifying different parameters.");
            return;
        }

        handleCoffeeOptions(chatId, bot);
        logger.info(response);
    }

    private void handleChoosingCoffee(Long chatId, String messageText, CoffeeTelegramBot bot) {
        String response = refineSelection(PROMPT_RESPONSE_EXAMPLE, messageText);

        if (response.contains("answer")) {
            handleQna(chatId, bot, response);
            logger.info(response);
            return;
        }

        memoryAnswers.add(response);

        currentCoffees = JsonUtil.parseCoffeeList(response, "response");

        if (currentCoffees.isEmpty()) {
            bot.sendMessage(chatId, "No coffee found. Try specifying different parameters.");
            return;
        }

        handleCoffeeOptions(chatId, bot);
        logger.info(response);
    }

    private void handleQnaAnswer(Long chatId, String messageText, CoffeeTelegramBot bot) {
        String response;
        if (currentCoffees.isEmpty()) {
            response = getResponseFromOntology(RDF_FILE_PATH, PROMPT_RDF, messageText);
        } else {
            response = refineSelection(PROMPT_RESPONSE_EXAMPLE, messageText);
        }

        if (response.contains("answer")) {
            handleQna(chatId, bot, response);
            logger.info(response);
            return;
        }

        memoryAnswers.add(response);

        currentCoffees = JsonUtil.parseCoffeeList(response, "response");

        if (currentCoffees.isEmpty()) {
            bot.sendMessage(chatId, "No coffee found. Try specifying different parameters.");
            return;
        }

        handleCoffeeOptions(chatId, bot);
        logger.info(response);

    }

    private void handleQna(Long chatId, CoffeeTelegramBot bot, String response) {
        List<String> qnaList = JsonUtil.parseCoffeeList(response, "answer");
        if (!qnaList.isEmpty()) {
            bot.sendMessage(chatId, qnaList.get(0));
            bot.sendMessage(chatId, "So, let's return to coffee! What coffee do you want?");
            if (!questionsForUser.isEmpty()) {
                bot.sendMessage(chatId, questionsForUser.get(questionsForUser.size() - 1));
            }
            state = ConversationState.WAITING_FOR_QNA_ANSWER;
        }
    }


    private String refineSelection(String answerExampleFilePath, String userInput) {
        String answerExamples = fileReader.readFileFromResources(answerExampleFilePath); // Читаємо приклади відповіді
        String previousAnswers = memoryAnswers.get(memoryAnswers.size() - 1);

        return chatLanguageModel.chat("User refinement: " + userInput
                + " Previous answers: " + previousAnswers
                + " Answer examples: " + answerExamples);
    }

    private String makeQuestion(String promptFilePath) {
        String prompt = fileReader.readFileFromResources(promptFilePath);
        String previousAnswers = memoryAnswers.get(memoryAnswers.size() - 1);
        return chatLanguageModel.chat("Previous answers: " + previousAnswers + " " + prompt);
    }

    private String getResponseFromOntology(String ontologyPath, String promptFilePath, String userQuery) {
        String textRdf = fileReader.readFileFromResources(ontologyPath);
        String prompt = fileReader.readFileFromResources(promptFilePath);
        return chatLanguageModel.chat(prompt + " Ontology RDF/XML: " + textRdf + " Query: " + userQuery);
    }

    private void resetState() {
        state = ConversationState.INITIAL;
        memoryAnswers.clear();
        currentCoffees.clear();
    }

    private enum ConversationState {
        INITIAL,
        WAITING_FOR_QNA_ANSWER,
        WAITING_FOR_FIRST_QUERY,
        CHOOSING_BETWEEN_COFFEES,
        ASKING_MORE_PARAMETERS
    }
}
