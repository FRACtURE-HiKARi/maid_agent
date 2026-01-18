package com.github.fracture_hikari.maid_agent.ai.service.function;

import mezz.jei.api.runtime.IJeiRuntime;

import java.util.Optional;

/**
 * Static holder for the JEI runtime instance.
 * JEI provides the runtime via IModPlugin.onRuntimeAvailable(),
 * so we capture and store it here for use by function calls.
 */
public class JeiRuntimeHolder {
    private static IJeiRuntime jeiRuntime;

    public static void setRuntime(IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    public static Optional<IJeiRuntime> getRuntime() {
        return Optional.ofNullable(jeiRuntime);
    }

    public static boolean isAvailable() {
        return jeiRuntime != null;
    }
}
