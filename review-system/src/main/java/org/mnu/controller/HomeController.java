package org.mnu.controller;

import org.springframework.core.SpringVersion;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.mnu.domain.QueryFilter;
import org.mnu.service.RecordService;
import org.mnu.service.StatsService;
import org.springframework.security.core.Authentication;

@Controller
public class HomeController {
    private final StatsService stats;
    private final RecordService records;
    public HomeController(StatsService stats,RecordService records){this.stats=stats;this.records=records;}
    @GetMapping("/")
    public String home(Authentication auth,Model model) {
        QueryFilter q=new QueryFilter();q.setFrom(records.today().withDayOfMonth(1).toString());q.setTo(records.today().toString());
        model.addAttribute("report",stats.report(Current.id(auth),q));
        model.addAttribute("recent",records.list(Current.id(auth),new QueryFilter()));
        model.addAttribute("filter",q);model.addAttribute("pageTitle","나의 감상 공간");
        return "home";
    }

    @GetMapping(value = "/health", produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public String health() {
        return "OK - Spring " + SpringVersion.getVersion() + ", Java " + System.getProperty("java.version");
    }
}
