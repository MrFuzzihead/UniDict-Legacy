package com.mrfuzzihead.unidict.module;

import java.util.ArrayList;
import java.util.List;

import com.mrfuzzihead.unidict.LoadStage;

/**
 * Holds the mod's modules in registration order and drives them through FML load stages
 * sequentially. Ported from {@code wanion.unidict.module.ModuleHandler} (WanionCane, MPL-2.0) with
 * the {@code THashMap}-of-nested-{@code Manager} indirection collapsed: {@link #addModule} keeps an
 * ordered {@link List}, and {@link #startModules} lazily {@code init()}s each module once, after
 * which every stage is dispatched to the module's sequential {@link LoadStageExecutor}.
 */
public final class ModuleHandler {

    private final List<AbstractModule> modules = new ArrayList<>();

    /** Registers a module, ignoring {@code null} and duplicates, preserving call order. */
    public void addModule(final AbstractModule module) {
        if (module != null && !modules.contains(module)) modules.add(module);
    }

    public boolean isEmpty() {
        return modules.isEmpty();
    }

    /** Lazily initializes modules that are not yet initialized, then runs them at the given stage. */
    public void startModules(final LoadStage loadStage) {
        if (loadStage == null || modules.isEmpty()) return;
        for (final AbstractModule module : modules) {
            if (module.isEmpty()) module.init();
            module.start(loadStage);
        }
    }
}
