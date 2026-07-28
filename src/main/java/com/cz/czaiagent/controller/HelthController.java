package com.cz.czaiagent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/helth")
public class HelthController {

    @GetMapping
    public String healthCheck(){
        return "OK!";
    }
}
