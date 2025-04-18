/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.js.runtime.array.dyn;

import static com.oracle.truffle.js.runtime.builtins.JSAbstractArray.arrayGetArray;

import java.util.List;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.js.builtins.helper.ListGetNode;
import com.oracle.truffle.js.runtime.Boundaries;
import com.oracle.truffle.js.runtime.JSConfig;
import com.oracle.truffle.js.runtime.array.DynamicArray;
import com.oracle.truffle.js.runtime.objects.JSDynamicObject;

/**
 * LazyArray is a backing class for a JSArray that allows array elements to be supplied on demand
 * (i.e., lazily) from a list generator.
 *
 * Whenever a lazy array is written to, the entire lazy array is enumerated and copied and loses its
 * lazy lookup property.
 */
public class LazyArray extends AbstractConstantLazyArray {

    private static final LazyArray LAZY_ARRAY = new LazyArray(INTEGRITY_LEVEL_NONE, createCache()).maybePreinitializeCache();

    protected LazyArray(int integrityLevel, DynamicArrayCache cache) {
        super(integrityLevel, cache);
    }

    public static LazyArray createLazyArray() {
        return LAZY_ARRAY;
    }

    @Override
    protected DynamicArray withIntegrityLevel(int newIntegrityLevel) {
        return new LazyArray(newIntegrityLevel, cache);
    }

    private static List<?> arrayGetLazyList(JSDynamicObject object) {
        return (List<?>) arrayGetArray(object);
    }

    @Override
    public Object getElementInBounds(JSDynamicObject object, int index) {
        return Boundaries.listGet(arrayGetLazyList(object), index);
    }

    public Object getElementInBounds(JSDynamicObject object, int index, ListGetNode listGetNode) {
        return listGetNode.execute(arrayGetArray(object), index);
    }

    @Override
    public AbstractWritableArray createWriteableObject(JSDynamicObject object, long index, Object value, Node node, CreateWritableProfileAccess profile) {
        // enumerate the whole array
        int len = lengthInt(object);
        Object[] array = new Object[len];
        for (int i = 0; i < len; i++) {
            array[i] = getElementInBounds(object, i);
        }
        AbstractObjectArray newArray;
        newArray = ZeroBasedObjectArray.makeZeroBasedObjectArray(object, array.length, array.length, array, integrityLevel);
        if (JSConfig.TraceArrayTransitions) {
            traceArrayTransition(this, newArray, index, value);
        }
        return newArray;
    }

    @Override
    public Object cloneArray(JSDynamicObject object) {
        return arrayGetLazyList(object);
    }
}
