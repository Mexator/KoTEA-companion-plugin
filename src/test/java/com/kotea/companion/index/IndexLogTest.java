package com.kotea.companion.index;

import junit.framework.TestCase;

public class IndexLogTest extends TestCase {

    public void testMask_shortensPackagesToInitials() {
        assertEquals("c.e.SomeEvent", IndexLog.mask("com.example.SomeEvent"));
        assertEquals("c.e.Outer.Inner", IndexLog.mask("com.example.Outer.Inner"));
    }
}
