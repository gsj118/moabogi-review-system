package org.mnu.controller;

import java.util.Collections;
import java.util.List;
import org.mnu.service.HiddenGenreService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HiddenGenreController {
    private final HiddenGenreService genres;

    public HiddenGenreController(HiddenGenreService genres) {
        this.genres = genres;
    }

    @GetMapping("/works/hidden-genres")
    public String edit(Authentication auth, Model model) {
        model.addAttribute("genres", genres.choices(Current.id(auth)));
        model.addAttribute("pageTitle", "장르 숨김 설정");
        return "hidden-genres";
    }

    @PostMapping("/works/hidden-genres")
    public String save(Authentication auth, @RequestParam(name="hide", required=false) List<Long> hide,
                       RedirectAttributes flash) {
        genres.replace(Current.id(auth), hide == null ? Collections.emptyList() : hide);
        flash.addFlashAttribute("success", "작품 목록의 장르 숨김 설정을 저장했습니다.");
        return "redirect:/works";
    }
}
