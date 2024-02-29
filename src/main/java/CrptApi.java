import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CrptApi {
    private final Semaphore semaphore;
    private final HttpClient client;
    private final Logger logger;
    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.semaphore = new Semaphore(requestLimit);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.logger = Logger.getLogger(CrptApi.class.getName());
        this.objectMapper = new ObjectMapper();
        schedulePermitRelease(timeUnit);
    }

    private void schedulePermitRelease(TimeUnit timeUnit) {
        Runnable task = () -> {
            while (true) {
                try {
                    Thread.sleep(timeUnit.toMillis(1));
                    semaphore.release();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        };
        new Thread(task).start();
    }

    public void createDocument(CrptDocument document, String signature) {
        try {
            semaphore.acquire();
            HttpRequest request = buildRequest(document, signature);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Код ответа: " + response.statusCode());
            logger.info("Тело ответа: " + response.body());
        } catch (InterruptedException | IOException e) {
            logger.log(Level.SEVERE, "Ошибка при выполнении запроса", e);
        }
    }

    private HttpRequest buildRequest(CrptDocument document, String signature) {
        try {
            String jsonDocument = objectMapper.writeValueAsString(document);
            return HttpRequest.newBuilder()
                    .uri(new URI("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                    .header("Content-Type", "application/json")
                    .header("Signature", signature)
                    .build();
        } catch (URISyntaxException | JsonProcessingException e) {
            throw new IllegalArgumentException("Ошибка при построении запроса", e);
        }
    }

    public class CrptDocument {
        @JsonProperty("description")
        private Description description;

        @JsonProperty("doc_id")
        private String docId;

        @JsonProperty("doc_status")
        private String docStatus;

        @JsonProperty("doc_type")
        private String docType;

        @JsonProperty("importRequest")
        private boolean importRequest;

        @JsonProperty("owner_inn")
        private String ownerInn;

        @JsonProperty("participant_inn")
        private String participantInn;

        @JsonProperty("producer_inn")
        private String producerInn;

        @JsonProperty("production_date")
        private String productionDate;

        @JsonProperty("production_type")
        private String productionType;

        @JsonProperty("products")
        private List<Product> products;

        @JsonProperty("reg_date")
        private String regDate;

        @JsonProperty("reg_number")
        private String regNumber;
    }

    class Description {
        @JsonProperty("participantInn")
        private String participantInn;
    }

    class Product {
        @JsonProperty("certificate_document")
        private String certificateDocument;

        @JsonProperty("certificate_document_date")
        private String certificateDocumentDate;

        @JsonProperty("certificate_document_number")
        private String certificateDocumentNumber;

        @JsonProperty("owner_inn")
        private String ownerInn;

        @JsonProperty("producer_inn")
        private String producerInn;

        @JsonProperty("production_date")
        private String productionDate;

        @JsonProperty("tnved_code")
        private String tnvedCode;

        @JsonProperty("uit_code")
        private String uitCode;

        @JsonProperty("uitu_code")
        private String uituCode;
    }
}
