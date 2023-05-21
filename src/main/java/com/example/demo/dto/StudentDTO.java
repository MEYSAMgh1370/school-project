package com.example.demo.dto;
import com.example.demo.enums.FieldType;
import com.example.demo.enums.Order;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class StudentDTO {

   private FieldType fieldType;
   private Order order;
   private String firstName;
   private String lastName;
   private String nationalNumber;

}
