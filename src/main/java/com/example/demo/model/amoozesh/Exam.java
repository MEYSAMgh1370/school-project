package com.example.demo.model.amoozesh;

import com.example.demo.model.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Exam extends BaseEntity {

    LocalDateTime examDate;
    @ManyToOne
    EducationalYear educationalYear;
    @ManyToOne
    Course course;

}
