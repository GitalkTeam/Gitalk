package com.gitalk.domain.chatbot.service;

import com.gitalk.common.api.GithubTrendingClient;
import com.gitalk.common.api.OpenAIClient;
import com.gitalk.common.util.Spinner;
import com.gitalk.domain.chatbot.model.TrendingRepo;

import java.util.List;

public class TrendingService {

    private final GithubTrendingClient trendingClient = new GithubTrendingClient();
    private final OpenAIClient openAIClient = new OpenAIClient();

    public static String filterDesc(String filter) {
        if (filter == null) return "전체";
        return filter.startsWith("topic:") ? "topic:" + filter.substring(6) : filter;
    }

    public List<TrendingRepo> fetchTrending(String filter) throws Exception {
        Spinner spinner = new Spinner();
        spinner.start("GitHub 트렌딩 조회 중 [" + filterDesc(filter) + "]");
        try {
            List<TrendingRepo> result = trendingClient.fetchTrending(5, filter);
            spinner.stop();
            return result;
        } catch (Exception e) {
            spinner.stop();
            throw e;
        }
    }

    public String[] translateDescriptions(List<TrendingRepo> repos) {
        String[] result = new String[repos.size()];
        StringBuilder prompt = new StringBuilder();
        prompt.append("아래 레포지토리 설명들을 한국어로 20자 이내로 요약해줘.\n");
        prompt.append("반드시 번호 형식으로만 답해줘. 다른 말은 하지 마.\n");
        prompt.append("1. 번역\n2. 번역\n...\n\n");
        for (int i = 0; i < repos.size(); i++) {
            String desc = repos.get(i).description();
            prompt.append(i + 1).append(". ").append(desc != null ? desc : "(설명 없음)").append("\n");
        }
        Spinner spinner = new Spinner();
        spinner.start("설명 번역 중");
        try {
            String raw = openAIClient.analyze(prompt.toString());
            spinner.stop();
            String[] lines = raw.strip().split("\n");
            int idx = 0;
            for (String line : lines) {
                if (idx >= repos.size()) break;
                String trimmed = line.replaceFirst("^\\d+\\.\\s*", "").trim();
                if (!trimmed.isEmpty()) result[idx++] = trimmed;
            }
        } catch (Exception e) {
            spinner.stop();
            for (int i = 0; i < repos.size(); i++) result[i] = repos.get(i).description();
        }
        return result;
    }

    public String analyzeRepo(TrendingRepo repo) throws Exception {
        String prompt = String.format(
                "GitHub 레포지토리 '%s'가 왜 인기 있는지 한국어로 3~4문장으로 설명해줘.\n언어: %s\n설명: %s",
                repo.fullName(), repo.language(), repo.description());
        Spinner spinner = new Spinner();
        spinner.start("AI 분석 중");
        try {
            String result = openAIClient.analyze(prompt);
            spinner.stop();
            return result;
        } catch (Exception e) {
            spinner.stop();
            throw e;
        }
    }
}
