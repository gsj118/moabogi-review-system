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

@ControllerAdvice
public class AppAdvice {
    private final RecordService records;
    public AppAdvice(RecordService records){this.records=records;}
    @ModelAttribute public void common(Model model){
        Map<String,String> states=new LinkedHashMap<String,String>();states.put("WANT","보고 싶음");states.put("WATCHING","시청 중");states.put("COMPLETED","완료");states.put("DROPPED","중단");
        model.addAttribute("stateLabels",states);model.addAttribute("today",records.today());
        List<String> ratings=new ArrayList<String>();for(int n=1;n<=10;n++)ratings.add(String.format(java.util.Locale.ROOT,"%.1f",n/2.0));model.addAttribute("ratingOptions",ratings);
    }
    @InitBinder("signupForm") public void signup(WebDataBinder b){b.setAllowedFields("username","password","name","email");}
    @InitBinder("recordForm") public void record(WebDataBinder b){b.setAllowedFields("workId","viewedOn","rating","review","action","token","version");}
    @InitBinder("workForm") public void work(WebDataBinder b){b.setAllowedFields("title","mediaType","releaseYear","synopsis","genreIds","confirmDuplicate");}
    @InitBinder("filter") public void filter(WebDataBinder b){b.setAllowedFields("keyword","mediaType","genreId","workId","from","to","page","sort");b.registerCustomEditor(int.class,"page",new java.beans.PropertyEditorSupport(){@Override public void setAsText(String text){setValue(QueryFilter.parsePage(text));}});}
    @ExceptionHandler(InputError.class) public ModelAndView input(InputError e,HttpServletResponse response){response.setStatus(e.getStatus());ModelAndView view=new ModelAndView("error");view.addObject("message",e.getMessage());return view;}
    @ExceptionHandler({org.springframework.validation.BindException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class}) public ModelAndView binding(Exception e,HttpServletResponse response){response.setStatus(400);return new ModelAndView("error","message","입력 형식과 필수 항목을 확인하세요.");}
    @ExceptionHandler(DataAccessException.class) public ModelAndView database(DataAccessException e,HttpServletResponse response){response.setStatus(503);return new ModelAndView("error","message","저장소에 연결하지 못했습니다. 잠시 후 다시 시도하세요.");}
}
