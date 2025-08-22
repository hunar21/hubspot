package Http;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class HttpJsonClient {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_2)
            .build();

    public String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("GET non-2xx: " + res.statusCode() + " body=" + res.body());
        }
        return res.body();
    }

    public String postJson(URI uri, String json) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("POST non-2xx: " + res.statusCode() + " body=" + res.body());
        }
        return res.body();
    }
}
