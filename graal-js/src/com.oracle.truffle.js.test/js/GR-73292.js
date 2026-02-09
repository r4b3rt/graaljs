/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */

/**
 * Test of the consistency of String.prototype.toUpper/LowerCase
 * and String.prototype.toLocaleUpper/LowerCase
 * 
 * @locale en
 */

load('./assert.js');

for (var codePoint = 0; codePoint <= 0x10FFFF; codePoint++) {
    var s = String.fromCodePoint(codePoint);
    assertSame(s.toUpperCase(), s.toLocaleUpperCase());
    assertSame(s.toLowerCase(), s.toLocaleLowerCase());
}
