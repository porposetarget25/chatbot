package com.example.genai.controller;

import com.example.genai.service.ExcelImporter3Col;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/excel")
public class ExcelUploadController {

    private final ExcelImporter3Col importer;

    public ExcelUploadController(ExcelImporter3Col importer) {
        this.importer = importer;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadExcel(@RequestParam("file") MultipartFile file) {
        try {
            importer.importLocations(file.getInputStream());
            return ResponseEntity.ok("Excel uploaded and processed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
