package in.medmate.__NAME__;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MessageTest {
    @Test
    void includesTheAppName() {
        assertEquals("Hello from __NAME__", Message.text("__NAME__"));
    }
}
