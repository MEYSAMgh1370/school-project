package com.example.demo.repositories;

import com.example.demo.model.amoozesh.Personnel;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface PersonnelRepository extends CrudRepository<Personnel, Long> {
    Long deleteByPersonalInfo_NationalNumber(String personalInfo_nationalNumber);
    Optional<Personnel> findPersonnelByPersonalInfo_NationalNumber(String personalInfo_nationalNumber);


}
