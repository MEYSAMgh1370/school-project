package com.example.roshdeandishe.iam.shared.exeption;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ExceptionRegistry {

    private final Map<Class<? extends Exception>, ExceptionDescriptor> registry = Map.of(
            ModuleNotEnabledException.class,
            new ExceptionDescriptor(HttpStatus.FORBIDDEN, "MODULE_DISABLED", "Module is disabled"),

            IllegalStateException.class,
            new ExceptionDescriptor(HttpStatus.BAD_REQUEST, "ILLEGAL_STATE", "Invalid system state")
    );

    public ExceptionDescriptor resolve(Exception ex) {
        return registry.getOrDefault(
                ex.getClass(),
                new ExceptionDescriptor(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_ERROR",
                        "Unexpected error occurred"
                )
        );
    }
}