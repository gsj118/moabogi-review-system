package org.mnu.controller;

import java.util.*;
import javax.servlet.http.HttpServletResponse;
import org.mnu.domain.*;
import org.mnu.external.TmdbClient;
import org.mnu.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WorkController {
    private final WorkService works;private final RecordService records;
    public WorkController(WorkService works,RecordService records){this.works=works;this.records=records;}
    @GetMapping("/works") public String list(Authentication auth,@ModelAttribute("filter") QueryFilter q,BindingResult binding,Model model,HttpServletResponse response){model.addAttribute("genres",works.genres());model.addAttribute("listPath","/works");model.addAttribute("pageTitle","작품 찾기");try{if(binding.hasErrors())throw new InputError("검색 조건을 확인하세요.");List<Map<String,Object>> found=works.list(q);model.addAttribute("works",found);List<Long> ids=new ArrayList<Long>();for(Map<String,Object> row:found)ids.add(((Number)row.get("id")).longValue());model.addAttribute("myWorks",records.workSummaries(Current.id(auth),ids));model.addAttribute("total",works.count(q));}catch(InputError e){model.addAttribute("queryError",true);model.addAttribute("message",e.getMessage());response.setStatus(e.getStatus());}return "works";}
    @GetMapping("/works/new") public String create(@RequestParam(defaultValue="") String title,Model model){WorkForm f=new WorkForm();f.setTitle(title);model.addAttribute("workForm",f);return workForm(model,false,0);}
    private String workForm(Model model,boolean edit,long id){model.addAttribute("genres",works.genres());model.addAttribute("editing",edit);model.addAttribute("workId",id);model.addAttribute("pageTitle",edit?"공용 작품 수정":"작품 직접 등록");return "work-form";}
    @PostMapping("/works") public String save(Authentication auth,@ModelAttribute("workForm") WorkForm form,BindingResult binding,Model model,HttpServletResponse response,RedirectAttributes flash){
        try{if(binding.hasErrors())throw new InputError("작품 입력 항목을 확인하세요.");long id=works.create(Current.id(auth),form);flash.addFlashAttribute("success","공용 작품이 등록되었습니다.");return "redirect:/works/"+id;}
        catch(InputError e){model.addAttribute("message",e.getMessage());model.addAttribute("duplicates",works.duplicates(form));response.setStatus(e.getStatus());return workForm(model,false,0);}
    }
    @GetMapping("/works/{id}") public String detail(Authentication auth,@PathVariable long id,@ModelAttribute("filter") QueryFilter q,BindingResult binding,Model model){if(binding.hasErrors())throw new InputError("조회 조건을 확인하세요.");q.setWorkId(id);model.addAttribute("work",works.get(id));model.addAttribute("records",records.list(Current.id(auth),q));model.addAttribute("total",records.count(Current.id(auth),q));model.addAttribute("listPath","/works/"+id);model.addAttribute("historySummary",records.workSummary(Current.id(auth),id));model.addAttribute("currentState",records.state(Current.id(auth),id));model.addAttribute("pageTitle","작품과 나의 이력");return "work-detail";}
    @PostMapping("/works/{id}/state") public String state(Authentication auth,@PathVariable long id,@RequestParam String status,RedirectAttributes flash){records.state(Current.id(auth),id,status);flash.addFlashAttribute("success",status.isEmpty()?"현재 상태를 해제했습니다. 감상 이력은 유지됩니다.":"현재 상태를 변경했습니다. 감상 이력은 유지됩니다.");return "redirect:/works/"+id;}
    @GetMapping("/states") public String states(Authentication auth,@RequestParam(defaultValue="") String status,Model model){model.addAttribute("states",records.states(Current.id(auth),status));model.addAttribute("selectedState",status);model.addAttribute("pageTitle","현재 시청 상태");return "states";}
    @GetMapping("/admin/works/{id}/edit") public String edit(@PathVariable long id,Model model){Map<String,Object> row=works.get(id);WorkForm form=new WorkForm();form.setTitle((String)row.get("title"));form.setMediaType((String)row.get("mediaType"));form.setSynopsis(row.get("synopsis")==null?"":row.get("synopsis").toString());form.setReleaseYear(row.get("releaseYear")==null?"":row.get("releaseYear").toString());form.setGenreIds(works.genreIds(id));model.addAttribute("workForm",form);return workForm(model,true,id);}
    @PostMapping("/admin/works/{id}") public String update(Authentication auth,@PathVariable long id,@ModelAttribute("workForm") WorkForm form,BindingResult binding,Model model,HttpServletResponse response,RedirectAttributes flash){try{if(binding.hasErrors())throw new InputError("작품 입력 항목을 확인하세요.");works.update(id,form,Current.admin(auth));flash.addFlashAttribute("success","공용 작품을 수정했습니다.");return "redirect:/works/"+id;}catch(InputError e){model.addAttribute("message",e.getMessage());response.setStatus(e.getStatus());return workForm(model,true,id);}}
    @PostMapping("/admin/works/{id}/delete") public String delete(Authentication auth,@PathVariable long id,RedirectAttributes flash){works.delete(id,Current.admin(auth));flash.addFlashAttribute("success","참조되지 않은 공용 작품을 삭제했습니다.");return "redirect:/works";}
    @GetMapping("/admin/genres") public String genres(Model model){model.addAttribute("genres",works.genres());model.addAttribute("pageTitle","공용 장르 관리");return "genres";}
    @PostMapping("/admin/genres") public String createGenre(Authentication auth,@RequestParam String name,RedirectAttributes flash){works.createGenre(name,Current.admin(auth));flash.addFlashAttribute("success","장르를 등록했습니다.");return "redirect:/admin/genres";}
    @PostMapping("/admin/genres/{id}") public String renameGenre(Authentication auth,@PathVariable long id,@RequestParam String name,RedirectAttributes flash){works.renameGenre(id,name,Current.admin(auth));flash.addFlashAttribute("success","장르를 수정했습니다.");return "redirect:/admin/genres";}
    @PostMapping("/admin/genres/{id}/delete") public String deleteGenre(Authentication auth,@PathVariable long id,RedirectAttributes flash){works.deleteGenre(id,Current.admin(auth));flash.addFlashAttribute("success","장르를 삭제했습니다.");return "redirect:/admin/genres";}
    @GetMapping("/works/search-external") public String searchExternal(@RequestParam(defaultValue="") String query,@RequestParam(defaultValue="MOVIE") String mediaType,Model model,HttpServletResponse response){
        model.addAttribute("externalSearchEnabled",works.isExternalSearchEnabled());
        model.addAttribute("pageTitle","외부 작품 검색");
        if(!query.isEmpty()){
            try{
                model.addAttribute("externalResults",works.searchExternal(query,mediaType));
                model.addAttribute("query",query);
                model.addAttribute("mediaType",mediaType);
            }catch(TmdbClient.TmdbException e){
                model.addAttribute("errorType",e.getType());
                model.addAttribute("message",e.getMessage());
                response.setStatus(e.getType()==TmdbClient.TmdbException.ErrorType.NOT_FOUND?404:500);
            }
        }
        return "search-external";
    }
    @PostMapping("/works/import-external") public String importExternal(Authentication auth,@RequestParam long externalId,@RequestParam String mediaType,@RequestParam boolean confirmDuplicate,Model model,HttpServletResponse response,RedirectAttributes flash){
        try{
            Map<String,Object> details=works.getExternalDetails(externalId,mediaType);
            WorkForm form=new WorkForm();
            form.setTitle((String)details.get("title"));
            form.setMediaType(mediaType);
            Integer year=(Integer)details.get("releaseYear");
            form.setReleaseYear(year==null?"":year.toString());
            form.setSynopsis((String)details.get("overview"));
            form.setConfirmDuplicate(confirmDuplicate);
            @SuppressWarnings("unchecked")
            List<Long> genreIds=(List<Long>)details.get("genreIds");
            form.setGenreIds(genreIds!=null?genreIds:new ArrayList<Long>());
            form.setExternalId(externalId);
            form.setExternalSource("TMDB");
            long id=works.create(Current.id(auth),form);
            flash.addFlashAttribute("success","외부 작품을 등록했습니다.");
            return "redirect:/works/"+id;
        }catch(TmdbClient.TmdbException e){
            model.addAttribute("message","작품 정보를 가져올 수 없습니다. "+e.getMessage());
            response.setStatus(500);
            return "error";
        }catch(InputError e){
            model.addAttribute("message",e.getMessage());
            response.setStatus(e.getStatus());
            return "error";
        }
    }
}
