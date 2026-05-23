package com.cresensolutions.leaveservice.service.ai.aiservice;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatCacheService {

    // creating In-memory cache (hashmap) -> Q-A
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    // getting value from cache
    public String get(String key){
        return cache.get(key);
    }

    // add new Q-A (key-value) in cache
    public void put(String key, String value){
        cache.put(key, value);
    }
}
