package org.mnu.controller;

import java.util.*;
import javax.servlet.http.*;
import org.mnu.domain.*;
import org.mnu.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * @brief 인증 회원의 감상 기록 목록·작성·수정·삭제와 오류 복구를 담당하는 컨트롤러입니다.
 */
@Controller
public class RecordController {
    /**
     * @brief 소유권·저장 동작·UUID 재전송·버전 충돌을 처리하는 기록 서비스입니다.
     */
    private final RecordService records;
    /**
     * @brief 감상 대상 공용 작품과 장르를 조회하는 서비스입니다.
     */
    private final WorkService works;
    /**
     * @brief 기록 처리와 공용 작품 조회 서비스를 주입받습니다.
     * @param records 감상 기록 서비스
     * @param works 공용 작품 서비스
     */
    public RecordController(RecordService records,WorkService works){this.records=records;this.works=works;}
    /**
     * @brief 회원별 기록·건수를 조회하고 조건 오류에는 queryError와 HTTP 상태를 표시합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param q 검색·기간·정렬·페이지 조회 조건
     * @param binding 폼 또는 조회 조건의 바인딩 결과
     * @param model 화면에 전달할 모델
     * @param response 오류 HTTP 상태를 설정할 응답
     * @return records 뷰 이름
     */
    @GetMapping("/records") public String list(Authentication auth,@ModelAttribute("filter") QueryFilter q,BindingResult binding,Model model,HttpServletResponse response){model.addAttribute("genres",works.genres());model.addAttribute("listPath","/records");model.addAttribute("pageTitle","나의 감상 기록");try{if(binding.hasErrors())throw new InputError("조회 조건을 확인하세요.");model.addAttribute("records",records.list(Current.id(auth),q));model.addAttribute("total",records.count(Current.id(auth),q));}catch(InputError e){model.addAttribute("queryError",true);model.addAttribute("records",Collections.emptyList());model.addAttribute("total",0);model.addAttribute("message",e.getMessage());response.setStatus(e.getStatus());}return "records";}
    /**
     * @brief PAST 또는 COMPLETE 동작의 새 감상 폼을 만들고 세션 작성 UUID를 발급합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param workId 감상할 공용 작품 식별자
     * @param action PAST 또는 COMPLETE 저장 동작
     * @param session 작성 토큰을 보관할 세션
     * @param model 화면에 전달할 모델
     * @return record-form 뷰 이름
     * @throws InputError 저장 동작이 허용되지 않거나 작품을 찾을 수 없는 경우
     */
    @GetMapping("/records/new") public String create(Authentication auth,@RequestParam long workId,@RequestParam(defaultValue="PAST") String action,HttpSession session,Model model){if(!"PAST".equals(action)&&!"COMPLETE".equals(action))throw new InputError("기록 저장 동작을 선택하세요.");RecordForm f=new RecordForm();f.setWorkId(workId);f.setViewedOn(records.today().toString());f.setAction(action);f.setToken(SessionTokens.issue(session));model.addAttribute("recordForm",f);return form(model,f,false,0,Current.id(auth));}
    /**
     * @brief 작품·회원 전체이력 요약·편집 여부를 공통 감상 폼 모델에 채웁니다.
     * @param model 화면에 전달할 모델
     * @param form 표시할 감상 입력값
     * @param editing 기존 기록 수정 화면인지 여부
     * @param id 수정 대상 기록 식별자이며 신규 작성이면 0
     * @param userId 이력 요약을 조회할 인증 회원 식별자
     * @return record-form 뷰 이름
     * @throws InputError 대상 공용 작품이 존재하지 않는 경우
     */
    private String form(Model model,RecordForm form,boolean editing,long id,long userId){model.addAttribute("historySummary",records.workSummary(userId,form.getWorkId()));model.addAttribute("work",works.get(form.getWorkId()));model.addAttribute("editing",editing);model.addAttribute("recordId",id);model.addAttribute("pageTitle",editing?"감상 기록 수정":"감상 기록 남기기");return "record-form";}
    /**
     * @brief 세션 UUID를 확인해 감상을 저장하고 재전송·저장 동작별 안내와 오류 폼을 반환합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param f 감상 입력값과 저장 동작·UUID
     * @param binding 폼 또는 조회 조건의 바인딩 결과
     * @param session 작성 UUID를 확인할 세션
     * @param model 화면에 전달할 모델
     * @param response 오류 HTTP 상태를 설정할 응답
     * @param flash 리다이렉트 후 표시할 일회성 안내
     * @return 성공하면 기록 상세 리다이렉트, 입력 오류이면 작성 폼
     */
    @PostMapping("/records")
    public String save(Authentication auth, @ModelAttribute("recordForm") RecordForm form, BindingResult binding,
                       HttpSession session, Model model, HttpServletResponse response, RedirectAttributes flash) {
        long userId = Current.id(auth);
        try {
            if (binding.hasErrors()) throw new InputError("감상 기록 입력 항목을 확인하세요.");
            SessionTokens.check(session, form.getToken());
            RecordService.SubmitResult result = records.submit(userId, form);
            String message;
            if (result.isDuplicate()) {
                message = "이미 처리된 저장 요청입니다. 기존 기록을 보여드립니다.";
            } else {
                message = "COMPLETE".equals(form.getAction()) ? "기록을 저장하고 현재 상태를 완료로 변경했습니다."
                        : "과거 감상을 추가했습니다. 현재 상태는 유지됩니다.";
                if (result.getSameDayExisting() > 0) {
                    message += " 같은 날짜의 기존 기록 " + result.getSameDayExisting() + "건과 별개의 감상으로 저장했습니다.";
                }
            }
            flash.addFlashAttribute("success", message);
            return "redirect:/records/" + result.getId();
        } catch (InputError e) {
            model.addAttribute("message", e.getMessage());
            response.setStatus(e.getStatus());
            return form(model, form, false, 0, userId);
        }
    }
    /**
     * @brief 본인 소유 기록과 해당 작품의 전체이력 요약을 상세 화면에 전달합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param id 조회할 감상 기록 식별자
     * @param model 화면에 전달할 모델
     * @return record-detail 뷰 이름
     * @throws InputError 본인 소유의 해당 기록을 찾을 수 없는 경우
     */
    @GetMapping("/records/{id}")
    public String detail(Authentication auth, @PathVariable long id, Model model) {
        long userId = Current.id(auth);
        Map<String,Object> record = records.get(userId, id);
        model.addAttribute("record", record);
        model.addAttribute("previousViewing", record.get("previousViewing"));
        model.addAttribute("ratingBasis", record.get("ratingBasis"));
        model.addAttribute("historySummary", records.workSummary(userId, ((Number) record.get("workId")).longValue()));
        model.addAttribute("pageTitle", "감상 기록 상세");
        return "record-detail";
    }
    /**
     * @brief 본인 기록을 읽어 작품·평가·날짜·버전이 채워진 수정 폼을 구성합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param id 수정할 감상 기록 식별자
     * @param model 화면에 전달할 모델
     * @return record-form 뷰 이름
     * @throws InputError 본인 소유의 해당 기록을 찾을 수 없는 경우
     */
    @GetMapping("/records/{id}/edit") public String edit(Authentication auth,@PathVariable long id,Model model){Map<String,Object> row=records.get(Current.id(auth),id);RecordForm f=new RecordForm();f.setWorkId(((Number)row.get("workId")).longValue());f.setViewedOn(row.get("viewedOn").toString());f.setRating(row.get("rating")==null?"":row.get("rating").toString());f.setReview(row.get("review")==null?"":row.get("review").toString());f.setVersion(((Number)row.get("version")).intValue());model.addAttribute("recordForm",f);return form(model,f,true,id,Current.id(auth));}
    /**
     * @brief 기존 작품을 고정해 기록을 수정하고 409 충돌에는 제출 초안과 최신 기록을 구분해 표시합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param id 수정할 감상 기록 식별자
     * @param f 수정한 입력값과 화면에서 읽은 version
     * @param binding 폼 또는 조회 조건의 바인딩 결과
     * @param model 화면에 전달할 모델
     * @param response 오류 HTTP 상태를 설정할 응답
     * @param flash 리다이렉트 후 표시할 일회성 안내
     * @return 성공하면 기록 상세 리다이렉트, 오류이면 수정 폼
     * @throws InputError 수정 대상 또는 충돌 복구용 본인 기록을 찾을 수 없는 경우
     */
    @PostMapping("/records/{id}") public String update(Authentication auth,@PathVariable long id,@ModelAttribute("recordForm") RecordForm f,BindingResult binding,Model model,HttpServletResponse response,RedirectAttributes flash){Map<String,Object> existing=records.get(Current.id(auth),id);f.setWorkId(((Number)existing.get("workId")).longValue());try{if(binding.hasErrors())throw new InputError("감상 기록 입력 항목을 확인하세요.");records.update(Current.id(auth),id,f);flash.addFlashAttribute("success","감상 기록을 수정했습니다. 현재 상태는 유지됩니다.");return "redirect:/records/"+id;}catch(InputError e){model.addAttribute("message",e.getMessage());if(e.getStatus()==409){model.addAttribute("conflict",true);model.addAttribute("latestRecord",records.get(Current.id(auth),id));}response.setStatus(e.getStatus());return form(model,f,true,id,Current.id(auth));}}
    /**
     * @brief 본인 기록을 조회해 실제 삭제 전 확인 화면을 표시합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param id 삭제 확인할 감상 기록 식별자
     * @param model 화면에 전달할 모델
     * @return record-delete 뷰 이름
     * @throws InputError 본인 소유의 해당 기록을 찾을 수 없는 경우
     */
    @GetMapping("/records/{id}/delete") public String confirmDelete(Authentication auth,@PathVariable long id,Model model){model.addAttribute("record",records.get(Current.id(auth),id));model.addAttribute("pageTitle","감상 기록 삭제 확인");return "record-delete";}
    /**
     * @brief 본인 감상 기록만 삭제하고 공용 작품과 현재 상태를 유지한 채 목록으로 이동합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param id 삭제할 감상 기록 식별자
     * @param flash 리다이렉트 후 표시할 일회성 안내
     * @return 기록 목록 리다이렉트
     * @throws InputError 본인 기록을 찾지 못하거나 삭제 중 기록이 변경된 경우
     */
    @PostMapping("/records/{id}/delete") public String delete(Authentication auth,@PathVariable long id,RedirectAttributes flash){records.delete(Current.id(auth),id);flash.addFlashAttribute("success","기록을 삭제했습니다. 작품과 현재 상태는 유지됩니다.");return "redirect:/records";}
}
