package com.example.demo.controllers;

import com.example.demo.dto.StudentDTO;
import com.example.demo.model.amoozesh.Student;
import com.example.demo.service.StudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StudentController {

    private final StudentService studentService;


    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("api/Student")
    public List<Student> sortStudent(StudentDTO studentDTO) {
        return studentService.getStudent(studentDTO);
    }

}
