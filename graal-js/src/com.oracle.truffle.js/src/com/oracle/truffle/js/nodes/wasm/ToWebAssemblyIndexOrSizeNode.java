/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.nodes.wasm;

import com.oracle.truffle.api.ExactMath;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.cast.JSToNumberNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSRuntime;

/**
 * Performs a conversion of the argument to non-negative {@code int} common to various parts of
 * WebAssembly implementation.
 */
public abstract class ToWebAssemblyIndexOrSizeNode extends JavaScriptBaseNode {
    private final String errorMessagePrefix;
    private final BranchProfile errorBranch;
    @Child JSToNumberNode toNumberNode;

    protected ToWebAssemblyIndexOrSizeNode(String errorMessagePrefix) {
        this.errorMessagePrefix = errorMessagePrefix;
        this.errorBranch = BranchProfile.create();
        this.toNumberNode = JSToNumberNode.create();
    }

    public static ToWebAssemblyIndexOrSizeNode create(String errorMessagePrefix) {
        return ToWebAssemblyIndexOrSizeNodeGen.create(errorMessagePrefix);
    }

    public abstract int executeInt(Object value);

    @Specialization
    protected int convertInt(int intValue) {
        if (intValue < 0) {
            errorBranch.enter();
            throw Errors.createTypeErrorFormat("%s must be non-negative", this, errorMessagePrefix);
        }
        return intValue;
    }

    @Specialization(replaces = "convertInt")
    protected int convert(Object value) {
        Number valueNumber = toNumberNode.executeNumber(value);
        double valueDouble = JSRuntime.doubleValue(valueNumber);
        if (!Double.isFinite(valueDouble)) {
            errorBranch.enter();
            throw Errors.createTypeErrorFormat("%s must be convertible to a valid number", this, errorMessagePrefix);
        }
        valueDouble = ExactMath.truncate(valueDouble);
        if (valueDouble < 0) {
            errorBranch.enter();
            throw Errors.createTypeErrorFormat("%s must be non-negative", this, errorMessagePrefix);
        }
        if (valueDouble > 0xFFFF_FFFFL) {
            errorBranch.enter();
            throw Errors.createTypeErrorFormat("%s must be in the unsigned long range", this, errorMessagePrefix);
        }
        if (valueDouble > Integer.MAX_VALUE) {
            errorBranch.enter();
            throw Errors.createRangeErrorFormat("%s must be in the int range", this, errorMessagePrefix);
        }
        return (int) valueDouble;
    }
}
