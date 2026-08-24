package com.cafe.dashboard.nts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** DTOs for 공공데이터포털 "국세청_사업자등록정보 진위확인 및 상태조회 서비스" (v1.1). */
public class NtsDtos {

    public record ValidateBusinessInput(
            String b_no,
            String start_dt,
            String p_nm,
            String p_nm2,
            String b_nm,
            String corp_no,
            String b_sector,
            String b_type,
            String b_adr
    ) {
        public static ValidateBusinessInput of(String bNo, String startDt, String pNm) {
            return new ValidateBusinessInput(bNo, startDt, pNm, "", "", "", "", "", "");
        }
    }

    public record ValidateRequest(List<ValidateBusinessInput> businesses) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusInfo(
            String b_stt,
            String b_stt_cd,
            String tax_type,
            String tax_type_cd,
            String end_dt,
            String rbf_tax_type,
            String rbf_tax_type_cd
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ValidateResultItem(
            String b_no,
            String valid,
            String valid_msg,
            StatusInfo status
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ValidateResponse(
            String status_code,
            Integer request_cnt,
            Integer valid_cnt,
            List<ValidateResultItem> data
    ) {}

    public record StatusRequest(List<String> b_no) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusResultItem(
            String b_no,
            String b_stt,
            String b_stt_cd,
            String tax_type,
            String tax_type_cd,
            String end_dt,
            String rbf_tax_type,
            String rbf_tax_type_cd
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusResponse(
            String status_code,
            Integer match_cnt,
            Integer request_cnt,
            List<StatusResultItem> data
    ) {}
}
