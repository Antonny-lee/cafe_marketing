package com.cafe.dashboard.nts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 공공데이터포털 "국세청_사업자등록정보 진위확인 및 상태조회 서비스" 연동.
 * .env의 NTS_SERVICE_KEY는 "디코딩" 키를 넣어야 한다 (RestClient가 URL 인코딩을 알아서 함).
 */
@Component
public class NtsClient {

    private final RestClient restClient;
    private final String serviceKey;

    public NtsClient(@Value("${nts.base-url}") String baseUrl, @Value("${nts.service-key}") String serviceKey) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.serviceKey = serviceKey;
    }

    public NtsDtos.ValidateResponse validate(NtsDtos.ValidateRequest request) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/validate")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("returnType", "JSON")
                        .build())
                .body(request)
                .retrieve()
                .body(NtsDtos.ValidateResponse.class);
    }

    public NtsDtos.StatusResponse status(String bNo) {
        return status(List.of(bNo));
    }

    public NtsDtos.StatusResponse status(List<String> bNos) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/status")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("returnType", "JSON")
                        .build())
                .body(new NtsDtos.StatusRequest(bNos))
                .retrieve()
                .body(NtsDtos.StatusResponse.class);
    }
}
