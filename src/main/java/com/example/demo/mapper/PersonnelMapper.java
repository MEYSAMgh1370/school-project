package com.example.demo.mapper;

import com.example.demo.dto.PersonnelDTO;
import com.example.demo.model.amoozesh.Personnel;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;


@Mapper(componentModel="spring",unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PersonnelMapper  {

    PersonnelDTO personnelToPersonnelDTO(Personnel personnel);
    Personnel personnelDTOToPersonnel(PersonnelDTO personnelDTO);
}
