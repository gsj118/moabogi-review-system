package org.mnu.controller;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.mnu.domain.*;
import org.mnu.service.AccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
/**
 * @brief 로그인 화면과 회원가입 입력·성공 이동·오류 복구를 처리하는 컨트롤러입니다.
 */
@Controller
public class AuthController {
    /**
     * @brief 회원가입 검증과 계정 저장을 담당하는 서비스입니다.
     */
    private final AccountService accounts;
    /**
     * @brief 회원가입 서비스를 주입받습니다.
     * @param accounts 회원 검증·등록 서비스
     */
    public AuthController(AccountService accounts){this.accounts=accounts;}
    /**
     * @brief 로그인 실패 때 세션에 보관한 아이디를 화면에 전달하고 세션 항목을 제거합니다.
     * @param model 화면에 전달할 모델
     * @param session 실패 아이디가 임시 보관된 HTTP 세션
     * @return login 뷰 이름
     */
    @GetMapping("/login") public String login(Model model,HttpSession session){model.addAttribute("pageTitle","로그인");model.addAttribute("loginUsername",session.getAttribute("loginUsername"));session.removeAttribute("loginUsername");return "login";}
    /**
     * @brief 빈 회원가입 폼과 페이지 제목을 준비합니다.
     * @param model 화면에 전달할 모델
     * @return signup 뷰 이름
     */
    @GetMapping("/signup") public String signup(Model model){model.addAttribute("signupForm",new SignupForm());model.addAttribute("pageTitle","회원가입");return "signup";}
    /**
     * @brief 가입을 요청하고 입력 오류 시 비밀번호를 비운 폼을, 성공 시 로그인 이동을 반환합니다.
     * @param form 입력한 가입 정보
     * @param binding 폼 또는 조회 조건의 바인딩 결과
     * @param model 화면에 전달할 모델
     * @param response 오류 HTTP 상태를 설정할 응답
     * @param redirect 가입 성공 표시를 전달할 리다이렉트 속성
     * @return 성공하면 로그인 리다이렉트, 입력 오류이면 signup 뷰
     */
    @PostMapping("/signup") public String register(@ModelAttribute("signupForm") SignupForm form,BindingResult binding,Model model,HttpServletResponse response,RedirectAttributes redirect){
        try{if(binding.hasErrors())throw new InputError("가입 정보를 확인하세요.");accounts.register(form);redirect.addAttribute("registered","");return "redirect:/login";}
        catch(InputError e){form.setPassword("");model.addAttribute("message",e.getMessage());model.addAttribute("pageTitle","회원가입");response.setStatus(e.getStatus());return "signup";}
    }
}
