package me.hsgamer.testgenesis.cms.web;

import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import me.hsgamer.testgenesis.cms.service.TestExecutionService;

import java.util.logging.Logger;

/**
 * Controller for triggering test executions. Manually wired.
 */
public class ExecutionController implements HttpService {

    private final Logger logger = Logger.getLogger(getClass().getName());
    private final TestExecutionService executionManager;

    public ExecutionController(TestExecutionService executionManager) {
        this.executionManager = executionManager;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/tests/run/{testId}", this::runTestOnFirstAgent);
        rules.get("/run-test", this::runTestWithParams);
    }

    private void runTestOnFirstAgent(ServerRequest request, ServerResponse response) {
        Long testId = Long.valueOf(request.path().pathParameters().get("testId"));
        // This is a placeholder for triggering execution via the manager
        logger.info("Triggering test " + testId + " on first available agent");
        response.status(Status.OK_200).send("Test " + testId + " execution triggered.");
    }

    private void runTestWithParams(ServerRequest request, ServerResponse response) {
        String agentId = request.query().get("agentId");
        String testIdStr = request.query().get("testId");
        
        if (agentId == null || testIdStr == null) {
            response.status(Status.BAD_REQUEST_400).send("Missing agentId or testId");
            return;
        }

        Long testId = Long.valueOf(testIdStr);
        executionManager.startTest(agentId, testId).handle((ok, ex) -> {
            if (ex != null) {
                response.status(Status.INTERNAL_SERVER_ERROR_500).send("Error: " + ex.getMessage());
            } else if (Boolean.TRUE.equals(ok)) {
                response.status(Status.SEE_OTHER_303).header("Location", "/").send();
            } else {
                response.status(Status.ACCEPTED_202).send("Test queued or agent busy.");
            }
            return null;
        });
    }
}
