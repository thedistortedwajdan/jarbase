package dev.wajdan.jarbase.core.agent;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/** Finds the installed {@link AgentAdapter}s. Adding an agent never requires changing this class. */
public final class AgentAdapters {

    private AgentAdapters() {
    }

    public static List<AgentAdapter> all() {
        return ServiceLoader.load(AgentAdapter.class).stream().map(ServiceLoader.Provider::get).toList();
    }

    public static Optional<AgentAdapter> find(String id) {
        return all().stream().filter(adapter -> adapter.id().equals(id)).findFirst();
    }

    public static List<String> ids() {
        return all().stream().map(AgentAdapter::id).sorted().toList();
    }
}
