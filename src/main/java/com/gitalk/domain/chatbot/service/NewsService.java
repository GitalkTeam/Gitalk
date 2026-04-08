package com.gitalk.domain.chatbot.service;

import com.gitalk.common.api.HackerNewsClient;
import com.gitalk.common.api.OpenAIClient;
import com.gitalk.common.util.Spinner;
import com.gitalk.domain.chatbot.model.NewsItem;

import java.net.URI;
import java.time.Instant;
import java.util.List;

public class NewsService {

    private final HackerNewsClient hackerNewsClient = new HackerNewsClient();
    private final OpenAIClient openAIClient = new OpenAIClient();

    public List<NewsItem> fetchTopStories() throws Exception {
        Spinner spinner = new Spinner();
        spinner.start("HackerNews 조회 중");
        try {
            List<NewsItem> result = hackerNewsClient.fetchTopStories(5);
            spinner.stop();
            return result;
        } catch (Exception e) {
            spinner.stop();
            throw e;
        }
    }

    public String[] translateTitles(List<NewsItem> items) {
        String[] result = items.stream().map(NewsItem::title).toArray(String[]::new);
        StringBuilder prompt = new StringBuilder();
        prompt.append("아래 뉴스 제목들을 자연스러운 한국어로 번역해줘.\n");
        prompt.append("번역이 어색한 고유명사(기술명, 라이브러리명 등)는 영어 그대로 둬.\n");
        prompt.append("반드시 번호 형식으로만 답해줘. 다른 말은 하지 마.\n");
        prompt.append("1. 번역\n2. 번역\n...\n\n");
        for (int i = 0; i < items.size(); i++) {
            prompt.append(i + 1).append(". ").append(items.get(i).title()).append("\n");
        }
        Spinner spinner = new Spinner();
        spinner.start("제목 번역 중");
        try {
            String raw = openAIClient.analyze(prompt.toString());
            spinner.stop();
            String[] lines = raw.strip().split("\n");
            int idx = 0;
            for (String line : lines) {
                if (idx >= items.size()) break;
                String trimmed = line.replaceFirst("^\\d+\\.\\s*", "").trim();
                if (!trimmed.isEmpty()) result[idx++] = trimmed;
            }
        } catch (Exception ignored) {
            spinner.stop();
        }
        return result;
    }

    public String summarizeArticle(NewsItem item) throws Exception {
        String prompt = String.format(
                "다음 개발자 뉴스 기사를 한국어로 3~4문장으로 설명해줘. " +
                "어떤 내용인지, 왜 개발자들에게 중요한지 위주로 설명해줘.\n제목: %s",
                item.title());
        Spinner spinner = new Spinner();
        spinner.start("AI 설명 중");
        try {
            String result = openAIClient.analyze(prompt);
            spinner.stop();
            return result;
        } catch (Exception e) {
            spinner.stop();
            throw e;
        }
    }

    public String relativeTime(long epochSeconds) {
        long diff = Instant.now().getEpochSecond() - epochSeconds;
        if (diff < 60)    return "방금 전";
        if (diff < 3600)  return (diff / 60) + "분 전";
        if (diff < 86400) return (diff / 3600) + "시간 전";
        return (diff / 86400) + "일 전";
    }

    public String domain(String url) {
        if (url == null) return "news.ycombinator.com";
        try {
            String host = URI.create(url).getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : url;
        } catch (Exception e) {
            return url;
        }
    }
}
