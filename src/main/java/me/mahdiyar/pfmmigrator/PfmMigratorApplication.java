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
        headers.add("x-csrf-token", "hBhHO4Un9zPVQtlAZbAFepr3rx6I5bwd2PEMMrJq");
        headers.add("cookie", "remember_web_59ba36addc2b2f9401580f014c7f58ea4e30989d=eyJpdiI6Ilk0N05MbTZBVWs5RmgweEJ1QkR3aGc9PSIsInZhbHVlIjoiNjExakZCaTVnUUdZWnF4bHlNL3VaeUVPYkpqSzdiWlRZbWRpYzJZVEFLeHlMc2N2dThvZng3WW1PY0oxL1VscFhRVWNxTVlnVzBYU3pSd3BzR1g1cHlMV0p4MGRScHlmVldaNy9velVtQnN6Q0ZBVC95d2Q4S2xhUEltOVM1TWc4b2VZbUVKdHJWWUJYY3MzSi82TFltVE1zV1Fvb3EvMWxuMjZDaTJ6SXdnVDdaUnZSbklQSzBheHJsTVo4YjFGdzNUYmw1WnVQR2g3dTlSMDZlRU5zTzRsZUtKSFhLNlhRWmc2Q3FadERNUT0iLCJtYWMiOiIwNTg3YjJiZjQ3M2M2NGI3MGViZjQ4YmNhZTZkMmZiZWZjZTdlMGMzNWU5MTU0MTM5NDVlZGY1ZTM4NTRiZTkwIiwidGFnIjoiIn0%3D; report-categories=undefined; report-budgets=undefined; report-tags=undefined; report-end=20230331; report-double=undefined; report-accounts=24; report-type=audit; report-start=20230317; audit-option-checkbox=icon%2Cdescription%2Cbalance_before%2Camount%2Cbalance_after%2Cdate%2Cto; google2fa_token=eyJpdiI6IjBvaSt5dXN6eDY3K3NpclBZWEFCVmc9PSIsInZhbHVlIjoiMkxvRno5eHF4N2RjMmxPdW8vSUJvUGhHZnNWWFJNS29WMWVuNytYbFF1amMxS0dvUDIxWHNwQ2UxYko2MWQydSIsIm1hYyI6ImUyNTZiYjdkOWZlOWIwY2E1YWQzNjBmZmE3NTlhMTc5NjJlZWQ0ZjQ0YTIwYjAxODMzNTcyYjcxZWU4MjYxYTciLCJ0YWciOiIifQ%3D%3D; laravel_token=eyJpdiI6Ik9tY0dQWU1YV0kxZFF4NUE3YUZycFE9PSIsInZhbHVlIjoiWUlPQmxyTE44RFRua3BLUlh1RW5aak8zU0FKOVp5cGd6TVZXd1JDZTlSUExZa3hZbVVwZ1FOREdCYXFRRlRuTkJEa1gwRlJzTHp1Q1FrVnZKWFpDRUpLbTFKTUE0RDZHOGpYUG9laEpTOXlqMXFPOCs2VzZnQlZaZ0tDa0pxT0pSMzZYYUY3TnR4SThybktHbWpxZERQdlJTdGZUNjV4K2dMYTIyL1REYjg3K1F5bTJQKytrUE5CVG9qYUN5a2ovelFQMjh4c2RHemtpWkVIck5qUFdnTlIwYitKSm9IY2ZkUmZHMnh0VkV5K1ROclJRbVdFSXFHUXI1S3JWbWFscmlxUTdZcmJCTEJJTk5aUm5PS1dMZFBHME0vR1lKM1lteW5nYmxCMUIwc2l4cmJrZXBCcnNiNjBJdEFKbEMzVnEiLCJtYWMiOiI4OGRhYWRkMWQwOGFhNTUwODg0NTBiMTVhMDZmNzM1NDdmYWM0M2E3YTkwYTgyNzg5YThiOTliNmU1ZmQyNDVjIiwidGFnIjoiIn0%3D; XSRF-TOKEN=eyJpdiI6IjNSeW5kS1MwV2NOZStwblNaUEsvM2c9PSIsInZhbHVlIjoidWpDL1EyblozSUorZHBKN3RjV01xalFGMjRqRGpXbTY3b0FQNit3ZkhoQzh6NTRXVWp4bktMbGg4ejNyaDI4NkNzN2lleW1DOHdydFMwK3QraWVZQkNpemRxcU5aL2J1K2YzbDZleld0bkY0MzVBUGZNV2JMaFhTaFNzZUVHcm0iLCJtYWMiOiJkNTY3NGZjODliNjAzYjYyMmM2ODczZmZjMjIyNzZhMjMxNjJkMjBjYmVhYTYyMWY0MjNiY2I5OWVkNTIxZTI2IiwidGFnIjoiIn0%3D; firefly_session=eyJpdiI6Ik1ueDJzNmdhRHJRcS9yY2JEakUyOXc9PSIsInZhbHVlIjoiRFBabzBQamV4ekwyR0FHWXp0ekVWQ3FrNnY2SUtrWnh1bk1HOW44R1JQWTNhQWpoUnd0REh2VVJ0WUx4dzhJRWROTjVIY1JtbU5BanloT3cxNVd4ZlNSMEdiRTdZODRYYUc5UWM5OGx4YTdGZlFVS0hTWEV3bzkrT1FNWml6ZCsiLCJtYWMiOiIyYjFiNzRkMWQyZDBhYzNjY2FlOTAxZWI1ZWJkZDI4YWNhMjIyMDY5YjM1MzExYjIzMTI5Yjg4YTFiNDgyMjlhIiwidGFnIjoiIn0%3D");
        var restRequest = new HttpEntity<>(model, headers);
        return restTemplate.exchange("https://pfm.mahdiyar.me/api/v1/transactions?_token=hBhHO4Un9zPVQtlAZbAFepr3rx6I5bwd2PEMMrJq",
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