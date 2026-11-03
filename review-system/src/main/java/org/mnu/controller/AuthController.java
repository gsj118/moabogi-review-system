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
@Controller
public class AuthController {
    private final AccountService accounts;
    public AuthController(AccountService accounts){this.accounts=accounts;}
    @GetMapping("/login") public String login(Model model,HttpSession session){model.addAttribute("pageTitle","로그인");model.addAttribute("loginUsername",session.getAttribute("loginUsername"));session.removeAttribute("loginUsername");return "login";}
    @GetMapping("/signup") public String signup(Model model){model.addAttribute("signupForm",new SignupForm());model.addAttribute("pageTitle","회원가입");return "signup";}
    @PostMapping("/signup") public String register(@ModelAttribute("signupForm") SignupForm form,BindingResult binding,Model model,HttpServletResponse response,RedirectAttributes redirect){
        try{if(binding.hasErrors())throw new InputError("가입 정보를 확인하세요.");accounts.register(form);redirect.addAttribute("registered","");return "redirect:/login";}
        catch(InputError e){form.setPassword("");model.addAttribute("message",e.getMessage());model.addAttribute("pageTitle","회원가입");response.setStatus(e.getStatus());return "signup";}
    }
}
