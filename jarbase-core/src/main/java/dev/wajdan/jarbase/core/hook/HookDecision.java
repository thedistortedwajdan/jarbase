package dev.wajdan.jarbase.core.hook;

/**
 * What the pre-push check decided. Agent-neutral: each agent adapter turns this into the
 * format that agent understands.
 *
 * @param block  true to stop the push
 * @param reason the message the agent should see when blocked; empty when allowed
 */
public record HookDecision(boolean block, String reason) {

    public static HookDecision allow() {
        return new HookDecision(false, "");
    }

    public static HookDecision block(String reason) {
        return new HookDecision(true, reason);
    }
}
