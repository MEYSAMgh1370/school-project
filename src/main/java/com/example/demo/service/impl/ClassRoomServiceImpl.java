package com.example.demo.service.impl;

import com.example.demo.model.amoozesh.ClassRoom;
import com.example.demo.repositories.ClassRoomRepository;
import com.example.demo.service.ClassRoomService;

import java.util.Set;

public class ClassRoomServiceImpl implements ClassRoomService {

    private final ClassRoomRepository classRoomRepository;

    public ClassRoomServiceImpl(ClassRoomRepository classRoomRepository) {
        this.classRoomRepository = classRoomRepository;
    }

    @Override
    public Set<ClassRoom> getClasses() {
        return null;
    }

    @Override
    public ClassRoom saveClassRoom(ClassRoom classRoom) {
        return null;
    }

    @Override
    public ClassRoom findClassByName(String name) {
        return null;
    }

    @Override
    public void  delete(ClassRoom classRoom) {

    }
}
