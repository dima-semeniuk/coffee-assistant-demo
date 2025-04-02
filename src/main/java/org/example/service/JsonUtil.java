package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class JsonUtil {
    private static final Logger logger = LoggerFactory.getLogger(JsonUtil.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<String> parseCoffeeList(String jsonResponse, String rNode) {
        List<String> coffeeList = new ArrayList<>();

        if (jsonResponse == null || jsonResponse.isBlank()) {
            logger.warn("Received empty JSON response.");
            return Collections.emptyList();
        }

        try {
            jsonResponse = jsonResponse.trim()
                    .replaceAll("^```json\\s*|```$", "")
                    .replaceAll("(?<!\\\\)\\n", " "); // Прибираємо некоректні переноси рядків

            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            if (!rootNode.has(rNode)) {
                logger.warn("Node '{}' not found in JSON.", rNode);
                return Collections.emptyList();
            }

            JsonNode responseArray = rootNode.get(rNode);

            if (responseArray != null) {
                if (responseArray.isArray()) {
                    for (JsonNode node : responseArray) {
                        coffeeList.add(parseCoffeeDetails(node));
                    }
                } else {
                    coffeeList.add(parseCoffeeDetails(responseArray));
                }
            } else {
                logger.warn("Node '{}' is null in JSON.", rNode);
            }
        } catch (JsonProcessingException e) {
            logger.error("JSON parsing error: {}", e.getMessage(), e);
            return Collections.emptyList();
        }

        return coffeeList;
    }

    private static String parseCoffeeDetails(JsonNode node) {
        StringBuilder details = new StringBuilder();

        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
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
            });
        } else {
            details.append(node.asText());
        }

        return details.toString();
    }
}
