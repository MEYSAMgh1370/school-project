package com.example.demo.repositories;

import com.example.demo.model.amoozesh.Personnel;
import com.example.demo.model.amoozesh.Student;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface StudentRepository extends CrudRepository<Student, Long> {

    List<Student> findPersonnelsByPersonalInfo_FirstNameOrPersonalInfo_LastNameOrPersonalInfo_NationalNumberOr(String personalInfo_firstName, String personalInfo_lastName, String personalInfo_nationalNumber);
}
