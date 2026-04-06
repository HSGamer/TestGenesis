package me.hsgamer.testgenesis.cms.web;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.jstach.jstachio.JStachio;
import me.hsgamer.testgenesis.cms.db.repository.TestRepository;
import java.util.List;

/**
 * Controller for listing and managing tests. Manually wired.
 */
public class TestController implements HttpService {

    private final TestRepository testRepo;
    private final JStachio jstachio = JStachio.of();

    public TestController(TestRepository testRepo) {
        this.testRepo = testRepo;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/tests", this::listTests);
    }

    private void listTests(ServerRequest request, ServerResponse response) {
        // Helidon Data's findAll() returns a Stream
        List<TestListView.TestView> testViews = testRepo.findAll()
                .map(entity -> new TestListView.TestView(
                        entity.getId(),
                        entity.getName(),
                        entity.getTestType(),
                        entity.getPayloadCount()
                ))
                .toList();
        response.headers().contentType(MediaTypes.TEXT_HTML);
        response.send(jstachio.execute(new TestListView(testViews)));
    }
}
