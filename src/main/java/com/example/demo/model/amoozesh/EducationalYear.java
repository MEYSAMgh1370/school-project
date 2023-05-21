package com.example.demo.model.amoozesh;

import com.example.demo.model.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class EducationalYear extends BaseEntity {

    @Column(nullable = false)
    private String title;
    @ManyToMany
    @JoinTable(name = "educationalyear-classroom", joinColumns = @JoinColumn(name = "educationalyear_id",referencedColumnName = "id"),inverseJoinColumns = @JoinColumn(name = "classroom_id",referencedColumnName = "id"))
    private List<ClassRoom> classRooms;
    @OneToMany
    private List<Exam> examList;
}
