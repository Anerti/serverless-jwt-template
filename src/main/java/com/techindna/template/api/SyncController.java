package com.techindna.template.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SyncController {

    @GetMapping("/syn")
    public String syn() {
        return "syn-ack";
    }

}