package com.example.genai.service;

import com.example.genai.entity.City;
import com.example.genai.entity.Country;
import com.example.genai.entity.Spot;
import com.example.genai.repo.CityRepo;
import com.example.genai.repo.CountryRepo;
import com.example.genai.repo.SpotRepo;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class ExcelImporter3Col {

    private final CountryRepo countryRepo;
    private final CityRepo cityRepo;
    private final SpotRepo spotRepo;

    public ExcelImporter3Col(CountryRepo countryRepo,
                             CityRepo cityRepo,
                             SpotRepo spotRepo) {
        this.countryRepo = countryRepo;
        this.cityRepo = cityRepo;
        this.spotRepo = spotRepo;
    }

    @Transactional
    public void importLocations(InputStream excelIn) throws IOException {
        try (Workbook wb = WorkbookFactory.create(excelIn)) {
            // 1) Pick sheet: try "Data" first, then fallback to first sheet
            Sheet sheet = wb.getSheet("Data");
            if (sheet == null) {
                sheet = wb.getSheetAt(0);
            }

            if (sheet == null) {
                throw new IllegalStateException("No sheets found in Excel file");
            }

            // 2) Find header row automatically (first few rows)
            Row header = findHeaderRow(sheet);
            int headerRowIdx = header.getRowNum();

            // 3) Map header names -> column indices
            Map<String,Integer> col = mapColumns(header,
                    "country", "city", "spot");

            // 4) Iterate data rows
            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String countryName = get(row, col.get("country"));
                String cityName    = get(row, col.get("city"));
                String spotName    = get(row, col.get("spot"));

                if (isBlank(countryName) || isBlank(cityName) || isBlank(spotName)) {
                    continue;
                }

                Country country = countryRepo.findByNameIgnoreCase(countryName)
                        .orElseGet(() -> countryRepo.save(new Country(null, countryName)));

                City city = cityRepo.findByCountryAndNameIgnoreCase(country, cityName)
                        .orElseGet(() -> cityRepo.save(new City(null, country, cityName)));

                spotRepo.findByCityAndNameIgnoreCase(city, spotName)
                        .orElseGet(() -> spotRepo.save(new Spot(null, city, spotName)));
            }
        }
    }

    // ---------- Header detection ----------

    private static Row findHeaderRow(Sheet sheet) {
        int maxScanRows = Math.min(10, sheet.getLastRowNum()); // scan first up to 10 rows
        for (int r = 0; r <= maxScanRows; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            List<String> values = readRowStrings(row);
            if (values.isEmpty()) continue;

            // Lowercase all values for checks
            List<String> lower = values.stream()
                    .filter(Objects::nonNull)
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .toList();

            boolean hasCountry = lower.stream().anyMatch(s -> s.contains("country"));
            boolean hasCity    = lower.stream().anyMatch(s -> s.contains("city"));
            boolean hasSpot    = lower.stream().anyMatch(s -> s.contains("spot"));

            // If row contains at least 2 of the 3 keywords, we treat as header row
            int count = (hasCountry ? 1 : 0) + (hasCity ? 1 : 0) + (hasSpot ? 1 : 0);
            if (count >= 2) {
                return row;
            }
        }

        // If not found, build helpful message
        StringBuilder sb = new StringBuilder("Could not find header row with columns containing 'country', 'city', 'spot'. ");
        sb.append("First few rows:\n");
        for (int r = 0; r <= Math.min(5, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            sb.append("Row ").append(r + 1).append(": ").append(readRowStrings(row)).append("\n");
        }
        throw new IllegalStateException(sb.toString());
    }

    private static List<String> readRowStrings(Row row) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell c = row.getCell(i);
            if (c == null) {
                list.add(null);
            } else {
                c.setCellType(CellType.STRING);
                String v = c.getStringCellValue();
                list.add(v != null ? v.trim() : null);
            }
        }
        return list;
    }

    // ---------- Column mapping ----------

    private static Map<String,Integer> mapColumns(Row header, String... logicalNames) {
        Map<String,Integer> headerMap = new HashMap<>();
        for (int i = 0; i < header.getLastCellNum(); i++) {
            Cell c = header.getCell(i);
            if (c == null) continue;
            c.setCellType(CellType.STRING);
            String v = c.getStringCellValue();
            if (v == null) continue;
            String key = v.trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                headerMap.put(key, i);
            }
        }

        Map<String,Integer> result = new HashMap<>();
        for (String logical : logicalNames) {
            String logicalLower = logical.toLowerCase(Locale.ROOT);

            // 1) exact match
            Integer idx = headerMap.get(logicalLower);

            // 2) if no exact match, try "contains"
            if (idx == null) {
                for (Map.Entry<String,Integer> e : headerMap.entrySet()) {
                    if (e.getKey().contains(logicalLower)) {
                        idx = e.getValue();
                        break;
                    }
                }
            }

            if (idx == null) {
                throw new IllegalStateException(
                        "Missing column matching: " + logical +
                                ". Found headers: " + headerMap.keySet()
                );
            }

            result.put(logicalLower, idx);
        }

        return result;
    }

    // ---------- Helpers ----------

    private static String get(Row row, Integer idx) {
        if (row == null || idx == null || idx < 0) return null;
        Cell c = row.getCell(idx);
        if (c == null) return null;
        c.setCellType(CellType.STRING);
        String v = c.getStringCellValue();
        return v != null ? v.trim() : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
