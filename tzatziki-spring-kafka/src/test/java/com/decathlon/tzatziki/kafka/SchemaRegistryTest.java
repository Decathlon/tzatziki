package com.decathlon.tzatziki.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for SchemaRegistry to validate the fix for NumberFormatException
 * when parsing schema IDs with query parameters.
 */
public class SchemaRegistryTest {

    private static final String endpoint = "/schemaRegistry/";

    @Test
    @DisplayName("Test regex extraction - current implementation reproduces the bug")
    void testCurrentRegexImplementation_ReproducesBug() {
        // Current implementation regex
        String currentRegex = endpoint + "schemas/ids/(.+?)(\\?.*)?";
        
        String pathWithSingleDigit = "/schemaRegistry/schemas/ids/1?fetchMaxId=false&subject=order-purchase-topic-value";
        String pathWithMultiDigit = "/schemaRegistry/schemas/ids/10?fetchMaxId=false&subject=order-purchase-topic-value";
        String pathWithoutQuery = "/schemaRegistry/schemas/ids/5";
        
        // Test current implementation
        String extractedSingle = pathWithSingleDigit.replaceAll(currentRegex, "$1");
        String extractedMulti = pathWithMultiDigit.replaceAll(currentRegex, "$1");
        String extractedNoQuery = pathWithoutQuery.replaceAll(currentRegex, "$1");
        
        System.out.println("Current regex results:");
        System.out.println("Single digit: '" + extractedSingle + "'");
        System.out.println("Multi digit: '" + extractedMulti + "'");
        System.out.println("No query: '" + extractedNoQuery + "'");
        
        // Show that current implementation fails for multi-digit with query params
        try {
            Integer.parseInt(extractedMulti);
            fail("Expected NumberFormatException was not thrown");
        } catch (NumberFormatException e) {
            // This confirms the bug exists
            assertTrue(e.getMessage().contains("10?fetchMaxId=false"), 
                "Bug confirmed: regex extracts query params along with ID");
        }
    }
    
    @Test
    @DisplayName("Test proposed fix - should extract ID correctly")
    void testProposedFix_ExtractsIdCorrectly() {
        // Proposed fix regex - match everything before first '?'
        String fixedRegex = endpoint + "schemas/ids/([^?]+).*";
        
        String pathWithSingleDigit = "/schemaRegistry/schemas/ids/1?fetchMaxId=false&subject=order-purchase-topic-value";
        String pathWithMultiDigit = "/schemaRegistry/schemas/ids/10?fetchMaxId=false&subject=order-purchase-topic-value";
        String pathWithoutQuery = "/schemaRegistry/schemas/ids/5";
        String pathWithComplexId = "/schemaRegistry/schemas/ids/12345?param1=value1&param2=value2";
        
        // Test proposed fix
        String extractedSingle = pathWithSingleDigit.replaceAll(fixedRegex, "$1");
        String extractedMulti = pathWithMultiDigit.replaceAll(fixedRegex, "$1");
        String extractedNoQuery = pathWithoutQuery.replaceAll(fixedRegex, "$1");
        String extractedComplex = pathWithComplexId.replaceAll(fixedRegex, "$1");
        
        System.out.println("Fixed regex results:");
        System.out.println("Single digit: '" + extractedSingle + "'");
        System.out.println("Multi digit: '" + extractedMulti + "'");
        System.out.println("No query: '" + extractedNoQuery + "'");
        System.out.println("Complex ID: '" + extractedComplex + "'");
        
        // All should be parseable as integers
        assertEquals(1, Integer.parseInt(extractedSingle));
        assertEquals(10, Integer.parseInt(extractedMulti));
        assertEquals(5, Integer.parseInt(extractedNoQuery));
        assertEquals(12345, Integer.parseInt(extractedComplex));
    }
    
    @Test
    @DisplayName("Edge cases - should handle various path formats")
    void testEdgeCases() {
        String fixedRegex = endpoint + "schemas/ids/([^?]+).*";
        
        // Test with different query parameter patterns
        String[] testPaths = {
            "/schemaRegistry/schemas/ids/999",                                    // No query params
            "/schemaRegistry/schemas/ids/111?",                                   // Empty query params  
            "/schemaRegistry/schemas/ids/222?single=param",                       // Single param
            "/schemaRegistry/schemas/ids/333?a=1&b=2&c=3",                      // Multiple params
            "/schemaRegistry/schemas/ids/444?encoded=value%20with%20spaces",     // Encoded params
        };
        
        int[] expectedIds = {999, 111, 222, 333, 444};
        
        for (int i = 0; i < testPaths.length; i++) {
            String extracted = testPaths[i].replaceAll(fixedRegex, "$1");
            assertEquals(expectedIds[i], Integer.parseInt(extracted), 
                "Failed to extract ID from: " + testPaths[i]);
        }
    }
}