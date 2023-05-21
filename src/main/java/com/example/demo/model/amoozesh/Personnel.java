package com.example.demo.model.amoozesh;

import com.example.demo.model.BaseEntity;
import com.example.demo.model.PersonalInfo;
import jakarta.persistence.Entity;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Personnel extends BaseEntity {

    @OneToMany
    private List<TeacherCourse> teacherCourseList;

    @OneToMany
    private List<Score> scores;

    @OneToOne
    @MapsId
    private PersonalInfo personalInfo;
}
