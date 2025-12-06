package com.example.genai.controller;

import com.example.genai.service.PhotoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoService photoService;

    @Autowired
    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }


    @PostMapping("/generate")
    public String generateAll() {
        int spotsProcessed = photoService.downloadPhotosForAllSpots();
        return "Photo generation finished for " + spotsProcessed + " spots.";
    }
}
