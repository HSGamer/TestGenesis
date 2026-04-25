package me.hsgamer.testgenesis.agent.junit;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DependencyResolver {
    private static final Logger logger = LoggerFactory.getLogger(DependencyResolver.class);

    private final Map<String, List<File>> cache = new ConcurrentHashMap<>();

    public List<File> resolve(List<String> coordinates) {
        if (coordinates.isEmpty()) {
            return List.of();
        }

        List<File> cached = new ArrayList<>();
        List<String> uncached = new ArrayList<>();

        for (String coord : coordinates) {
            List<File> hit = cache.get(coord);
            if (hit != null) {
                cached.addAll(hit);
            } else {
                uncached.add(coord);
            }
        }

        if (uncached.isEmpty()) {
            return deduplicate(cached);
        }

        logger.info("Resolving {} uncached dependencies: {}", uncached.size(), uncached);
        File[] resolved = Maven.resolver()
                .resolve(uncached)
                .withTransitivity()
                .asFile();

        List<File> resolvedList = Arrays.asList(resolved);
        for (String coord : uncached) {
            cache.put(coord, resolvedList);
        }

        List<File> all = new ArrayList<>(cached);
        all.addAll(resolvedList);
        return deduplicate(all);
    }

    private List<File> deduplicate(List<File> files) {
        Set<String> seen = new LinkedHashSet<>();
        List<File> result = new ArrayList<>();
        for (File f : files) {
            if (seen.add(f.getAbsolutePath())) {
                result.add(f);
            }
        }
        return result;
    }
}
