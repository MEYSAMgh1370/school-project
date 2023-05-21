package com.example.demo.service;

import com.example.demo.model.amoozesh.Personnel;

import java.util.Set;

public interface PersonnelService {

    Set<Personnel> findAll();
    Personnel findByNationalNumber(String nationalNumber);
    Personnel createNewPersonnel(Personnel object);
    void deleteByNationalNumber(String nationalNumber);
    void delete(Personnel object);
    Personnel patchPersonnel(String nationalNumber, Personnel personnel);

    Personnel savePersonnelByDTO(Personnel personnelDTOToPersonnel);
}
