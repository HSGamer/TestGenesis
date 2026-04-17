package me.hsgamer.testgenesis.cms.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import me.hsgamer.testgenesis.cms.core.Agent;
import me.hsgamer.testgenesis.cms.dto.AgentInfo;
import me.hsgamer.testgenesis.cms.dto.TestTypeInfo;
import me.hsgamer.testgenesis.cms.dto.TranslationTypeInfo;
import me.hsgamer.testgenesis.uap.v1.Capability;
import me.hsgamer.testgenesis.uap.v1.ListenResponse;
import me.hsgamer.testgenesis.uap.v1.PayloadRequirement;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
@Slf4j
public class AgentManager {
    private final Map<String, AgentImpl> agents = new ConcurrentHashMap<>();

    public Collection<Agent> getAgents() {
        return Collections.unmodifiableCollection(agents.values());
    }

    public Optional<Agent> getAgent(String id) {
        return Optional.ofNullable(agents.get(id));
    }

    public String registerAgent(String displayName, List<Capability> capabilities) {
        String id = UUID.randomUUID().toString();
        agents.put(id, new AgentImpl(id, displayName, capabilities));
        log.info("Agent registered: {} ({})", id, displayName);
        return id;
    }

    public void setAgentDispatcher(String id, Consumer<ListenResponse> dispatcher) {
        Optional.ofNullable(agents.get(id)).ifPresent(a -> a.setDispatcher(dispatcher));
    }

    public Agent removeAgent(String id) {
        return agents.remove(id);
    }

    public List<AgentInfo> getAgentInfos() {
        return agents.values().stream().filter(Agent::isReady).map(a -> {
            List<TestTypeInfo> tests = a.capabilities().stream()
                .filter(Capability::hasTest).map(c -> {
                    var t = c.getTest();
                    var req = t.getPayloadsList().stream().filter(PayloadRequirement::getIsRequired).map(PayloadRequirement::getType).toList();
                    var opt = t.getPayloadsList().stream().filter(p -> !p.getIsRequired()).map(PayloadRequirement::getType).toList();
                    return new TestTypeInfo(t.getType(), req, opt);
                }).toList();
            List<TranslationTypeInfo> trans = a.capabilities().stream()
                .filter(Capability::hasTranslation).map(c -> {
                    var t = c.getTranslation();
                    var src = t.getSourcePayloadsList().stream().map(PayloadRequirement::getType).toList();
                    var trg = t.getTargetPayloadsList().stream().map(PayloadRequirement::getType).toList();
                    return new TranslationTypeInfo(t.getType(), src, trg);
                }).toList();
            return new AgentInfo(a.id(), a.displayName(), tests, trans);
        }).toList();
    }

    public Set<String> getAvailablePayloadTypes() {
        return agents.values().stream().filter(Agent::isReady).flatMap(a -> a.capabilities.stream())
            .flatMap(c -> switch (c.getFormatCase()) {
                case TEST -> c.getTest().getPayloadsList().stream().map(PayloadRequirement::getType);
                case TRANSLATION ->
                    Stream.concat(c.getTranslation().getSourcePayloadsList().stream(), c.getTranslation().getTargetPayloadsList().stream()).map(PayloadRequirement::getType);
                default -> Stream.empty();
            }).collect(Collectors.toCollection(TreeSet::new));
    }

    public Map<String, Set<String>> getPayloadMimeTypeMapping() {
        return agents.values().stream().filter(Agent::isReady).flatMap(a -> a.capabilities.stream())
            .flatMap(c -> switch (c.getFormatCase()) {
                case TEST -> c.getTest().getPayloadsList().stream();
                case TRANSLATION ->
                    Stream.concat(c.getTranslation().getSourcePayloadsList().stream(), c.getTranslation().getTargetPayloadsList().stream());
                default -> Stream.empty();
            }).collect(Collectors.groupingBy(PayloadRequirement::getType, Collectors.flatMapping(r -> r.getAcceptedMimeTypesList().stream(), Collectors.toSet())));
    }

    public void sendToAgent(String id, ListenResponse response) {
        Optional.ofNullable(agents.get(id)).ifPresent(a -> a.send(response));
    }

    public static class AgentImpl implements Agent {
        public final Set<String> activeSessions = ConcurrentHashMap.newKeySet();
        final String id, displayName;
        final List<Capability> capabilities;
        private Consumer<ListenResponse> dispatcher;

        AgentImpl(String id, String name, List<Capability> caps) {
            this.id = id;
            this.displayName = name;
            this.capabilities = caps;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String displayName() {
            return displayName;
        }

        @Override
        public List<Capability> capabilities() {
            return capabilities;
        }

        @Override
        public boolean isReady() {
            return dispatcher != null;
        }

        @Override
        public boolean supportsTestType(String type) {
            return capabilities.stream().anyMatch(c -> c.hasTest() && c.getTest().getType().equals(type));
        }

        void setDispatcher(Consumer<ListenResponse> d) {
            this.dispatcher = d;
        }

        void send(ListenResponse r) {
            if (dispatcher != null) dispatcher.accept(r);
        }
    }
}
