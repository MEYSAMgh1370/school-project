package com.example.roshdeandishe.web.controller;

import com.example.roshdeandishe.web.model.NavbarModelAssembler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * «حضور و غیاب» (openspec/changes/attendance.md): دو shell نازک که فقط ناوبار مشترک را می‌سازند؛ همه‌ی
 * داده client-side از {@code attendance.AttendanceController}
 * ({@code /api/admin/schools/{id}/attendance}) می‌آید. {@code /attendance} ثبتِ معلم/مدیر
 * (ATTENDANCE_RECORD یا ATTENDANCE_RECORD_ANY) و {@code /attendance/report} غایبینِ روز و گزارشِ بازه‌ایِ مدیر
 * (ATTENDANCE_VIEW_ALL) است. اجازه‌دهی فقط روی لایه‌ی REST می‌ماند.
 */
@Controller
public class AttendancePageController {

    private final NavbarModelAssembler navbarModelAssembler;

    public AttendancePageController(NavbarModelAssembler navbarModelAssembler) {
        this.navbarModelAssembler = navbarModelAssembler;
    }

    @GetMapping("/attendance")
    public String attendancePage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "attendance";
    }

    @GetMapping("/attendance/report")
    public String attendanceReportPage(Model model) {
        model.addAttribute("navbar", navbarModelAssembler.forCurrentUser());
        return "attendance-report";
    }
}
