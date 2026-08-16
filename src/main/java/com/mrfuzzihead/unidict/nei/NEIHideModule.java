package com.mrfuzzihead.unidict.nei;

import com.mrfuzzihead.unidict.module.AbstractModule;

/**
 * Client-only NEI module (TODO.md P0 #1+#2): at POST_INIT it walks every unified resource and calls
 * {@code NEIHelper.hide} for each non-kept, non-blacklisted variant, so the player sees one copper
 * ingot/plate/etc. instead of seven. Registered only on a client with NotEnoughItems present (see
 * {@code UniDict.preInit}), which guarantees the NEI/`codechicken` types are never referenced on a
 * dedicated server or an NEI-less client (BB-3 non-destructive: entries are hidden, never removed).
 */
public final class NEIHideModule extends AbstractModule {

    public NEIHideModule() {
        super("NEIHide");
    }

    @Override
    protected void init() {
        executor.add(new NEIHideThread());
    }
}
