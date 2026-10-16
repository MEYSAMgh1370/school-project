package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * «برگه‌ی حضور و غیابِ دانش‌آموز» (openspec/changes/attendance-reports.md): {@code /attendance/report/student/{studentId}}
 * صفحه‌ی چاپی (مرورگر آن را PDF می‌کند). پوسته‌ی نازک مثل {@code /question-sheets/{id}/print}: بازه ({@code from}/{@code to})
 * را خودِ صفحه از آدرس می‌خواند، داده از {@code GET /api/admin/schools/{id}/attendance/report/students/{studentId}}
 * (ماژول ATTENDANCE، مجوز ATTENDANCE_VIEW_ALL) می‌آید و اجازه‌دهی فقط روی همان لایه‌ی REST است. ناوبار ندارد؛
 * مدل فقط برای نامِ مدرسه‌ها در سربرگ است.
 */
@Controller
public class AttendanceSheetPageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public AttendanceSheetPageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/attendance/report/student/{studentId}")
    public String attendanceSheetPage(@PathVariable Long studentId, Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        model.addAttribute("studentId", studentId);
        return "attendance-sheet";
    }
}
