package org.mnu.controller;

import java.util.*;
import javax.servlet.http.HttpServletResponse;
import org.mnu.domain.InputError;
import org.mnu.domain.QueryFilter;
import org.mnu.service.RecordService;
import org.springframework.dao.DataAccessException;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

/**
 * @brief 공통 화면 데이터·허용 입력 바인딩·안전한 오류 화면을 제공하는 MVC 공통 처리기입니다.
 */
@ControllerAdvice
public class AppAdvice {
    /**
     * @brief 서버 기준 오늘 날짜를 제공하는 감상 기록 서비스입니다.
     */
    private final RecordService records;
    /**
     * @brief 공통 날짜 조회에 사용하는 기록 서비스를 주입받습니다.
     * @param records 감상 기록 서비스
     */
    public AppAdvice(RecordService records){this.records=records;}
    /**
     * @brief 상태 이름·서버 오늘 날짜·0.5 간격 평점 선택지를 모든 화면 모델에 추가합니다.
     * @param model 화면에 전달할 모델
     */
    @ModelAttribute public void common(Model model){
        Map<String,String> states=new LinkedHashMap<String,String>();states.put("WANT","보고 싶음");states.put("WATCHING","시청 중");states.put("COMPLETED","완료");states.put("DROPPED","중단");
        model.addAttribute("stateLabels",states);model.addAttribute("today",records.today());
        List<String> ratings=new ArrayList<String>();for(int n=1;n<=10;n++)ratings.add(String.format(java.util.Locale.ROOT,"%.1f",n/2.0));model.addAttribute("ratingOptions",ratings);
    }
    /**
     * @brief 회원가입 폼에 허용된 회원 입력 필드만 바인딩합니다.
     * @param b 허용 필드와 변환기를 설정할 데이터 바인더
     */
    @InitBinder("signupForm") public void signup(WebDataBinder b){b.setAllowedFields("username","password","name","email");}
    /**
     * @brief 감상 폼의 작품·평가·저장 동작·UUID·버전 입력만 허용합니다.
     * @param b 허용 필드와 변환기를 설정할 데이터 바인더
     */
    @InitBinder("recordForm") public void record(WebDataBinder b){b.setAllowedFields("workId","viewedOn","rating","review","action","token","version");}
    /**
     * @brief 공용 작품 폼에 허용된 작품 정보와 중복 확인 입력만 허용합니다.
     * @param b 허용 필드와 변환기를 설정할 데이터 바인더
     */
    @InitBinder("workForm") public void work(WebDataBinder b){b.setAllowedFields("title","mediaType","releaseYear","synopsis","genreIds","confirmDuplicate");}
    /**
     * @brief 조회 조건의 허용 필드를 제한하고 페이지 문자열을 안전하게 정규화합니다.
     * @param b 허용 필드와 변환기를 설정할 데이터 바인더
     */
    @InitBinder("filter") public void filter(WebDataBinder b){
        b.setAllowedFields("keyword","mediaType","genreId","workId","from","to","page","sort","ratingLevel");
        b.registerCustomEditor(int.class,"page",new java.beans.PropertyEditorSupport(){@Override public void setAsText(String text){setValue(QueryFilter.parsePage(text));}});
    }
    /**
     * @brief 사용자 오류의 메시지와 지정된 HTTP 상태를 공통 오류 화면에 반영합니다.
     * @param e 안내 메시지와 상태를 가진 입력 오류
     * @param response 오류 HTTP 상태를 설정할 응답
     * @return error 뷰와 사용자 안내 모델
     */
    @ExceptionHandler(InputError.class) public ModelAndView input(InputError e,HttpServletResponse response){response.setStatus(e.getStatus());ModelAndView view=new ModelAndView("error");view.addObject("message",e.getMessage());return view;}
    /**
     * @brief 바인딩·인자 형식·필수값 오류를 HTTP 400의 공통 안내로 변환합니다.
     * @param e Spring MVC 입력 바인딩 또는 요청 인자 오류
     * @param response 오류 HTTP 상태를 설정할 응답
     * @return 내부 예외 내용을 노출하지 않는 error 뷰
     */
    @ExceptionHandler({org.springframework.validation.BindException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class}) public ModelAndView binding(Exception e,HttpServletResponse response){response.setStatus(400);return new ModelAndView("error","message","입력 형식과 필수 항목을 확인하세요.");}
    /**
     * @brief DB 접근 오류를 HTTP 503의 일반 안내로 변환합니다.
     * @param e 처리 중 발생한 데이터 접근 예외
     * @param response 오류 HTTP 상태를 설정할 응답
     * @return DB 내부 내용을 노출하지 않는 error 뷰
     */
    @ExceptionHandler(DataAccessException.class) public ModelAndView database(DataAccessException e,HttpServletResponse response){response.setStatus(503);return new ModelAndView("error","message","저장소에 연결하지 못했습니다. 잠시 후 다시 시도하세요.");}
}
