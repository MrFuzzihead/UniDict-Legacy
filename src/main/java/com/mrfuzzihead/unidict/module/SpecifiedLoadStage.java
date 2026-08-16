package com.mrfuzzihead.unidict.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.mrfuzzihead.unidict.LoadStage;

/**
 * Declares the {@link LoadStage} a module thread should run at. Defaults to {@link LoadStage#POST_INIT}
 * when absent. Ported unchanged from {@code wanion.unidict.module.SpecifiedLoadStage} (WanionCane, MPL-2.0).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SpecifiedLoadStage {

    LoadStage stage();
}
