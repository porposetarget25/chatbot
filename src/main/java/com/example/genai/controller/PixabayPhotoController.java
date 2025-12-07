package com.example.genai.controller;

import com.example.genai.service.PixabayPhotoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/photos/pixabay")
public class PixabayPhotoController {

    private final PixabayPhotoService photoService;

    @Autowired
    public PixabayPhotoController(PixabayPhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping("/download")
    public String downloadPixabayPhotos() {
        int count = photoService.downloadPhotosForAllSpotsFromPixabay();
        return "Triggered Pixabay photo download for " + count + " spots.";
    }
}
