package Ojbects;

import Http.Json;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.List;

public class GenericParser {

    /**
     * Parse a JSON payload that may be:
     *  1) a top-level array:            [ {...}, {...} ]
     *  2) a top-level object containing an array under some key:
     *        { "items": [ {...}, ... ] }  or  { "results": [ ... ] }  etc.
     *
     * Strategy:
     *  - If root is an array -> map it.
     *  - If root is an object:
     *      - If candidateKeys provided, try them in order.
     *      - Else, auto-detect: take the FIRST array field found.
     */
    public static <T> List<T> parseArrayFlexible(String json,
                                                 Class<T> elementClass,
                                                 String... candidateKeys) throws Exception {
        JsonNode root = Json.MAPPER.readTree(json);

        // Case 1: top-level array
        if (root.isArray()) {
            return Json.MAPPER.readerForListOf(elementClass).readValue(root);
        }

        // Case 2: object with array field
        if (root.isObject()) {
            // Try explicit candidate keys first
            if (candidateKeys != null && candidateKeys.length > 0) {
                for (String key : candidateKeys) {
                    JsonNode arr = root.get(key);
                    if (arr != null && arr.isArray()) {
                        return Json.MAPPER.readerForListOf(elementClass).readValue(arr);
                    }
                }
            }

            // Auto-detect: first array field we encounter
            Iterator<String> it = root.fieldNames();
            while (it.hasNext()) {
                String key = it.next();
                JsonNode val = root.get(key);
                if (val != null && val.isArray()) {
                    return Json.MAPPER.readerForListOf(elementClass).readValue(val);
                }
            }

            // No array found
            throw new IllegalStateException("No array field found in the JSON object.");
        }

        throw new IllegalStateException("Unexpected JSON root type: " + root.getNodeType());
    }

    /**
     * Parse a single object payload, or extract the first object from an object field.
     * Useful if the question returns one object instead of an array.
     */
    public static <T> T parseObjectFlexible(String json,
                                            Class<T> type,
                                            String... candidateKeys) throws Exception {
        JsonNode root = Json.MAPPER.readTree(json);

        if (root.isObject()) {
            // If the whole root matches the target type
            return Json.MAPPER.treeToValue(root, type);
        }

        if (root.isArray() && root.size() > 0) {
            // If they gave an array but you only need the first
            return Json.MAPPER.treeToValue(root.get(0), type);
        }

        // Try to find an object under candidate keys
        if (candidateKeys != null) {
            for (String key : candidateKeys) {
                JsonNode node = root.get(key);
                if (node != null && node.isObject()) {
                    return Json.MAPPER.treeToValue(node, type);
                }
            }
        }

        throw new IllegalStateException("Could not parse a single object from the payload.");
    }
}
