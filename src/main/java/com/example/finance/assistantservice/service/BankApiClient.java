package com.example.finance.assistantservice.service;

import com.example.finance.assistantservice.webdto.AccountDto;
import com.example.finance.assistantservice.webdto.TokenResponse;
import com.example.finance.assistantservice.webdto.TransactionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class BankApiClient {

    private final RestTemplateBuilder restTemplateBuilder;

    public TokenResponse fetchToken(String tokenUrl,
                                    String clientId,
                                    String clientSecret,
                                    String username,
                                    String password,
                                    String scope) {
        RestTemplate rt = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        if (scope != null && !scope.isBlank()) {
            form.add("scope", scope);
        }
        form.add("username", username);
        form.add("password", password);

        HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(form, headers);
        ResponseEntity<TokenResponse> resp = rt.postForEntity(tokenUrl, req, TokenResponse.class);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("Failed to fetch token: status=" + resp.getStatusCode());
        }
        return resp.getBody();
    }

    public List<AccountDto> fetchAccounts(String accountsUrl, String accessToken) {
        RestTemplate rt = restTemplateBuilder.build();
        HttpHeaders headers = bearer(accessToken);
        ResponseEntity<AccountDto[]> resp = rt.exchange(
                accountsUrl, HttpMethod.GET, new HttpEntity<>(headers), AccountDto[].class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to fetch accounts: status=" + resp.getStatusCode());
        }
        return Arrays.asList(Objects.requireNonNullElse(resp.getBody(), new AccountDto[0]));
    }

    public List<TransactionDto> fetchTransactions(String transactionsUrl, String accessToken) {
        RestTemplate rt = restTemplateBuilder.build();
        HttpHeaders headers = bearer(accessToken);
        ResponseEntity<TransactionDto[]> resp = rt.exchange(
                transactionsUrl, HttpMethod.GET, new HttpEntity<>(headers), TransactionDto[].class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to fetch transactions: status=" + resp.getStatusCode());
        }
        return Arrays.asList(Objects.requireNonNullElse(resp.getBody(), new TransactionDto[0]));
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }
}
