package com.cafe.dashboard.service;

import com.cafe.dashboard.entity.Store;
import com.cafe.dashboard.repository.StoreRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class CrawlRefreshService {

    private static final Logger log = LoggerFactory.getLogger(CrawlRefreshService.class);

    /** Rough estimate shown to users while the nightly batch is running (70 stores, ~8s pacing between each). */
    private static final int BATCH_ESTIMATED_MINUTES = 40;

    private final ObjectMapper objectMapper;
    private final StoreRepository storeRepository;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    private volatile boolean batchRunning = false;
    private volatile LocalDateTime batchEta;

    private final Path webCrawlingDir = Paths.get(System.getProperty("user.dir"))
            .resolve("..").resolve("Web crawling").normalize();
    private final Path databaseDir = Paths.get(System.getProperty("user.dir"))
            .resolve("..").resolve("Database").normalize();

    public enum Status { RUNNING, DONE, ERROR }

    public static class Job {
        public volatile Status status = Status.RUNNING;
        public final List<String> log = new CopyOnWriteArrayList<>();
        public volatile Integer newReviews;
        public volatile Integer tagRows;
        public volatile String errorMessage;
    }

    public boolean isBatchRunning() {
        return batchRunning;
    }

    /** "22:40" style label for the "그때까지 업데이트 중" message, or null if no batch is running. */
    public String batchEtaLabel() {
        LocalDateTime eta = batchEta;
        return eta == null ? null : eta.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public Job start(String storeId) {
        Job existing = jobs.get(storeId);
        if (existing != null && existing.status == Status.RUNNING) {
            return existing;
        }
        Job job = new Job();
        jobs.put(storeId, job);
        executor.submit(() -> run(storeId, job));
        return job;
    }

    public Job get(String storeId) {
        return jobs.get(storeId);
    }

    /** Every night at ~22:00 (see jitter below): refresh every store's reviews/tags so all users see
     * the same daily-fresh data, without needing anyone to click the manual refresh button. Runs
     * through the same single-thread executor as manual refreshes, so a store already mid-refresh
     * (e.g. a user just clicked it) is reused rather than duplicated - see {@link #start}. */
    @Scheduled(cron = "0 0 22 * * *")
    public void runNightlyBatch() {
        // A batch that always starts at exactly 22:00:00 is itself a bot fingerprint (regular
        // automated traffic is easier to flag than human-triggered clicks), so spread the actual
        // start over a 10-minute window instead of firing on the dot.
        int jitterSeconds = ThreadLocalRandom.current().nextInt(0, 10 * 60 + 1);
        log.info("Nightly crawl batch scheduled; starting in {}s (jitter)", jitterSeconds);
        try {
            Thread.sleep(jitterSeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        List<String> storeIds = storeRepository.findAll().stream().map(Store::getStoreId).toList();
        log.info("Nightly crawl batch starting for {} stores", storeIds.size());

        batchEta = LocalDateTime.now().plusMinutes(BATCH_ESTIMATED_MINUTES);
        batchRunning = true;
        List<String> failedStoreIds = new ArrayList<>();
        try {
            for (String storeId : storeIds) {
                Job job = start(storeId);
                awaitCompletion(job);
                if (job.status == Status.ERROR) {
                    failedStoreIds.add(storeId);
                }
            }
            int succeeded = storeIds.size() - failedStoreIds.size();
            log.info("Nightly crawl batch finished: {}/{} stores succeeded", succeeded, storeIds.size());
            if (!failedStoreIds.isEmpty()) {
                log.warn("Nightly crawl batch failures ({}): {}", failedStoreIds.size(), failedStoreIds);
            }
        } finally {
            batchRunning = false;
            batchEta = null;
        }
    }

    private void awaitCompletion(Job job) {
        while (job.status == Status.RUNNING) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static final int MAX_ATTEMPTS = 2;

    /** Runs the full Review.py -> Review_category.py -> refresh_store.py pipeline for one store,
     * retrying the whole pipeline once more on failure (429s are already retried inside Review.py
     * itself; this covers everything else - timeouts, a crashed browser, a transient network blip). */
    private void run(String storeId, Job job) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                if (attempt > 1) {
                    job.log.add("이전 시도 실패 - 재시도합니다 (" + attempt + "/" + MAX_ATTEMPTS + ")...");
                }
                job.log.add("리뷰 수집 시작...");
                runProcess(job, webCrawlingDir.resolve("Review"), "Review.py", "--store-id=" + storeId);

                job.log.add("리뷰 태그 수집 시작...");
                runProcess(job, webCrawlingDir.resolve("Review"), "Review_category.py", "--store-id=" + storeId);

                job.log.add("DB 반영 중...");
                List<String> output = runProcess(job, databaseDir, "refresh_store.py", "--store-id=" + storeId);
                parseResult(job, output);

                job.status = Status.DONE;
                return;
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                job.log.add("실패 (시도 " + attempt + "/" + MAX_ATTEMPTS + "): " + message);
                if (attempt == MAX_ATTEMPTS) {
                    job.errorMessage = message;
                    job.status = Status.ERROR;
                }
            }
        }
    }

    private List<String> runProcess(Job job, Path workingDir, String script, String... args) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>(List.of("python", script));
        command.addAll(List.of(args));

        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDir.toFile())
                .redirectErrorStream(true);
        Process process = builder.start();

        List<String> lines = new java.util.ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                job.log.add(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(script + " 실행 실패 (exit=" + exitCode + ")");
        }
        return lines;
    }

    private void parseResult(Job job, List<String> output) {
        for (String line : output) {
            if (line.startsWith("RESULT_JSON:")) {
                try {
                    JsonNode node = objectMapper.readTree(line.substring("RESULT_JSON:".length()));
                    job.newReviews = node.path("newReviews").asInt();
                    job.tagRows = node.path("tagRows").asInt();
                } catch (Exception ignored) {
                    // 파싱 실패해도 job은 DONE으로 처리 (로그에 원본 출력이 남아있음)
                }
                return;
            }
        }
    }
}
