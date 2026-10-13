package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * صفحه‌های باکس سؤالات (openspec/changes/question-box.md): {@code /question-box} (سؤال‌های معلم)،
 * {@code /question-sheets} (برگه‌ها و سربرگ‌ها) و {@code /question-sheets/{id}/print} (برگه‌ی چاپی). پوسته‌های نازک
 * مثل {@code /curriculum}: داده از REST ماژول questionbox می‌آید و اجازه‌دهی فقط روی همان لایه است.
 * صفحه‌ی چاپ ناوبار ندارد؛ فقط نامِ مدرسه‌ها را برای سربرگ می‌خواهد.
 */
@Controller
public class QuestionBoxPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public QuestionBoxPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/question-box")
    public String questionBox(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "question-box";
    }

    @GetMapping("/question-sheets")
    public String questionSheets(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "question-sheets";
    }

    @GetMapping("/question-sheets/{sheetId}/print")
    public String printSheet(@PathVariable Long sheetId, Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        model.addAttribute("sheetId", sheetId);
        return "question-sheet-print";
    }
}
