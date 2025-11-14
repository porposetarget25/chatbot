package com.example.genai.service;

import com.example.genai.repo.*;
import com.example.genai.entity.*;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExcelImporter {

    private final CountryRepo countryRepo;
    private final CityRepo cityRepo;
    private final SpotRepo spotRepo;
    private final PromptTemplateRepo templRepo;

    public ExcelImporter(CountryRepo c, CityRepo ci, SpotRepo s, PromptTemplateRepo t) {
        this.countryRepo = c; this.cityRepo = ci; this.spotRepo = s; this.templRepo = t;
    }

    @Transactional
    public void importData(InputStream excelIn) throws IOException {
        try (Workbook wb = WorkbookFactory.create(excelIn)) {
            Sheet sheet = wb.getSheet("Data");
            if (sheet == null) throw new IllegalStateException("Sheet 'Data' not found");

            // header row index = 1 (based on your file)
            int headerRow = 1;
            Map<String,Integer> col = mapColumns(sheet.getRow(headerRow),
                    "Location","City","Country","Text1","Text2","Text3");

            for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String countryName = get(row, col.get("Country"));
                String cityName    = get(row, col.get("City"));
                String spotName    = get(row, col.get("Location"));

                if (isBlank(countryName) || isBlank(cityName) || isBlank(spotName)) continue;

                Country country = countryRepo.findByNameIgnoreCase(countryName)
                        .orElseGet(() -> countryRepo.save(new Country(null, countryName)));
                City city = cityRepo.findByCountryAndNameIgnoreCase(country, cityName)
                        .orElseGet(() -> cityRepo.save(new City(null, country, cityName)));
                Spot spot = spotRepo.findByCityAndNameIgnoreCase(city, spotName)
                        .orElseGet(() -> spotRepo.save(new Spot(null, city, spotName)));

                for (String key : List.of("Text1", "Text2", "Text3")) {
                    String txt = get(row, col.get(key));
                    if (!isBlank(txt)) {
                        templRepo.findBySpotAndPromptKey(spot, key)
                                .orElseGet(() -> templRepo.save(
                                        new PromptTemplate(null, spot, key, txt)
                                ));
                    }
                }

            }
        }
    }

    private static Map<String,Integer> mapColumns(Row header, String... names) {
        Map<String,Integer> map = new HashMap<>();
        for (int i=0;i<header.getLastCellNum();i++) {
            String v = get(header, i);
            if (v != null) map.put(v.trim(), i);
        }
        for (String n: names) if (!map.containsKey(n)) throw new IllegalStateException("Missing column: "+n);
        return map;
    }
    private static String get(Row row, int idx) {
        if (row == null || idx < 0) return null;
        Cell c = row.getCell(idx);
        return (c == null) ? null : c.toString().trim();
    }
    private static boolean isBlank(String s){ return s==null || s.isBlank(); }
}

