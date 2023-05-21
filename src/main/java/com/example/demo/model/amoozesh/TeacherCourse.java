package com.example.demo.model.amoozesh;

import com.example.demo.model.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class TeacherCourse extends BaseEntity {

    @ManyToOne
    private ClassRoom classRoom;
    @ManyToOne
    private Personnel personnel;
    @ManyToOne
    private Course course;
}
