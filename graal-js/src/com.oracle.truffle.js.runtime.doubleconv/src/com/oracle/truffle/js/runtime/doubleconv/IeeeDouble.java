/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

// Copyright 2010 the V8 project authors. All rights reserved.
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
//       copyright notice, this list of conditions and the following
//       disclaimer in the documentation and/or other materials provided
//       with the distribution.
//     * Neither the name of Google Inc. nor the names of its
//       contributors may be used to endorse or promote products derived
//       from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.oracle.truffle.js.runtime.doubleconv;

// @formatter:off

// Helper functions for doubles.
@SuppressWarnings("all")
final class IeeeDouble {

    // We assume that doubles and long have the same endianness.
    static long doubleToLong(final double d)   { return Double.doubleToRawLongBits(d); }
    static double longToDouble(final long d64) { return Double.longBitsToDouble(d64); }

    static final long kSignMask = 0x8000000000000000L;
    static final long kExponentMask = 0x7FF0000000000000L;
    static final long kSignificandMask = 0x000FFFFFFFFFFFFFL;
    static final long kHiddenBit = 0x0010000000000000L;
    static final int kPhysicalSignificandSize = 52;  // Excludes the hidden bit.
    static final int kSignificandSize = 53;

    static private final int kExponentBias = 0x3FF + kPhysicalSignificandSize;
    static private final int kDenormalExponent = -kExponentBias + 1;
    static private final int kMaxExponent = 0x7FF - kExponentBias;
    static private final long kInfinity = 0x7FF0000000000000L;
    static private final long kNaN = 0x7FF8000000000000L;

    static DiyFp asDiyFp(final long d64) {
        assert (!isSpecial(d64));
        return new DiyFp(significand(d64), exponent(d64));
    }

    // The value encoded by this Double must be strictly greater than 0.
    static DiyFp asNormalizedDiyFp(final long d64) {
        assert (value(d64) > 0.0);
        long f = significand(d64);
        int e = exponent(d64);

        // The current double could be a denormal.
        while ((f & kHiddenBit) == 0) {
            f <<= 1;
            e--;
        }
        // Do the final shifts in one go.
        f <<= DiyFp.kSignificandSize - kSignificandSize;
        e -= DiyFp.kSignificandSize - kSignificandSize;

        return new DiyFp(f, e);
    }

    // Returns the next greater double. Returns +infinity on input +infinity.
    static double nextDouble(final long d64) {
        if (d64 == kInfinity) return longToDouble(kInfinity);
        if (sign(d64) < 0 && significand(d64) == 0) {
            // -0.0
            return 0.0;
        }
        if (sign(d64) < 0) {
            return longToDouble(d64 - 1);
        } else {
            return longToDouble(d64 + 1);
        }
    }

    static double previousDouble(final long d64) {
        if (d64 == (kInfinity | kSignMask)) return -longToDouble(kInfinity);
        if (sign(d64) < 0) {
            return longToDouble(d64 + 1);
        } else {
            if (significand(d64) == 0) return -0.0;
            return longToDouble(d64 - 1);
        }
    }

    static int exponent(final long d64) {
        if (isDenormal(d64)) return kDenormalExponent;

        final int biased_e = (int) ((d64 & kExponentMask) >>> kPhysicalSignificandSize);
        return biased_e - kExponentBias;
    }

    static long significand(final long d64) {
        final long significand = d64 & kSignificandMask;
        if (!isDenormal(d64)) {
            return significand + kHiddenBit;
        } else {
            return significand;
        }
    }

    // Returns true if the double is a denormal.
    static boolean isDenormal(final long d64) {
        return (d64 & kExponentMask) == 0L;
    }

    // We consider denormals not to be special.
    // Hence only Infinity and NaN are special.
    static boolean isSpecial(final long d64) {
        return (d64 & kExponentMask) == kExponentMask;
    }

    static boolean isNaN(final long d64) {
        return ((d64 & kExponentMask) == kExponentMask) &&
                ((d64 & kSignificandMask) != 0L);
    }


    static boolean isInfinite(final long d64) {
        return ((d64 & kExponentMask) == kExponentMask) &&
                ((d64 & kSignificandMask) == 0L);
    }


    static int sign(final long d64) {
        return (d64 & kSignMask) == 0L ? 1 : -1;
    }


    // Computes the two boundaries of this.
    // The bigger boundary (m_plus) is normalized. The lower boundary has the same
    // exponent as m_plus.
    // Precondition: the value encoded by this Double must be greater than 0.
    static void normalizedBoundaries(final long d64, final DiyFp m_minus, final DiyFp m_plus) {
        assert (value(d64) > 0.0);
        final DiyFp v = asDiyFp(d64);
        m_plus.setF((v.f() << 1) + 1);
        m_plus.setE(v.e() - 1);
        m_plus.normalize();
        if (lowerBoundaryIsCloser(d64)) {
            m_minus.setF((v.f() << 2) - 1);
            m_minus.setE(v.e() - 2);
        } else {
            m_minus.setF((v.f() << 1) - 1);
            m_minus.setE(v.e() - 1);
        }
        m_minus.setF(m_minus.f() << (m_minus.e() - m_plus.e()));
        m_minus.setE(m_plus.e());
    }

    static boolean lowerBoundaryIsCloser(final long d64) {
        // The boundary is closer if the significand is of the form f == 2^p-1 then
        // the lower boundary is closer.
        // Think of v = 1000e10 and v- = 9999e9.
        // Then the boundary (== (v - v-)/2) is not just at a distance of 1e9 but
        // at a distance of 1e8.
        // The only exception is for the smallest normal: the largest denormal is
        // at the same distance as its successor.
        // Note: denormals have the same exponent as the smallest normals.
        final boolean physical_significand_is_zero = ((d64 & kSignificandMask) == 0);
        return physical_significand_is_zero && (exponent(d64) != kDenormalExponent);
    }

    static double value(final long d64) {
        return longToDouble(d64);
    }

    // Returns the significand size for a given order of magnitude.
    // If v = f*2^e with 2^p-1 <= f <= 2^p then p+e is v's order of magnitude.
    // This function returns the number of significant binary digits v will have
    // once it's encoded into a double. In almost all cases this is equal to
    // kSignificandSize. The only exceptions are denormals. They start with
    // leading zeroes and their effective significand-size is hence smaller.
    static int significandSizeForOrderOfMagnitude(final int order) {
        if (order >= (kDenormalExponent + kSignificandSize)) {
            return kSignificandSize;
        }
        if (order <= kDenormalExponent) return 0;
        return order - kDenormalExponent;
    }

    static double Infinity() {
        return longToDouble(kInfinity);
    }

    static double NaN() {
        return longToDouble(kNaN);
    }

    private IeeeDouble() {
    }
}
