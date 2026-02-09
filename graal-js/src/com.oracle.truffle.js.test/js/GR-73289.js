/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */

/**
 * Regression test of the import of the root directory.
 * 
 * @option unhandled-rejections=throw
 */

load('./assert.js');

function verifyError(e) {
    assertTrue(e instanceof Error);
    assertTrue(e.message === 'Is a directory' || e.message.startsWith('Cannot find module '));
}

import('/').catch(e => {
    verifyError(e);
});

// inspired by mjsunit/regress/regress-crbug-422706696.js
import('../'.repeat(50)).catch(e => {
    verifyError(e);
});
