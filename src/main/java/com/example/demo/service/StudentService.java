package com.example.demo.service;


import com.example.demo.dto.StudentDTO;
import com.example.demo.model.amoozesh.Student;

import java.util.List;

public interface StudentService {
    List<Student> getStudent(StudentDTO studentDTO);
    Student save(Student object);
    void deleteByNationalNumber(String nationalNumber);
    void delete(Student object);
}


