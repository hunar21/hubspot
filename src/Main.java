import Http.HttpJsonClient;
import Http.Json;
import Ojbects.*;

import java.net.URI;
import java.util.*;

public class Main {

    private static final String GET_URL =
            "https://candidate.hubteam.com/candidateTest/v3/problem/dataset?userKey=69bf2d64913a583b62a92f53b50d";

    private static final String POST_URL =
            "https://candidate.hubteam.com/candidateTest/v3/problem/result?userKey=69bf2d64913a583b62a92f53b50d";

    public static void main(String[] args) throws Exception {
        var http = new HttpJsonClient();

        // 1) GET dataset
        System.out.println("Fetching dataset...");
        String json = http.get(GET_URL);
       // System.out.println(json);

        // 2) Parse into Ojbects
        CallRecord[] calls = GenericParser.parseArrayFlexible(json, CallRecord.class).toArray(new CallRecord[0]);
        System.out.println("Total records: " + calls.length);
        System.out.println("Call Structure = " + Arrays.toString(Arrays.stream(calls).toArray()));

        // 3) Compute results main function
        List<ResultEntry> results = Solver.computeResults(calls);

        // 4) Wrap and POST back
        ResultWrapper payload = new ResultWrapper(results);
        String body = Json.MAPPER.writeValueAsString(payload);

        System.out.println("Posting results (" + results.size() + " rows)...");
        String postResponse = http.postJson(URI.create(POST_URL), body);
        System.out.println("POST response:");
        System.out.println(postResponse);
    }
}
