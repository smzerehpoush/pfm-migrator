package me.mahdiyar.pfmmigrator;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class PfmMigratorApplication implements CommandLineRunner {

    File input = createFileIfNotExists("/tmp/data.csv");
    File errors = createFileIfNotExists("/tmp/errors.csv");
    File succeed = createFileIfNotExists("/tmp/succeed.csv");

    FileWriter errorWriter = new FileWriter(errors);
    FileWriter succeedWriter = new FileWriter(succeed);

    public PfmMigratorApplication() throws IOException {
    }

    public static void main(String[] args) {
        SpringApplication.run(PfmMigratorApplication.class, args);
    }

    private static void processLine(String line) throws Exception {
        var parts = line.split(",");
        var id = parts[0].trim();
        var date = parts[1].trim();
        var amount = parts[2].trim();
        var description = parts[3].trim();
        var category = parts[4].trim();
        var account = parts[5].trim();
        var mode = Mode.valueOf(parts[6]);
        TransactionModel transaction;
        if (mode == Mode.Withdraw) {
            transaction = new TransactionModel("withdrawal", date, amount, description, mapBankId(account), account, category);
        } else {
            if (mode == Mode.Deposit)
                transaction = new TransactionModel("deposit", date, amount, description, account, mapBankId(account), category);
            else if (mode == Mode.Transfer)
                transaction = new TransactionModel("transfer", date, amount, description, category, mapBankId(category), account, mapBankId(account));
            else throw new IllegalArgumentException("mode is not supported");
        }
        var requestModel = new RequestModel();
        requestModel.setTransactions(List.of(transaction));
        System.out.println("calling api for id " + id);
        var apiResponse = callApi(requestModel);
        if (apiResponse.getStatusCode() != HttpStatus.OK) {
            System.out.println("id " + id + " failed");
            throw new Exception();
        }
        System.out.println("id " + id + " inserted successfully");

    }


    private static ResponseEntity<Object> callApi(RequestModel model) {
        var restTemplate = new RestTemplate();
        var headers = new HttpHeaders();
        headers.add("authority", "pfm.mahdiyar.me");
        headers.add("accept", "application/json, text/plain, */*");
        headers.add("accept-language", "en-US,en;q=0.9,fa;q=0.8");
        headers.add("content-type", "application/json");
        headers.add("dnt", "1");
        headers.add("origin", "https://pfm.mahdiyar.me");
        headers.add("x-csrf-token", "priMZ3AwDUgqVFNbjyfEh6Ul9ppN2hhkTUoM7AHf");
        headers.add("cookie", "google2fa_token=eyJpdiI6Inl6eDdFaXUzMjBtNTZjYllYR3dtRWc9PSIsInZhbHVlIjoiZEtPN0hiOGY0TVUxNzl3WEs0cVZyY2hJM1RvWUgvc0duN1dWOUlCTHhLcmlYYWFjeXVhbXpZU3BSZWNWZ3hzRSIsIm1hYyI6Ijc3Y2MzMjk3YjM1NGJiYTYxM2Q0N2YyNzdjZWI3YmViOTY2ODUxZjhhY2Y0NzhjZmZiYWE0ZTkyNTQ4Nzc5NzQiLCJ0YWciOiIifQ==; laravel_token=eyJpdiI6IjhidGxGVys3K0EwcVJkaEJGakkyUlE9PSIsInZhbHVlIjoib0tuZUhlMEhKalJ2N3B6T0VWdzkxRllkUGphakVYMnVwWFpLRk1JQUZVNExDYkdma0V1K3pvVG9IaVpYSmFvV3A0a2VmNXh1U3FHZHdmY3lqS3dKSWlTVHJzWGxUNFBlb1k2UnBwbGtnQUJlNy9aZ3V4VlJOL1NMa21VSEU4eklOZzdWOHA4NXRMUFNGTWxMTHRZMTg0Q2R1SnBiNDlWZWdZMzFlN0dlbVpCREhEWVFWQlh2cThteGxISHg1NWRnd2dGSUJ1N2U1NDZMa0YzT0FBSTNuMFh2VklGRVhpQmVZL3plYTZscU52dzZ4ZllqeU55dExSdUJiVHl0NXMrUmlZRkMyWEtzcTlUZG4rSysvZjhRbGtWRTlxUURLV0w2Z2JOa0Y3NXhnMGNQbXNKRGRlbWhDNU9DSXJ2RzRUb20iLCJtYWMiOiI1MTFmZTYzNTI4MDZlMjQ4ZjE1NTdlOWFmOThjMjY2MWI3OWY2ZmQ0MmQzYjUzNDk4YmY0YWYzZjlkNzdhMjUzIiwidGFnIjoiIn0=; XSRF-TOKEN=eyJpdiI6ImZQVHZ3U3I2VytGcEtTSXZ5NVBRalE9PSIsInZhbHVlIjoidXFaMmRlcSt3K0loNkM1UVZ1dlNJelNEMStLcFdrNWNLaldLS2xJeWgyQXpuajRYa25FSUFyekxkUTJUYTA5NUlBdE51ZFRnODNqNTFoTTNNMkhZRUsxZkFKMy96NnhUWkZTcy9BMmgyVGw3T2NCR2FheldLdTJ4aUE2SHRsU0UiLCJtYWMiOiJiYTdmOTlkN2RhNWI2YjY2YTNlYTU4ZWJmOGQ3NDdmYzVmOGIwZTA2NzllMzMwNDBjNmE0OWRhNTkxMWQwNTE0IiwidGFnIjoiIn0=; firefly_session=eyJpdiI6ImhEN1NSZWtweitkUjRIeXFsdmhQdHc9PSIsInZhbHVlIjoiYVVBd0NkUXRrV0NWS0I4VCtlY201TXVDNnh2MzEwSW0wV1E1S0FIcDg4bEJtTFErbmMzSVVGTVdPbHRqYTFtTzBrTE5lY08yV3hUWEJUZkVnNWV2U3JuQ2piUk9Ya09tRHptVkdjSXpoa3hhVTRWUEJvYjNPODJ4MnZjSTlHNXYiLCJtYWMiOiI5MzhhMzc1OGExYjU3ZWY1NzIxM2UxMzI3NjI0YzNiYmRhOWE3NWU3YzZmMzE2MjBlMjViNGI2YjQ0ZjI2ODlmIiwidGFnIjoiIn0=");
        var restRequest = new HttpEntity<>(model, headers);
        return restTemplate.exchange("http://old-pfm.mahdiyar.me/api/v1/transactions?_token=priMZ3AwDUgqVFNbjyfEh6Ul9ppN2hhkTUoM7AHf",
                HttpMethod.POST, restRequest, Object.class);
    }

    private static int mapBankId(String bankName) {
        return switch (bankName) {
            case "آینده کوتاه مدت قدیمی" -> 53;
            case "آینده کوتاه مدت" -> 1;
            case "پاسارگاد مهرا" -> 18;
            case "پاسارگاد مهیاس" -> 20;
            case "ملت کوتاه مدت" -> 43;
            case "سامان" -> 10;
            case "اقتصاد نوین" -> 14;
            case "پاسارگاد" -> 16;
            case "سپ کارت" -> 12;
            case "آینده ایران کارت" -> 6;
            case "رفاه مامان" -> 33;
            case "دی مامان" -> 34;
            case "سپه" -> 47;
            case "پاسارگاد مامان" -> 22;
            case "رسالت" -> 27;
            case "آینده دسته چک" -> 8;
            case "بلو بانک" -> 24;
            case "کیف پول" -> 4;
            case "آینده ساز" -> 36;
            default -> -1;
        };
    }

    private static File createFileIfNotExists(String fileName) {
        var path = Path.of(fileName);
        if (!Files.exists(path)) {
            try {
                Files.createFile(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return new File(fileName);
    }

    @Override
    public void run(String... args) throws Exception {
        var lines = new ArrayList<String>();
        try {
            var scanner = new Scanner(input);
            while (scanner.hasNextLine()) {
                lines.add(scanner.nextLine());
            }
            var done = new AtomicInteger();
            lines.parallelStream().forEach(
                    line -> {
                        try {
                            processLine(line);
                            done.getAndIncrement();
                            succeedWriter.write(line + "\n");
                        } catch (Exception e) {
                            System.err.println(e.getMessage());
                            try {
                                errorWriter.write(line + "\n");
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        } finally {
                            System.out.println("progress : " + (done.get() * 100 / lines.size()) + "% ");
                            System.out.println("left : " + (lines.size() - done.get()));
                        }
                    }
            );
            scanner.close();
            errorWriter.flush();
            succeedWriter.flush();
            System.out.println("........FINISH........");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}

@Data
class RequestModel {
    private List<TransactionModel> transactions;

}

@Data
class TransactionModel {
    public TransactionModel(String type, String date, String amount, String description, int sourceId, String sourceName, String categoryName) {
        this.type = type;
        this.date = date;
        this.amount = amount;
        this.description = description;
        this.sourceId = sourceId;
        this.sourceName = sourceName;
        this.destinationId = 5;
        this.categoryName = categoryName;
    }

    public TransactionModel(String type, String date, String amount, String description, String destinationName, int destinationId, String categoryName) {
        this.type = type;
        this.date = date;
        this.amount = amount;
        this.description = description;
        this.sourceId = 5;
        this.destinationName = destinationName;
        this.destinationId = destinationId;
        this.categoryName = categoryName;
    }

    public TransactionModel(String type, String date, String amount, String description, String sourceName, int sourceId, String destinationName, int destinationId) {
        this.type = type;
        this.date = date;
        this.amount = amount;
        this.description = description;
        this.sourceId = sourceId;
        this.sourceName = sourceName;
        this.destinationName = destinationName;
        this.destinationId = destinationId;
    }

    private String type;
    private String date;
    private String amount;
    private String description;
    @JsonProperty("source_id")
    private int sourceId;
    @JsonProperty("source_name")
    private String sourceName;
    @JsonProperty("destination_id")
    private int destinationId;
    private String destinationName = "";
    @JsonProperty("category_name")
    private String categoryName;
    private String interestDate = "";
    private String bookDate = "";
    private String processDate = "";
    private String dueDate = "";
    private String paymentDate = "";
    private String invoiceDate = "";
    private String internalReference = "";
    private String notes = "";
    private String externalUrl = "";
}

enum Mode {
    Deposit,
    Withdraw,
    Transfer
}