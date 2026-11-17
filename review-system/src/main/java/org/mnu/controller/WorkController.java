package org.mnu.controller;

import java.util.*;
import javax.servlet.http.HttpServletResponse;
import org.mnu.domain.*;
import org.mnu.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * @brief 공용 작품 조회·등록·관리와 회원별 이력·현재 상태 화면을 연결하는 컨트롤러입니다.
 */
@Controller
public class WorkController {
    /**
     * @brief 공용 작품·장르의 조회와 권한·중복 검증을 담당하는 서비스입니다.
     */
    private final WorkService works;
    /**
     * @brief 인증 회원별 감상 이력과 현재 시청 상태를 제공하는 서비스입니다.
     */
    private final RecordService records;
    /**
     * @brief 공용 작품 및 개인 감상 기록 서비스를 주입받습니다.
     * @param works 공용 작품·장르 서비스
     * @param records 회원 감상 기록·상태 서비스
     */
    public WorkController(WorkService works,RecordService records){this.works=works;this.records=records;}
    /**
     * @brief 등록된 공용 작품과 인증 회원의 작품별 이력 요약을 조회하고 조건 오류를 표시합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param q 작품 검색·정렬·페이지 조회 조건
     * @param binding 폼 또는 조회 조건의 바인딩 결과
     * @param model 화면에 전달할 모델
     * @param response 오류 HTTP 상태를 설정할 응답
     * @return works 뷰 이름
     */
    @GetMapping("/works") public String list(Authentication auth,@ModelAttribute("filter") QueryFilter q,BindingResult binding,Model model,HttpServletResponse response){model.addAttribute("genres",works.genres());model.addAttribute("listPath","/works");model.addAttribute("pageTitle","작품 찾기");try{if(binding.hasErrors())throw new InputError("검색 조건을 확인하세요.");List<Map<String,Object>> found=works.list(q);model.addAttribute("works",found);List<Long> ids=new ArrayList<Long>();for(Map<String,Object> row:found)ids.add(((Number)row.get("id")).longValue());model.addAttribute("myWorks",records.workSummaries(Current.id(auth),ids));model.addAttribute("total",works.count(q));}catch(InputError e){model.addAttribute("queryError",true);model.addAttribute("message",e.getMessage());response.setStatus(e.getStatus());}return "works";}
    /**
     * @brief 전달된 검색 제목을 초안으로 삼아 공용 작품 직접 등록 폼을 만듭니다.
     * @param title 초기 제목이며 생략하면 빈 문자열
     * @param model 화면에 전달할 모델
     * @return work-form 뷰 이름
     */
    @GetMapping("/works/new") public String create(@RequestParam(defaultValue="") String title,Model model){WorkForm f=new WorkForm();f.setTitle(title);model.addAttribute("workForm",f);return workForm(model,false,0);}
    /**
     * @brief 장르 목록·신규 또는 수정 여부·작품 식별자를 작품 폼 모델에 채웁니다.
     * @param model 화면에 전달할 모델
     * @param edit 기존 공용 작품 수정 화면인지 여부
     * @param id 수정 대상 작품 식별자이며 신규 등록이면 0
     * @return work-form 뷰 이름
     */
    private String workForm(Model model,boolean edit,long id){model.addAttribute("genres",works.genres());model.addAttribute("editing",edit);model.addAttribute("workId",id);model.addAttribute("pageTitle",edit?"공용 작품 수정":"작품 직접 등록");return "work-form";}
    /**
     * @brief 작품을 등록하고 입력·중복 오류에는 입력값과 중복 후보를 같은 폼에 표시합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param form 등록할 작품 정보와 수동 중복 확인 여부
     * @param binding 폼 또는 조회 조건의 바인딩 결과
     * @param model 화면에 전달할 모델
     * @param response 오류 HTTP 상태를 설정할 응답
     * @param flash 리다이렉트 후 표시할 일회성 안내
     * @return 성공하면 작품 상세 리다이렉트, 오류이면 작품 작성 폼
     */
    @PostMapping("/works") public String save(Authentication auth,@ModelAttribute("workForm") WorkForm form,BindingResult binding,Model model,HttpServletResponse response,RedirectAttributes flash){
        try{if(binding.hasErrors())throw new InputError("작품 입력 항목을 확인하세요.");long id=works.create(Current.id(auth),form);flash.addFlashAttribute("success","공용 작품이 등록되었습니다.");return "redirect:/works/"+id;}
        catch(InputError e){model.addAttribute("message",e.getMessage());model.addAttribute("duplicates",works.duplicates(form));response.setStatus(e.getStatus());return workForm(model,false,0);}
    }
    /**
     * @brief 공용 작품과 본인 기록·전체이력 요약·현재 상태를 함께 표시합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param id 조회할 공용 작품 식별자
     * @param q 해당 작품의 개인 기록 조회 조건
     * @param binding 폼 또는 조회 조건의 바인딩 결과
     * @param model 화면에 전달할 모델
     * @return work-detail 뷰 이름
     * @throws InputError 조건이 올바르지 않거나 작품을 찾을 수 없는 경우
     */
    @GetMapping("/works/{id}") public String detail(Authentication auth,@PathVariable long id,@ModelAttribute("filter") QueryFilter q,BindingResult binding,Model model){if(binding.hasErrors())throw new InputError("조회 조건을 확인하세요.");q.setWorkId(id);model.addAttribute("work",works.get(id));model.addAttribute("records",records.list(Current.id(auth),q));model.addAttribute("total",records.count(Current.id(auth),q));model.addAttribute("listPath","/works/"+id);model.addAttribute("historySummary",records.workSummary(Current.id(auth),id));model.addAttribute("currentState",records.state(Current.id(auth),id));model.addAttribute("pageTitle","작품과 나의 이력");return "work-detail";}
    /**
     * @brief 인증 회원의 현재 상태만 변경하거나 해제하며 감상 기록은 유지합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param id 상태를 관리할 공용 작품 식별자
     * @param status WANT·WATCHING·COMPLETED·DROPPED 또는 해제할 빈 문자열
     * @param flash 리다이렉트 후 표시할 일회성 안내
     * @return 작품 상세 리다이렉트
     * @throws InputError 작품이 없거나 상태 코드가 허용되지 않는 경우
     */
    @PostMapping("/works/{id}/state") public String state(Authentication auth,@PathVariable long id,@RequestParam String status,RedirectAttributes flash){records.state(Current.id(auth),id,status);flash.addFlashAttribute("success",status.isEmpty()?"현재 상태를 해제했습니다. 감상 이력은 유지됩니다.":"현재 상태를 변경했습니다. 감상 이력은 유지됩니다.");return "redirect:/works/"+id;}
    /**
     * @brief 인증 회원의 현재 시청 상태를 선택한 상태별로 조회합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param status 조회할 상태 코드이며 빈 문자열이면 전체 상태
     * @param model 화면에 전달할 모델
     * @return states 뷰 이름
     * @throws InputError 상태 코드가 허용되지 않는 경우
     */
    @GetMapping("/states") public String states(Authentication auth,@RequestParam(defaultValue="") String status,Model model){model.addAttribute("states",records.states(Current.id(auth),status));model.addAttribute("selectedState",status);model.addAttribute("pageTitle","현재 시청 상태");return "states";}
    /**
     * @brief 관리자 경로에서 기존 공용 작품과 장르가 채워진 수정 폼을 준비합니다.
     * @param id 수정할 공용 작품 식별자
     * @param model 화면에 전달할 모델
     * @return work-form 뷰 이름
     * @throws InputError 작품을 찾을 수 없는 경우
     */
    @GetMapping("/admin/works/{id}/edit") public String edit(@PathVariable long id,Model model){Map<String,Object> row=works.get(id);WorkForm form=new WorkForm();form.setTitle((String)row.get("title"));form.setMediaType((String)row.get("mediaType"));form.setSynopsis(row.get("synopsis")==null?"":row.get("synopsis").toString());form.setReleaseYear(row.get("releaseYear")==null?"":row.get("releaseYear").toString());form.setGenreIds(works.genreIds(id));model.addAttribute("workForm",form);return workForm(model,true,id);}
    /**
     * @brief 관리자 여부를 서비스에 전달하여 공용 작품을 수정하고 오류에는 폼을 유지합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param id 수정할 공용 작품 식별자
     * @param form 수정할 공용 작품 정보
     * @param binding 폼 또는 조회 조건의 바인딩 결과
     * @param model 화면에 전달할 모델
     * @param response 오류 HTTP 상태를 설정할 응답
     * @param flash 리다이렉트 후 표시할 일회성 안내
     * @return 성공하면 작품 상세 리다이렉트, 오류이면 작품 수정 폼
     */
    @PostMapping("/admin/works/{id}") public String update(Authentication auth,@PathVariable long id,@ModelAttribute("workForm") WorkForm form,BindingResult binding,Model model,HttpServletResponse response,RedirectAttributes flash){try{if(binding.hasErrors())throw new InputError("작품 입력 항목을 확인하세요.");works.update(id,form,Current.admin(auth));flash.addFlashAttribute("success","공용 작품을 수정했습니다.");return "redirect:/works/"+id;}catch(InputError e){model.addAttribute("message",e.getMessage());response.setStatus(e.getStatus());return workForm(model,true,id);}}
    /**
     * @brief 관리자 권한으로 개인 기록·상태에서 참조하지 않는 공용 작품을 삭제합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param id 삭제할 공용 작품 식별자
     * @param flash 리다이렉트 후 표시할 일회성 안내
     * @return 작품 목록 리다이렉트
     * @throws InputError 관리자 권한이 없거나 작품이 없거나 다른 데이터에서 참조 중인 경우
     */
    @PostMapping("/admin/works/{id}/delete") public String delete(Authentication auth,@PathVariable long id,RedirectAttributes flash){works.delete(id,Current.admin(auth));flash.addFlashAttribute("success","참조되지 않은 공용 작품을 삭제했습니다.");return "redirect:/works";}
    /**
     * @brief 관리자용 공용 장르 목록 화면을 준비합니다.
     * @param model 화면에 전달할 모델
     * @return genres 뷰 이름
     */
    @GetMapping("/admin/genres") public String genres(Model model){model.addAttribute("genres",works.genres());model.addAttribute("pageTitle","공용 장르 관리");return "genres";}
    /**
     * @brief 관리자 권한으로 공용 장르를 등록합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param name 등록할 장르 이름
     * @param flash 리다이렉트 후 표시할 일회성 안내
     * @return 장르 관리 화면 리다이렉트
     * @throws InputError 관리자 권한이 없거나 장르 이름이 유효하지 않거나 중복인 경우
     */
    @PostMapping("/admin/genres") public String createGenre(Authentication auth,@RequestParam String name,RedirectAttributes flash){works.createGenre(name,Current.admin(auth));flash.addFlashAttribute("success","장르를 등록했습니다.");return "redirect:/admin/genres";}
    /**
     * @brief 관리자 권한으로 기존 장르 이름을 변경합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param id 변경할 장르 식별자
     * @param name 새 장르 이름
     * @param flash 리다이렉트 후 표시할 일회성 안내
     * @return 장르 관리 화면 리다이렉트
     * @throws InputError 권한·장르 존재·이름 검증 또는 중복 검사에 실패한 경우
     */
    @PostMapping("/admin/genres/{id}") public String renameGenre(Authentication auth,@PathVariable long id,@RequestParam String name,RedirectAttributes flash){works.renameGenre(id,name,Current.admin(auth));flash.addFlashAttribute("success","장르를 수정했습니다.");return "redirect:/admin/genres";}
    /**
     * @brief 관리자 권한으로 작품에서 참조하지 않는 장르를 삭제합니다.
     * @param auth 로그인 회원의 인증 정보
     * @param id 삭제할 장르 식별자
     * @param flash 리다이렉트 후 표시할 일회성 안내
     * @return 장르 관리 화면 리다이렉트
     * @throws InputError 관리자 권한이 없거나 작품이 참조 중인 경우
     */
    @PostMapping("/admin/genres/{id}/delete") public String deleteGenre(Authentication auth,@PathVariable long id,RedirectAttributes flash){works.deleteGenre(id,Current.admin(auth));flash.addFlashAttribute("success","장르를 삭제했습니다.");return "redirect:/admin/genres";}
}
