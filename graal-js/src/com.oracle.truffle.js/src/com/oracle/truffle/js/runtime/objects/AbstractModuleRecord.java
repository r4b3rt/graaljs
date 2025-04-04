/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.runtime.objects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.js.parser.ir.Module.ImportPhase;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.builtins.JSModuleNamespace;
import com.oracle.truffle.js.runtime.builtins.JSModuleNamespaceObject;
import com.oracle.truffle.js.runtime.builtins.JSPromise;
import com.oracle.truffle.js.runtime.builtins.JSPromiseObject;
import com.oracle.truffle.js.runtime.util.Pair;

/**
 * Abstract Module Record.
 */
public abstract class AbstractModuleRecord extends ScriptOrModule {

    /** Lazily initialized Module Namespace object ({@code [[Namespace]]}). */
    private JSModuleNamespaceObject namespace;
    /** Lazily initialized Deferred Module Namespace object ({@code [[DeferredNamespace]]}). */
    private JSModuleNamespaceObject deferredNamespace;
    /** Lazily initialized frame ({@code [[Environment]]}). */
    private MaterializedFrame environment;
    private FrameDescriptor frameDescriptor;

    // [HostDefined]
    private Object hostDefined;

    protected AbstractModuleRecord(JSContext context, Source source, Object hostDefined, FrameDescriptor frameDescriptor) {
        super(context, source);
        this.frameDescriptor = frameDescriptor;
        this.hostDefined = hostDefined;
    }

    /**
     * Prepares the module for linking by recursively loading all its dependencies.
     */
    public abstract JSPromiseObject loadRequestedModules(JSRealm realm, Object hostDefinedArg);

    public final void loadRequestedModulesSync(JSRealm realm, Object hostDefinedArg) {
        JSPromiseObject loadPromise = loadRequestedModules(realm, hostDefinedArg);
        assert !JSPromise.isPending(loadPromise);
        JSPromise.throwIfRejected(loadPromise, realm);
    }

    /**
     * Prepare the module for evaluation by transitively resolving all module dependencies and
     * creating a Module Environment Record.
     *
     * LoadRequestedModules must have completed successfully prior to invoking this method.
     */
    public abstract void link(JSRealm realm);

    /**
     * Returns a promise for the evaluation of this module and its dependencies, resolving on
     * successful evaluation or if it has already been evaluated successfully, and rejecting for an
     * evaluation error or if it has already been evaluated unsuccessfully.
     *
     * Link must have completed successfully prior to invoking this method.
     */
    public abstract JSPromiseObject evaluate(JSRealm realm);

    /*
     * This method can be used for non-{@link CyclicModuleRecord}s, for which {@link #evaluate}
     * always returns a settled promise, so we can throw the evaluation error immediately, if any.
     * Should not be used for {@link CyclicModuleRecord}s, since they may require async execution.
     *
     * May be overridden by synthetic module records to skip the unnecessary promise creation.
     */
    @TruffleBoundary
    public void evaluateSync(JSRealm realm) {
        assert !(this instanceof CyclicModuleRecord cyclicModule) || cyclicModule.isReadyForSyncExecution() : this;
        JSPromiseObject loadPromise = evaluate(realm);
        assert !JSPromise.isPending(loadPromise);
        JSPromise.throwIfRejected(loadPromise, realm);
    }

    @TruffleBoundary
    public final Collection<TruffleString> getExportedNames() {
        return getExportedNames(new HashSet<>());
    }

    public abstract Collection<TruffleString> getExportedNames(Set<JSModuleRecord> exportStarSet);

    @TruffleBoundary
    public final ExportResolution resolveExport(TruffleString exportName) {
        return resolveExport(exportName, new HashSet<>());
    }

    public abstract ExportResolution resolveExport(TruffleString exportName, Set<Pair<? extends AbstractModuleRecord, TruffleString>> resolveSet);

    public final JSModuleNamespaceObject getModuleNamespaceOrNull(ImportPhase phase) {
        assert phase == ImportPhase.Evaluation || phase == ImportPhase.Defer : phase;
        return getModuleNamespaceOrNull(phase == ImportPhase.Defer);
    }

    public final JSModuleNamespaceObject getModuleNamespace(ImportPhase phase) {
        assert phase == ImportPhase.Evaluation || phase == ImportPhase.Defer : phase;
        return getModuleNamespace(phase == ImportPhase.Defer);
    }

    public final JSModuleNamespaceObject getModuleNamespaceOrNull(boolean deferred) {
        if (deferred) {
            return deferredNamespace;
        } else {
            return namespace;
        }
    }

    @TruffleBoundary
    public final JSModuleNamespaceObject getModuleNamespace(boolean deferred) {
        JSModuleNamespaceObject ns = getModuleNamespaceOrNull(deferred);
        if (ns != null) {
            return ns;
        }
        assert (!(this instanceof CyclicModuleRecord cyclicModule) || (cyclicModule.getStatus() != CyclicModuleRecord.Status.New)) : this;
        Collection<TruffleString> exportedNames = getExportedNames();
        List<Map.Entry<TruffleString, ExportResolution>> unambiguousNames = new ArrayList<>();
        for (TruffleString exportedName : exportedNames) {
            ExportResolution resolution = resolveExport(exportedName);
            if (resolution.isNull()) {
                throw Errors.createSyntaxError("Could not resolve export");
            } else if (!resolution.isAmbiguous()) {
                unambiguousNames.add(Map.entry(exportedName, resolution));
            }
        }
        unambiguousNames.sort((a, b) -> a.getKey().compareCharsUTF16Uncached(b.getKey()));
        ns = JSModuleNamespace.create(getContext(), JSRealm.get(null), this, unambiguousNames, deferred);
        if (deferred) {
            this.deferredNamespace = ns;
        } else {
            this.namespace = ns;
        }
        return ns;
    }

    public abstract Object getModuleSource();

    public final MaterializedFrame getEnvironment() {
        return environment;
    }

    public final void setEnvironment(MaterializedFrame environment) {
        assert this.environment == null;
        this.environment = environment;
    }

    protected final void clearEnvironment() {
        this.environment = null;
    }

    public final FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    protected final void setFrameDescriptor(FrameDescriptor frameDescriptor) {
        this.frameDescriptor = frameDescriptor;
    }

    public final Object getHostDefined() {
        return this.hostDefined;
    }

    /**
     * Collects the direct post-order list of asynchronous unexecuted transitive dependencies,
     * stopping the depth-first search for a branch when an asynchronous dependency is found.
     */
    @TruffleBoundary
    public List<AbstractModuleRecord> gatherAsynchronousTransitiveDependencies() {
        return gatherAsynchronousTransitiveDependencies(new ArrayList<>(), new HashSet<>());
    }

    private List<AbstractModuleRecord> gatherAsynchronousTransitiveDependencies(List<AbstractModuleRecord> result, Set<AbstractModuleRecord> seen) {
        CompilerAsserts.neverPartOfCompilation();
        if (!seen.add(this)) {
            return result;
        }
        if (this instanceof CyclicModuleRecord cyclicModule) {
            if (cyclicModule.getStatus() == CyclicModuleRecord.Status.Evaluating || cyclicModule.getStatus() == CyclicModuleRecord.Status.Evaluated) {
                return result;
            } else if (cyclicModule.hasTLA()) {
                result.add(cyclicModule);
                return result;
            } else {
                for (var request : cyclicModule.getRequestedModules()) {
                    var requiredModule = cyclicModule.getImportedModule(request);
                    requiredModule.gatherAsynchronousTransitiveDependencies(result, seen);
                }
            }
        }
        return result;
    }

    public abstract CyclicModuleRecord.Status getStatus();
}
