/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */

/**
 * Test of the correctness and performance of uppercasing of sharp s.
 */

load('./assert.js');

var eszett = '\u00df';
var doubleS = 'SS';

assertSame(doubleS, eszett.toUpperCase());

var count = 1000000;
assertSame(doubleS.repeat(count), eszett.repeat(count).toUpperCase());
