package me.hsgamer.testgenesis.cms.controller;

import me.hsgamer.testgenesis.cms.entity.Payload;
import me.hsgamer.testgenesis.cms.entity.TestProject;
import me.hsgamer.testgenesis.cms.repository.PayloadRepository;
import me.hsgamer.testgenesis.cms.repository.TestProjectRepository;
import me.hsgamer.testgenesis.cms.viewmodel.TestEditViewModel;
import me.hsgamer.testgenesis.cms.viewmodel.TestListViewModel;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/tests")
public class TestProjectController {

    private final TestProjectRepository testRepo;
    private final PayloadRepository payloadRepo;

    public TestProjectController(TestProjectRepository testRepo, PayloadRepository payloadRepo) {
        this.testRepo = testRepo;
        this.payloadRepo = payloadRepo;
    }

    @GetMapping
    public String listTests(Model model) {
        List<TestListViewModel.TestView> testViews = testRepo.findAll().stream()
                .map(entity -> new TestListViewModel.TestView(
                        entity.getId(),
                        entity.getName(),
                        entity.getTestType(),
                        entity.getPayloadCount()
                ))
                .toList();
        model.addAttribute("tests", testViews);
        return "tests";
    }

    @GetMapping("/new")
    public String newTest(Model model) {
        List<TestEditViewModel.PayloadOption> payloads = payloadRepo.findAll().stream()
                .map(p -> new TestEditViewModel.PayloadOption(p.getId(), p.getPayloadType(), p.getPayloadType(), false))
                .toList();
        model.addAttribute("test", new TestEditViewModel.TestView(null, "", ""));
        model.addAttribute("availablePayloads", payloads);
        model.addAttribute("isEdit", false);
        return "test_edit";
    }

    @GetMapping("/edit/{id}")
    public String editTest(@PathVariable Long id, Model model) {
        TestProject test = testRepo.findById(id).orElseThrow();
        List<Long> selectedPayloadIds = test.getPayloads().stream().map(Payload::getId).toList();

        List<TestEditViewModel.PayloadOption> payloads = payloadRepo.findAll().stream()
                .map(p -> new TestEditViewModel.PayloadOption(
                        p.getId(),
                        p.getPayloadType(),
                        p.getPayloadType(),
                        selectedPayloadIds.contains(p.getId())))
                .toList();

        TestEditViewModel.TestView testView = new TestEditViewModel.TestView(
                test.getId(),
                test.getName(),
                test.getTestType()
        );

        model.addAttribute("test", testView);
        model.addAttribute("availablePayloads", payloads);
        model.addAttribute("isEdit", true);
        return "test_edit";
    }

    @PostMapping("/save")
    public String saveTest(@RequestParam(required = false) Long id,
                           @RequestParam String name,
                           @RequestParam String testType,
                           @RequestParam(required = false) List<Long> payloadIds) {
        TestProject test;
        if (id != null) {
            test = testRepo.findById(id).orElse(new TestProject());
        } else {
            test = new TestProject();
        }

        test.setName(name);
        test.setTestType(testType);

        List<Payload> payloads = new ArrayList<>();
        if (payloadIds != null) {
            for (Long pId : payloadIds) {
                payloadRepo.findById(pId).ifPresent(payloads::add);
            }
        }
        test.setPayloads(payloads);

        testRepo.save(test);
        return "redirect:/tests";
    }

    @PostMapping("/delete/{id}")
    public String deleteTest(@PathVariable Long id) {
        testRepo.deleteById(id);
        return "redirect:/tests";
    }
}
