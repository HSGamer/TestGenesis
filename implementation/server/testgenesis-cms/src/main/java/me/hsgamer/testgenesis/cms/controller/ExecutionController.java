package me.hsgamer.testgenesis.cms.controller;

import me.hsgamer.testgenesis.cms.service.TestExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.logging.Logger;

@Controller
public class ExecutionController {

    private final Logger logger = Logger.getLogger(getClass().getName());
    private final TestExecutionService executionManager;

    public ExecutionController(TestExecutionService executionManager) {
        this.executionManager = executionManager;
    }

    @GetMapping("/tests/run/{testId}")
    @ResponseBody
    public String runTestOnFirstAgent(@PathVariable Long testId) {
        // This is a placeholder for triggering execution via the manager
        logger.info("Triggering test " + testId + " on first available agent");
        return "Test " + testId + " execution triggered.";
    }

    @GetMapping("/run-test")
    public Object runTestWithParams(@RequestParam String agentId, @RequestParam Long testId) {
        return executionManager.startTest(agentId, testId).handle((ok, ex) -> {
            if (ex != null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + ex.getMessage());
            } else if (Boolean.TRUE.equals(ok)) {
                return "redirect:/";
            } else {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body("Test queued or agent busy.");
            }
        }).join();
    }
}
