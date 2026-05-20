package com.example.makemovie.enums;

import lombok.Getter;

@Getter
public enum ShotType {
    ECU("大特写"),
    CU("特写"),
    MCU("中近景"),
    MS("中景"),
    FS("全景"),
    LS("远景");

    private final String displayName;

    ShotType(String displayName) {
        this.displayName = displayName;
    }
}
