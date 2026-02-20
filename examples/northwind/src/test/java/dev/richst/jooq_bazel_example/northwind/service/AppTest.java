package dev.richst.jooq_bazel_example.northwind.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class AppTest {
    @Test
    void smokeTest() throws IOException, InterruptedException {
        var service = new App().run(0); //get a random port
        try (var client = HttpClient.newHttpClient()) {
            String apiUrl = String.format("http://localhost:%d/api/employees", service.port());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("[]");

            request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"first_name\": \"John\", \"last_name\": \"Smith\"}"))
                    .build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("""
                    {"id":1,"last_name":"Smith","first_name":"John"}""");


            request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .build();

            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("""
                    [{"id":1,"last_name":"Smith","first_name":"John"}]""");
        } finally {
            service.awaitStop();
        }


    }
}