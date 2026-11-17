package org.mnu.controller;
import org.mnu.domain.*;
import org.mnu.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
/**
 * @brief 회원별 통계 화면과 조회 조건 오류 복구를 담당하는 컨트롤러입니다.
 */
@Controller
public class StatsController {
    /**
     * @brief 개인 활동·평점·장르 보고서를 생성하는 서비스입니다.
     */
    private final StatsService stats;
    /**
     * @brief 조회 조건의 공용 장르 목록을 제공하는 서비스입니다.
     */
    private final WorkService works;
    /**
     * @brief 통계 및 공용 장르 조회 서비스를 주입받습니다.
     * @param stats 회원 통계 서비스
     * @param works 작품·장르 서비스
     */
    public StatsController(StatsService stats,WorkService works){this.stats=stats;this.works=works;}
    /**
     * @brief 인증 회원의 조건별 보고서를 조회하고 입력 오류에는 같은 조건 폼과 HTTP 상태를 유지합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param q 기간·작품·장르 등 통계 조회 조건
     * @param binding 폼 또는 조회 조건의 바인딩 결과
     * @param model 화면에 전달할 모델
     * @param response 오류 HTTP 상태를 설정할 응답
     * @return stats 뷰 이름
     */
    @GetMapping("/stats") public String stats(Authentication auth,@ModelAttribute("filter") QueryFilter q,BindingResult binding,Model model,HttpServletResponse response){
        model.addAttribute("genres",works.genres());model.addAttribute("pageTitle","기록으로 보는 나의 취향");
        try{if(binding.hasErrors())throw new InputError("통계 조회 조건을 확인하고 다시 조회하세요.");model.addAttribute("report",stats.report(Current.id(auth),q));}
        catch(InputError e){model.addAttribute("message",e.getMessage()+" 조건을 수정한 뒤 다시 조회하세요.");response.setStatus(e.getStatus());}
        return "stats";
    }
}
