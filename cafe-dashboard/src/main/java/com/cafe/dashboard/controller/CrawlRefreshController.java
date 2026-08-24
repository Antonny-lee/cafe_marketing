package com.cafe.dashboard.controller;

import com.cafe.dashboard.service.CrawlRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class CrawlRefreshController {

    private final CrawlRefreshService crawlRefreshService;

    public record RefreshStatus(String status, List<String> log, Integer newReviews, Integer tagRows, String errorMessage) {}

    @PostMapping("/stores/{storeId}/refresh")
    @ResponseBody
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RefreshStatus start(@PathVariable String storeId) {
        CrawlRefreshService.Job job = crawlRefreshService.start(storeId);
        return toStatus(job);
    }

    @GetMapping("/stores/{storeId}/refresh/status")
    @ResponseBody
    public RefreshStatus status(@PathVariable String storeId) {
        CrawlRefreshService.Job job = crawlRefreshService.get(storeId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "진행 중인 갱신 작업이 없습니다.");
        }
        return toStatus(job);
    }

    private RefreshStatus toStatus(CrawlRefreshService.Job job) {
        return new RefreshStatus(job.status.name(), List.copyOf(job.log), job.newReviews, job.tagRows, job.errorMessage);
    }
}
