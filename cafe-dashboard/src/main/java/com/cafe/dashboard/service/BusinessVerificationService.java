package com.cafe.dashboard.service;

import com.cafe.dashboard.entity.Business;
import com.cafe.dashboard.entity.Store;
import com.cafe.dashboard.nts.NtsClient;
import com.cafe.dashboard.nts.NtsDtos;
import com.cafe.dashboard.repository.BusinessRepository;
import com.cafe.dashboard.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BusinessVerificationService {

    private final NtsClient ntsClient;
    private final BusinessRepository businessRepository;
    private final StoreRepository storeRepository;

    public record VerifyResult(boolean valid, String message, Business business) {}

    public VerifyResult verify(String bNoRaw, String openDate, String ceoName, String bizName, String phone,
                                Long ownerUserId) {
        String bNo = bNoRaw.replaceAll("[^0-9]", "");

        NtsDtos.ValidateResponse response = ntsClient.validate(
                new NtsDtos.ValidateRequest(List.of(NtsDtos.ValidateBusinessInput.of(bNo, openDate, ceoName))));

        if (response == null || response.data() == null || response.data().isEmpty()) {
            return new VerifyResult(false, "국세청 응답을 받지 못했습니다. 잠시 후 다시 시도해주세요.", null);
        }

        NtsDtos.ValidateResultItem item = response.data().get(0);
        boolean valid = "01".equals(item.valid());

        if (!valid) {
            String msg = item.valid_msg() != null ? item.valid_msg() : "입력하신 정보와 일치하는 사업자를 찾을 수 없습니다.";
            return new VerifyResult(false, msg, null);
        }

        Business business = businessRepository.findById(bNo).orElseGet(Business::new);
        business.setBizRegNo(bNo);
        business.setOwnerUserId(ownerUserId);
        business.setCeoName(ceoName);
        business.setOpenDate(openDate);
        business.setBizName(bizName);
        business.setPhone(phone);
        business.setVerified("Y");
        business.setVerifiedAt(LocalDateTime.now());

        NtsDtos.StatusInfo status = item.status();
        if (status != null) {
            business.setBizStatus(status.b_stt());
            business.setBizStatusCode(status.b_stt_cd());
            business.setTaxType(status.tax_type());
            business.setTaxTypeCode(status.tax_type_cd());
        }

        if (business.getStoreId() == null && bizName != null && !bizName.isBlank()) {
            findStoreByName(bizName).ifPresent(store -> business.setStoreId(store.getStoreId()));
        }

        businessRepository.save(business);
        return new VerifyResult(true, "인증된 사업자예요", business);
    }

    private Optional<Store> findStoreByName(String bizName) {
        String needle = normalize(bizName);
        if (needle.isEmpty()) return Optional.empty();

        List<Store> stores = storeRepository.findAll();

        Optional<Store> exact = stores.stream()
                .filter(s -> normalize(s.getName()).equals(needle))
                .findFirst();
        if (exact.isPresent()) return exact;

        return stores.stream()
                .filter(s -> {
                    String candidate = normalize(s.getName());
                    return !candidate.isEmpty() && (candidate.contains(needle) || needle.contains(candidate));
                })
                .findFirst();
    }

    /** 공백/대소문자 차이로 인한 매칭 실패를 줄이기 위한 정규화. 상호명 자체가 다른 경우는 잡아내지 못한다. */
    private String normalize(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").toLowerCase();
    }

    /** The store_id linked to this user's (first) verified business, if any. */
    public Optional<String> getMyStoreId(Long ownerUserId) {
        return businessRepository.findByOwnerUserId(ownerUserId).stream()
                .map(Business::getStoreId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst();
    }

    public void linkStore(String bizRegNo, Long ownerUserId, String storeId) {
        Business business = businessRepository.findById(bizRegNo)
                .filter(b -> b.getOwnerUserId().equals(ownerUserId))
                .orElseThrow(() -> new IllegalArgumentException("본인 소유의 사업자만 연결할 수 있습니다."));
        business.setStoreId(storeId == null || storeId.isBlank() ? null : storeId);
        businessRepository.save(business);
    }
}
