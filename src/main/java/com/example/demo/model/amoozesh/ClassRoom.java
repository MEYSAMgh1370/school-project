package com.example.demo.model.amoozesh;

import com.example.demo.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class ClassRoom extends BaseEntity {

    @Column
    @OneToMany
    private List<TeacherCourse> teacherCourseList;
    @OneToMany
    private List<Student> classStudents;
    @ManyToMany(mappedBy = "classRooms")
    private Set<EducationalYear> educationalYears;
    @ManyToOne
    @JoinColumn(name = "level_id")
    private Level level;

}
