package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.service.ai.aiservice.ChatCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatCacheServiceTest {

    private ChatCacheService chatCacheService;

    @BeforeEach
    void setUp() {
        chatCacheService = new ChatCacheService();
    }

    @Test
    void putAndGet_success() {
        // Arrange
        String key = "What is Java?";
        String value = "Java is a high-level programming language.";

        // Act
        chatCacheService.put(key, value);
        String retrievedValue = chatCacheService.get(key);

        // Assert
        assertEquals(value, retrievedValue, "The retrieved value should match the stored value.");
    }

    @Test
    void get_nonExistentKey_returnsNull() {
        // Act
        String result = chatCacheService.get("non-existent-key");

        // Assert
        assertNull(result, "Getting a non-existent key should return null.");
    }

    @Test
    void put_existingKey_updatesValue() {
        // Arrange
        String key = "status";
        chatCacheService.put(key, "old_value");

        // Act
        chatCacheService.put(key, "new_value");
        String result = chatCacheService.get(key);

        // Assert
        assertEquals("new_value", result, "The value for an existing key should be updated.");
    }

    @Test
    void multipleKeys_success() {
        // Arrange
        chatCacheService.put("key1", "val1");
        chatCacheService.put("key2", "val2");

        // Assert
        assertEquals("val1", chatCacheService.get("key1"));
        assertEquals("val2", chatCacheService.get("key2"));
        assertNotEquals(chatCacheService.get("key1"), chatCacheService.get("key2"));
    }

    @Test
    void put_nullHandling() {
        // ConcurrentHashMap (which you used) throws NullPointerException on null keys or values
        assertThrows(NullPointerException.class, () -> {
            chatCacheService.put(null, "someValue");
        }, "ConcurrentHashMap does not allow null keys.");

        assertThrows(NullPointerException.class, () -> {
            chatCacheService.put("someKey", null);
        }, "ConcurrentHashMap does not allow null values.");
    }
}