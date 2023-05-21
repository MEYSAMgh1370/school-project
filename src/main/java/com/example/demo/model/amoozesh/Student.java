package com.example.demo.model.amoozesh;

import com.example.demo.model.BaseEntity;
import com.example.demo.model.PersonalInfo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;



import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Student extends BaseEntity {

    @OneToMany
    private List<StudentLevel> studentLevels;
    @OneToMany
    private List<Score> scores;

    @OneToOne
    @MapsId
    private PersonalInfo personalInfo;

    @ManyToOne
    @MapsId
    private PersonalInfo motherInfo;
    private String motherLastName;
    private String fatherName;
    private Long fatherNumber;
    private Long motherNumber;
    private Long homeNumber;

}
