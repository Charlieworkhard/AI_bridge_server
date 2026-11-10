package org.example.service.llm;

import java.util.List;
import java.util.Map;

public interface LlmService {

    /** history 为历史对话（role/content），userText 为本次输入。 */
    String chat(List<Map<String, String>> history, String userText);
}
