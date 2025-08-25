package Ojbects;

import Http.Json;
import com.fasterxml.jackson.databind.JsonNode;

public class DatasetParser {
    public static Records[] parseCallRecords(String json) throws Exception {
        JsonNode root = Json.MAPPER.readTree(json);

        if (root.isArray()) {
            return Json.MAPPER.treeToValue(root, Records[].class);
        }

        if (root.isObject()) {
            JsonNode arr = root.get("callRecords"); // ✅ correct field name
            if (arr != null && arr.isArray()) {
                return Json.MAPPER.treeToValue(arr, Records[].class);
            }
            throw new IllegalStateException("Expected 'callRecords' array but not found. Keys present: " + root.fieldNames().toString());
        }

        throw new IllegalStateException("Unexpected JSON root type: " + root.getNodeType());
    }
}
