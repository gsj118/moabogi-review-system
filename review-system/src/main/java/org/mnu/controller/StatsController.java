package org.mnu.controller;
import org.mnu.domain.*;
import org.mnu.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
@Controller
public class StatsController {
    private final StatsService stats;private final WorkService works;
    public StatsController(StatsService stats,WorkService works){this.stats=stats;this.works=works;}
    @GetMapping("/stats") public String stats(Authentication auth,@ModelAttribute("filter") QueryFilter q,BindingResult binding,Model model,HttpServletResponse response){
        model.addAttribute("genres",works.genres());model.addAttribute("pageTitle","기록으로 보는 나의 취향");
        try{if(binding.hasErrors())throw new InputError("통계 조회 조건을 확인하고 다시 조회하세요.");model.addAttribute("report",stats.report(Current.id(auth),q));}
        catch(InputError e){model.addAttribute("message",e.getMessage()+" 조건을 수정한 뒤 다시 조회하세요.");response.setStatus(e.getStatus());}
        return "stats";
    }
}
