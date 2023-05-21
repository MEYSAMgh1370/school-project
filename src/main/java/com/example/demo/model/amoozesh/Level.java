package com.example.demo.model.amoozesh;

import com.example.demo.model.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
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
public class Level extends BaseEntity {
    String title;
    @OneToMany
    List<Course> course;
    @OneToMany
    List<ClassRoom> classRoom;
    @OneToMany
    List<StudentLevel> studentLevels;
}
