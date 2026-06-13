package com.ld.poetry.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class LlmTranslationServiceParsingTest {

    private LlmTranslationService service;

    @BeforeEach
    void setUp() {
        service = new LlmTranslationService();
    }

    @Test
    void testFindClosingQuoteIndex() {
        // Standard quotes
        assertEquals(12, invokeFindClosingQuoteIndex("\"Hello World\""));
        
        // Escaped quotes inside
        assertEquals(16, invokeFindClosingQuoteIndex("\"Hello \\\"World\\\"\""));
        
        // Escaped backslash before quote
        assertEquals(9, invokeFindClosingQuoteIndex("\"Hello \\\\\""));
        
        // Trailing garbage (e.g. code fences or text)
        assertEquals(12, invokeFindClosingQuoteIndex("\"Hello World\"\n```"));
        assertEquals(12, invokeFindClosingQuoteIndex("\"Hello World\"   \n\n"));
        
        // Unclosed quotes
        assertEquals(-1, invokeFindClosingQuoteIndex("\"Hello World"));
        assertEquals(-1, invokeFindClosingQuoteIndex("\"Hello \\\"World"));
    }

    @Test
    void testIsCompleteToonToken() {
        // Not starting with quote should be complete
        assertTrue(invokeIsCompleteToonToken("Hello World"));
        
        // Starting and ending with quote (standard)
        assertTrue(invokeIsCompleteToonToken("\"Hello World\""));
        
        // Starting and ending with quote (with trailing code block)
        assertTrue(invokeIsCompleteToonToken("\"Hello World\"\n```"));
        
        // Unclosed quotes
        assertFalse(invokeIsCompleteToonToken("\"Hello World"));
    }

    @Test
    void testDecodeToonToken() {
        // No quotes
        assertEquals("Hello World", invokeDecodeToonToken("Hello World"));
        
        // Quotes and escaped characters
        assertEquals("Hello World", invokeDecodeToonToken("\"Hello World\""));
        assertEquals("Hello \"World\"", invokeDecodeToonToken("\"Hello \\\"World\\\"\""));
        assertEquals("Hello\nWorld", invokeDecodeToonToken("\"Hello\\nWorld\""));
        
        // Trailing code block discarded
        assertEquals("Hello World", invokeDecodeToonToken("\"Hello World\"\n```"));
        
        // Unclosed
        assertEquals("Hello World", invokeDecodeToonToken("\"Hello World"));
    }

    @Test
    void testParseStreamingTranslationView() {
        String rawResponse = """
                article:
                  title: "Translated Title"
                  content: "Translated Content\\nwith lines"
                ```""";
        
        Object view = ReflectionTestUtils.invokeMethod(service, "parseStreamingTranslationView", rawResponse);
        assertNotNull(view);
        
        String title = (String) ReflectionTestUtils.getField(view, "title");
        String content = (String) ReflectionTestUtils.getField(view, "content");
        boolean titleClosed = (boolean) ReflectionTestUtils.getField(view, "titleClosed");
        boolean contentClosed = (boolean) ReflectionTestUtils.getField(view, "contentClosed");
        
        assertEquals("Translated Title", title);
        assertEquals("Translated Content\nwith lines", content);
        assertTrue(titleClosed);
        assertTrue(contentClosed);
    }

    @Test
    void testValidateStreamingTranslationState() {
        // Case 1: Content same, Title different -> should return null
        Object state1 = createStreamingTranslationState("Hello", "你好", true, true);
        assertNull(invokeValidate(state1, "ddd", "你好"));

        // Case 2: Content same, Title same -> should return null
        Object state2 = createStreamingTranslationState("ddd", "你好", true, true);
        assertNull(invokeValidate(state2, "ddd", "你好"));

        // Case 3: Content different, Original Title is ASCII ("ddd") and Title same ("ddd") -> should succeed
        Object state3 = createStreamingTranslationState("ddd", "Hello", true, true);
        java.util.Map<String, String> res3 = invokeValidate(state3, "ddd", "你好");
        assertNotNull(res3);
        assertEquals("ddd", res3.get("title"));
        assertEquals("Hello", res3.get("content"));

        // Case 4: Content different, Original Title contains Chinese ("测试") and Title same ("测试") -> should return null
        Object state4 = createStreamingTranslationState("测试", "Hello", true, true);
        assertNull(invokeValidate(state4, "测试", "你好"));

        // Case 5: Content different, Original Title contains Chinese ("测试") and Title changed ("Test") -> should succeed
        Object state5 = createStreamingTranslationState("Test", "Hello", true, true);
        java.util.Map<String, String> res5 = invokeValidate(state5, "测试", "你好");
        assertNotNull(res5);
        assertEquals("Test", res5.get("title"));
        assertEquals("Hello", res5.get("content"));

        // Case 6: Content same, but Original Content is ASCII (e.g. code block) -> should succeed
        Object state6 = createStreamingTranslationState("Test", "System.out.println(\"Hello\");", true, true);
        java.util.Map<String, String> res6 = invokeValidate(state6, "测试", "System.out.println(\"Hello\");");
        assertNotNull(res6);
        assertEquals("System.out.println(\"Hello\");", res6.get("content"));

        // Case 7: Content same, and Original Content contains Chinese -> should return null
        Object state7 = createStreamingTranslationState("Test", "你好，世界", true, true);
        assertNull(invokeValidate(state7, "测试", "你好，世界"));
    }

    private Object createStreamingTranslationState(String title, String content, boolean titleClosed, boolean contentClosed) {
        try {
            Class<?> stateClass = Class.forName("com.ld.poetry.service.ai.LlmTranslationService$StreamingTranslationState");
            java.lang.reflect.Constructor<?> ctor = stateClass.getDeclaredConstructor(
                    String.class, String.class, boolean.class, boolean.class, String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(title, content, titleClosed, contentClosed, "");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, String> invokeValidate(Object state, String origTitle, String origContent) {
        return (java.util.Map<String, String>) ReflectionTestUtils.invokeMethod(
                service, "validateStreamingTranslationState", state, origTitle, origContent, "en");
    }

    private int invokeFindClosingQuoteIndex(String token) {
        Object result = ReflectionTestUtils.invokeMethod(service, "findClosingQuoteIndex", token);
        return result != null ? (int) result : -1;
    }

    private boolean invokeIsCompleteToonToken(String token) {
        Object result = ReflectionTestUtils.invokeMethod(service, "isCompleteToonToken", token);
        return result != null && (boolean) result;
    }

    private String invokeDecodeToonToken(String token) {
        return (String) ReflectionTestUtils.invokeMethod(service, "decodeToonToken", token);
    }
}
