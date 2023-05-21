package com.example.demo.controllers;


import com.example.demo.dto.PersonnelDTO;
import com.example.demo.mapper.PersonnelMapper;
import com.example.demo.service.PersonnelService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/personnels")
public class PersonnelController {

    private final PersonnelService personnelService;

    private final PersonnelMapper personnelMapper;


    public PersonnelController(PersonnelService personnelService, PersonnelMapper personnelMapper) {
        this.personnelService = personnelService;
        this.personnelMapper = personnelMapper;
    }

    @GetMapping("/{nNumber}")
    public PersonnelDTO getPersonnelByNationalNumber(@PathVariable String nNumber) {
        return personnelMapper.personnelToPersonnelDTO(personnelService.findByNationalNumber(nNumber));
    }

    @GetMapping("/personnels")
    public Set<PersonnelDTO> getPersonnels() {
        return personnelService.findAll().stream()
                .map(personnelMapper::personnelToPersonnelDTO)
                .collect(Collectors.toSet());
    }

    @Transactional
    @PostMapping
    public PersonnelDTO save(PersonnelDTO personnelDTO) {
        return personnelMapper.personnelToPersonnelDTO(personnelService.createNewPersonnel(personnelMapper.personnelDTOToPersonnel(personnelDTO)));
    }

   /* @Transactional
    @PatchMapping("/{id}")
    public per*/

    @PutMapping("/{id}")
    public ResponseEntity<PersonnelDTO> updatePersonnel(@RequestBody PersonnelDTO personnelDTO,@RequestParam Long id) {

        return new ResponseEntity<>(personnelMapper.personnelToPersonnelDTO(personnelService.
                savePersonnelByDTO(personnelMapper.personnelDTOToPersonnel(personnelDTO))), HttpStatus.OK);

    }
}
