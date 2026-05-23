package com.cresensolutions.leaveservice.common;

public class AIConfigConstants {

    private AIConfigConstants() {}

    // For ollama setup
    public static final String OLLAMA_BASE_URL = "http://localhost:11434";
    public static final String OLLAMA_MODEL_NAME = "llama3.2";
    public static final Double MODEL_TEMPERATURE = 0.1;

    // For Saving in DB
    public static final String USER_ROLE = "user";
    public static final String ASSISTANT_ROLE = "assistant";
    public static final String INITIAL_TITLE = "New Chat";
    public static final String PRODUCT_NAME = "creni_ai_chatbot";
}
