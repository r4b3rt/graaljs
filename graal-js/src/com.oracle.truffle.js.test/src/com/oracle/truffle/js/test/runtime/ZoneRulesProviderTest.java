/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.test.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.runtime.JSContextOptions;
import com.oracle.truffle.js.test.JSTest;
import org.graalvm.polyglot.Context;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests of {@code js.zone-rules-based-time-zones} option.
 */
public class ZoneRulesProviderTest {

    @Before
    public void setUp() {
        String provider = System.getProperty("java.time.zone.DefaultZoneRulesProvider");
        assumeTrue(SimpleZoneRulesProvider.class.getName().equals(provider));
    }

    @Test
    public void testNoIntl() {
        try (Context context = newContext(false)) {
            context.eval(JavaScriptLanguage.ID, "date = new Date(Date.UTC(2020, 3, 25, 12, 13, 14))");
            // SimpleZoneRulesProvider uses +7 offset for all time-zones
            assertResult(context, "date.toString()", "Sat Apr 25 2020 19:13:14 GMT+0700 (CET)");
            assertResult(context, "date.toLocaleString()", "Sat Apr 25 2020 19:13:14 GMT+0700 (CET)");
            assertResult(context, "date.toTimeString()", "19:13:14 GMT+0700 (CET)");
            assertResult(context, "date.toLocaleTimeString()", "19:13:14");
        }
    }

    @Test
    public void testIntl() {
        try (Context context = newContext(true)) {
            context.eval(JavaScriptLanguage.ID, "date = new Date(Date.UTC(2020, 3, 25, 12, 13, 14))");
            // SimpleZoneRulesProvider uses +7 offset for all time-zones
            assertResult(context, "date.toLocaleString()", "4/25/2020, 7:13:14\u202fPM");
            assertResult(context, "date.toLocaleTimeString()", "7:13:14\u202fPM");
            assertResult(context, "new Intl.DateTimeFormat('en', { dateStyle: 'long', timeStyle: 'long' }).format(date)", "April 25, 2020 at 7:13:14\u202fPM GMT+7");
        }
    }

    private static Context newContext(boolean intl) {
        return JSTest.newContextBuilder().//
                        option(JSContextOptions.LOCALE_NAME, "en").//
                        option(JSContextOptions.TIME_ZONE_NAME, "Europe/Prague").//
                        option(JSContextOptions.INTL_402_NAME, Boolean.toString(intl)).//
                        option(JSContextOptions.ZONE_RULES_BASED_TIME_ZONES_NAME, "true").//
                        build();
    }

    private static void assertResult(Context context, String code, String expectedResult) {
        String actualResult = context.eval(JavaScriptLanguage.ID, code).toString();
        assertEquals(expectedResult, actualResult);
    }

}
