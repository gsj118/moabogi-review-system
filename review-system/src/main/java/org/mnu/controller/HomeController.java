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

/**
 * @brief 로그인 회원의 월간 요약·최근 기록과 간단한 서버 상태 응답을 제공하는 컨트롤러입니다.
 */
@Controller
public class HomeController {
    /**
     * @brief 회원 통계 보고서를 생성하는 서비스입니다.
     */
    private final StatsService stats;
    /**
     * @brief 서버 날짜와 회원별 최근 기록을 조회하는 서비스입니다.
     */
    private final RecordService records;
    /**
     * @brief 대시보드에 필요한 통계·감상 기록 서비스를 주입받습니다.
     * @param stats 통계 서비스
     * @param records 감상 기록 서비스
     */
    public HomeController(StatsService stats,RecordService records){this.stats=stats;this.records=records;}
    /**
     * @brief 이달 1일부터 오늘까지의 통계와 전체 기간의 최근 기록을 화면에 전달합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param model 화면에 전달할 모델
     * @return home 뷰 이름
     */
    @GetMapping("/")
    public String home(Authentication auth,Model model) {
        QueryFilter q=new QueryFilter();q.setFrom(records.today().withDayOfMonth(1).toString());q.setTo(records.today().toString());
        model.addAttribute("report",stats.report(Current.id(auth),q));
        model.addAttribute("recent",records.list(Current.id(auth),new QueryFilter()));
        model.addAttribute("filter",q);model.addAttribute("pageTitle","나의 감상 공간");
        return "home";
    }

    /**
     * @brief Spring·Java 버전을 포함한 간단한 텍스트 상태 응답을 반환합니다.
     * @return OK와 실행 중인 프레임워크·Java 버전 문자열
     */
    @GetMapping(value = "/health", produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public String health() {
        return "OK - Spring " + SpringVersion.getVersion() + ", Java " + System.getProperty("java.version");
    }
}
