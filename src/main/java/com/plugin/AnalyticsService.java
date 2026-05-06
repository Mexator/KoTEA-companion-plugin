package com.plugin;

import com.google.gson.Gson;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.posthog.java.PostHog;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnalyticsService {
    private static final String API_KEY = "phc_zaep9SmUJKSeYob5AZQ4QAzghpZ5VhF6HrjJbSmuuQws";
    private static final String HOST = "https://us.i.posthog.com";

    private static final String CAPTURE_URL = "https://us.i.posthog.com/capture/";
    private static final Gson gson = new Gson();

    private static final HttpClient httpClient = createInsecureClient();
    private static final PostHog server = new PostHog.Builder(API_KEY)
            .host(HOST)
            .build();

    private static String getId() {
        PropertiesComponent pc = PropertiesComponent.getInstance();

        String id = pc.getValue("KOTEA-plugin.id");

        if (id == null) {
            id = UUID.randomUUID().toString();
            pc.setValue("KOTEA-plugin.id",  id);
        }

        return id;
    }

    private static HttpClient createInsecureClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());

            return HttpClient.newBuilder()
                    .sslContext(sc)
                    .build();
        }
        catch (Exception e) {
            return HttpClient.newHttpClient();
        }
    }

    public static void log(String event, Map<String, Object> properties) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Map<String,Object> payload = new HashMap<>();

                payload.put("api_key", API_KEY);
                payload.put("event", event);

                Map<String, Object> propsWithId = new HashMap<>(properties);
                propsWithId.put("distinct_id", getId());
                payload.put("properties", propsWithId);

                String jsonBody = gson.toJson(payload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(CAPTURE_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            }
            catch (Exception e) {

            }
        });
    }

}
