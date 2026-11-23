package org.mnu.controller;

import java.util.*;
import org.mnu.domain.*;
import org.mnu.service.FileService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FileController {
    private final FileService files;
    public FileController(FileService files) { this.files = files; }

    @PostMapping("/files/upload")
    public String upload(Authentication auth, @RequestParam long postId, @RequestParam("file") MultipartFile file,
                         Model model, RedirectAttributes flash) {
        try {
            FileAttachment fa = files.upload(postId, Current.id(auth), file);
            flash.addFlashAttribute("success", "파일이 첨부되었습니다.");
            return "redirect:/posts/" + postId;
        } catch (InputError e) {
            model.addAttribute("message", e.getMessage());
            return "error";
        }
    }

    @GetMapping("/files/{id}/download")
    public ResponseEntity<byte[]> download(Authentication auth, @PathVariable long id) {
        try {
            FileAttachment fa = files.get(id);
            byte[] content = files.download(id, Current.id(auth));
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fa.getOriginalFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, fa.getMimeType())
                .body(content);
        } catch (InputError e) {
            return ResponseEntity.status(e.getStatus()).build();
        }
    }

    @PostMapping("/files/{id}/delete")
    public String delete(Authentication auth, @PathVariable long id, RedirectAttributes flash) {
        try {
            FileAttachment fa = files.get(id);
            files.delete(id, Current.id(auth));
            flash.addFlashAttribute("success", "파일이 삭제되었습니다.");
            return "redirect:/posts/" + fa.getPostId();
        } catch (InputError e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/files/post/{postId}")
    @ResponseBody
    public List<Map<String, Object>> listByPost(@PathVariable long postId) {
        return files.listByPost(postId);
    }
}
