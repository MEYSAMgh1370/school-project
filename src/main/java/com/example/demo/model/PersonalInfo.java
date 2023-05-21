package com.example.demo.model;

import com.example.demo.enums.GenderType;
import com.example.demo.model.amoozesh.Student;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class PersonalInfo {
    @Id
    private Long id;

    private String firstName;
    private String lastName;
    private String birthDate;
    private String nationalNumber;
    private GenderType genderType;
    private String address;
    @Lob
    Byte[] image;



}
