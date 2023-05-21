package com.example.demo.model.amoozesh;

import com.example.demo.model.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
public class Course extends BaseEntity {
    String title;
    @OneToMany
    List<TeacherCourse> teacherCourseList;
    @ManyToOne
    Level level;
    @ManyToOne
    @JoinColumn(name = "exam_id")
    Exam exam;


}
