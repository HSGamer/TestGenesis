package me.hsgamer.testgenesis.cms.web;

import io.jstach.jstache.JStache;
import java.util.List;

/**
 * JStachio view for listing tests.
 * Uses a nested TestView record to provide type-safe data to the template.
 */
@JStache(path = "me/hsgamer/testgenesis/cms/web/tests.mustache")
public record TestListView(
        List<TestView> tests
) {
    /**
     * View-specific representation of a test entity.
     */
    public record TestView(Long id, String name, String testType, int payloadCount) {
    }
}
