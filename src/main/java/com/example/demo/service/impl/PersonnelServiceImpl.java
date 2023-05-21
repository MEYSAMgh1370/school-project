package com.example.demo.service.impl;

import com.example.demo.model.amoozesh.Personnel;
import com.example.demo.repositories.PersonnelRepository;
import com.example.demo.service.PersonnelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class PersonnelServiceImpl extends Throwable implements PersonnelService {

    private final PersonnelRepository personnelRepository;

    public PersonnelServiceImpl(PersonnelRepository personnelRepository) {
        this.personnelRepository = personnelRepository;
    }

    @Override
    public Set<Personnel> findAll() {

        Set<Personnel> personals = new HashSet<>();
        personnelRepository.findAll().iterator().forEachRemaining(personals::add);
        return personals;
    }

    @Override
    public Personnel findByNationalNumber(String nationalNumber) {

        Optional<Personnel> personnelOptional = personnelRepository.findPersonnelByPersonalInfo_NationalNumber(nationalNumber);
        if (personnelOptional.isEmpty()) {
            log.debug("personnel national number not found. national number" + nationalNumber);
        }

            return personnelOptional.get();
    }

    @Override
    public Personnel createNewPersonnel(Personnel personnel) {
        return personnelRepository.save(personnel);
    }

    @Override
    public void deleteByNationalNumber(String nationalNumber) {

        Long m= personnelRepository.deleteByPersonalInfo_NationalNumber(nationalNumber);
        log.info(m + "is deleted");
    }

    @Override
    public void delete(Personnel personnel) {

        personnelRepository.deleteById(personnel.getId());

    }

    @Override
    public Personnel patchPersonnel(String nationalNumber, Personnel personnel) {

        return personnelRepository.save(personnel);

    }

    @Override
    public Personnel savePersonnelByDTO(Personnel personnelDTOToPersonnel) {
        return null;
    }
}
