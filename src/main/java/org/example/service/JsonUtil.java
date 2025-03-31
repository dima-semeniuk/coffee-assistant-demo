package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JsonUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<String> parseCoffeeList(String jsonResponse, String rNode) {
        List<String> coffeeList = new ArrayList<>();
        jsonResponse = jsonResponse.trim().replaceAll("^```json\\s*|```$", "")
                .replaceAll("(?<!\\\\)\\n", " ");

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
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
                throw new RuntimeException("Node '" + rNode + "' not found in JSON.");
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON parsing error: " + e.getMessage(), e);
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
