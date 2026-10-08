package com.techindna.template.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SyncController {

    @GetMapping("/syn")
    public String syn() {
        return "syn-ack";
    }

}