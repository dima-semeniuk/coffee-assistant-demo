package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class AiGeminiChatModel {
    private static final Logger logger = LoggerFactory.getLogger(AiGeminiChatModel.class);

    private static final String RDF_FILE_PATH = "src/main/resources/Kulturra_Coffee.rdf";
    private static final String PROMPT_RDF = "src/main/resources/prompt-for-rds-search.txt";
    private static final String PROMPT_RESPONSE_EXAMPLE = "src/main/resources/prompt-response-example.txt";
    private static final String PROMPT_MORE_PARAM = "src/main/resources/prompt-for-question-for-more-param.txt";
    private static final String PROMPT_CHOOSE_ONE = "src/main/resources/prompt-for-question-to-choose-one-coffee.txt";

    private final FileReader fileReader = new FileReader();
    private final List<String> memoryAnswers = new ArrayList<>();
    private final Scanner scanner = new Scanner(System.in);

    ChatLanguageModel gemini = GoogleAiGeminiChatModel.builder()
            .apiKey(System.getenv("GEMINI_AI_KEY"))
            .modelName("gemini-2.0-flash")
            .build();

    public void assistForCoffee() {
        try {
            logger.info("Hello! I am a coffee assistant! What coffee do you want: ");

            String response = getResponseFromOntology(RDF_FILE_PATH, PROMPT_RDF);
            memoryAnswers.add(response);

            logger.info(response);

            while (true) {
                List<String> coffees = parseCoffeeList(response);

                if (coffees.size() == 1) {
                    System.out.println("Your coffee: " + coffees.get(0));
                    return;
                }

                if (coffees.size() == 2) {
                    response = refineSelection(PROMPT_CHOOSE_ONE, PROMPT_RESPONSE_EXAMPLE);
                    logger.info(response);
                } else if (coffees.size() > 2) {
                    response = refineSelection(PROMPT_MORE_PARAM, PROMPT_RESPONSE_EXAMPLE);
                    logger.info(response);
                } else {
                    logger.info("No coffee found. Please specify more general parameters like 'taste', " +
                            "'acidity', 'roasting method', 'processing type' etc.");
                    response = getResponseFromOntology(RDF_FILE_PATH, PROMPT_RDF);
                    logger.info(response);
                }

                memoryAnswers.add(response);
            }
        } finally {
            scanner.close();
        }

    }

    private String refineSelection(String prompt1FilePath, String prompt2FilePath) {
        String prompt = fileReader.readFileFromResources(prompt1FilePath);
        String prompt2 = fileReader.readFileFromResources(prompt2FilePath);
        String previousAnswers = memoryAnswers.get(memoryAnswers.size() - 1);
        logger.info(gemini.chat("Previous answers: " + previousAnswers + " " + prompt));
        String userInput = scanner.nextLine();
        return gemini.chat("User refinement: " + userInput + " Previous answers: " + previousAnswers
                + " If the user is unsure or don't know, select the best coffee yourself from previous variant. "
                + prompt2);
    }

    private String getResponseFromOntology(String ontologyPath, String promptPath) {
        String textRdf = fileReader.readFileFromResources(ontologyPath);
        String prompt = fileReader.readFileFromResources(promptPath);
        String userQuery = scanner.nextLine();
        return gemini.chat(prompt + " Ontology RDF/XML: " + textRdf + " Query: " + userQuery);
    }

    private List<String> parseCoffeeList(String jsonResponse) {
        List<String> coffeeList = new ArrayList<>();

        jsonResponse = jsonResponse.trim().replaceAll("^```json|```$", "");

        try {
            JsonNode rootNode = new ObjectMapper().readTree(jsonResponse);
            JsonNode responseArray = rootNode.get("response");

            if (responseArray != null && responseArray.isArray()) {
                for (JsonNode node : responseArray) {
                    coffeeList.add(parseCoffeeDetails(node));
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON parsing error: " + e);
        }
        return coffeeList;
    }

    private String parseCoffeeDetails(JsonNode node) {
        StringBuilder details = new StringBuilder(node.has("name") ? node.get("name").asText() : "Unknown Coffee");
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!key.equals("name")) {
                details.append(System.lineSeparator()).append(key).append(": ");
                JsonNode value = entry.getValue();
                if (value.isArray()) {
                    List<String> values = new ArrayList<>();
                    value.forEach(nde -> values.add(nde.asText()));
                    String join = String.join(", ", values);
                    details.append(join);
                } else if (value.isTextual()) {
                    details.append(value.asText());
                }
            }
        });

        return details.toString();
    }
}
