package com.ld.poetry.service;

public class ResourceMigrationSourceChangedException extends IllegalStateException {

    public ResourceMigrationSourceChangedException(String message) {
        super(message);
    }
}