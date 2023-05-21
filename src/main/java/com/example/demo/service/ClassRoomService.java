package com.example.demo.service;

import com.example.demo.model.amoozesh.ClassRoom;

import java.util.Set;

public interface ClassRoomService {

    Set<ClassRoom> getClasses();

    ClassRoom saveClassRoom(ClassRoom classRoom);

    ClassRoom findClassByName(String name);

    void delete(ClassRoom classRoom);
}
