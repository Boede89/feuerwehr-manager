package de.feuerwehr.manager.web;

import de.feuerwehr.manager.support.BugReportService;
import de.feuerwehr.manager.web.dto.BugReportRequest;
import de.feuerwehr.manager.web.dto.BugReportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/login")
@RequiredArgsConstructor
public class StartPageBugReportController {

    private final BugReportService bugReportService;

    @PostMapping("/bug-report")
    public BugReportResult submit(@RequestBody BugReportRequest request) {
        return bugReportService.submit(request);
    }
}
