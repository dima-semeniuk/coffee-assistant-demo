package org.example.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

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
    private final List<String> memoryAnswers = new ArrayList<>();
    private final Scanner scanner = new Scanner(System.in);

    public void assistForCoffee() {
        try {
            logger.info("Hello! I am a coffee assistant! What coffee do you want: ");
            String response = getResponseFromOntology(RDF_FILE_PATH, PROMPT_RDF);

            for (int counter = 0; counter < 5; counter++) {
                if (response.contains("answer")) {
                    List<String> qnaList = JsonUtil.parseCoffeeList(response, "answer");
                        logger.info(qnaList.get(0));
                        qnaList.clear();
                        logger.info("So, let's return to coffee) What coffee do you want?");
                        response = getResponseFromOntology(RDF_FILE_PATH, PROMPT_RDF);
                        continue;
                }
                break;
            }

            //When the user has made a QNA query for the sixth time, exit the app
            if (response.contains("answer")) {
                logger.info("Goodbye!!!");
                return;
            }

            memoryAnswers.add(response);
            logger.info(response);


            while (true) {
                List<String> coffees = JsonUtil.parseCoffeeList(response, "response");

                if (coffees.size() == 1) {
                    logger.info("Your coffee " + "☕" + coffees.get(0));
                    return;
                }

                if (coffees.size() == 2) {
                    response = refineSelection(PROMPT_CHOOSE_ONE, PROMPT_RESPONSE_EXAMPLE);
                    logger.info(response);
                    if (response.contains("answer")) {
                        logger.info("Goodbye!!!");
                        return;
                    }
                } else if (coffees.size() > 2) {
                    response = refineSelection(PROMPT_MORE_PARAM, PROMPT_RESPONSE_EXAMPLE);
                    logger.info(response);
                    if (response.contains("answer")) {
                        logger.info("Goodbye!!!");
                        return;
                    }
                } else {
                    logger.info("No coffee found. Please specify more general parameters like 'taste', " +
                            "'acidity', 'roasting method', 'processing type' etc.");
                    response = getResponseFromOntology(RDF_FILE_PATH, PROMPT_RDF);
                    logger.info(response);

                    for (int counter = 0; counter < 5; counter++) {
                        if (response.contains("answer")) {
                            List<String> qnaList = JsonUtil.parseCoffeeList(response, "answer");
                            logger.info(qnaList.get(0));
                            qnaList.clear();
                            logger.info("So, let's return to coffee) What coffee do you want?");
                            response = getResponseFromOntology(RDF_FILE_PATH, PROMPT_RDF);
                            continue;
                        }
                        break;
                    }

                    //When the user has made a QNA query for the sixth time, exit the app
                    if (response.contains("answer")) {
                        logger.info("Goodbye!!!");
                        return;
                    }
                }

                memoryAnswers.add(response);
            }
        } finally {
            scanner.close();
        }

    }

    private String refineSelection(String prompt1FilePath, String prompt2FilePath) {
        String prompt = fileReader.readFileFromResources(prompt1FilePath);
        String previousAnswers = memoryAnswers.get(memoryAnswers.size() - 1);
        String question = chatLanguageModel.chat("Previous answers: " + previousAnswers + " " + prompt);
        logger.info(question);

        String prompt2 = fileReader.readFileFromResources(prompt2FilePath);
        String userInput = scanner.nextLine();
        String response =  chatLanguageModel.chat("User refinement: " + userInput + " Previous answers: " + previousAnswers
                + " If the user is unsure or don't know, select the best coffee yourself from previous variant. "
                + prompt2);

        for (int counter = 0; counter < 5; counter++) {
            if (response.contains("answer")) {
                List<String> qnaList = JsonUtil.parseCoffeeList(response, "answer");
                logger.info(qnaList.get(0));
                qnaList.clear();
                logger.info("So, let's return to coffee) What coffee do you want? " + question);
                userInput = scanner.nextLine();
                response = chatLanguageModel.chat("User refinement: " + userInput + " Previous answers: " + previousAnswers
                        + " If the user is unsure or don't know, select the best coffee yourself from previous variant. "
                        + prompt2);
                continue;
            }
            break;
        }

        return response;
    }

    private String getResponseFromOntology(String ontologyPath, String promptPath) {
        String textRdf = fileReader.readFileFromResources(ontologyPath);
        String prompt = fileReader.readFileFromResources(promptPath);
        String userQuery = scanner.nextLine();
        return chatLanguageModel.chat(prompt + " Ontology RDF/XML: " + textRdf + " Query: " + userQuery);
    }
}
