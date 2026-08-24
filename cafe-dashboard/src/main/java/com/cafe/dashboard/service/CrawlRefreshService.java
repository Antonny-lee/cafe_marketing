package com.cafe.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class CrawlRefreshService {

    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

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

    private void run(String storeId, Job job) {
        try {
            job.log.add("리뷰 수집 시작...");
            runProcess(job, webCrawlingDir.resolve("Review"), "Review.py", "--store-id=" + storeId);

            job.log.add("리뷰 태그 수집 시작...");
            runProcess(job, webCrawlingDir.resolve("Review"), "Review_category.py", "--store-id=" + storeId);

            job.log.add("DB 반영 중...");
            List<String> output = runProcess(job, databaseDir, "refresh_store.py", "--store-id=" + storeId);
            parseResult(job, output);

            job.status = Status.DONE;
        } catch (Exception e) {
            job.errorMessage = e.getMessage() == null ? e.toString() : e.getMessage();
            job.log.add("실패: " + job.errorMessage);
            job.status = Status.ERROR;
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
