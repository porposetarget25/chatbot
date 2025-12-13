package com.example.genai.repo;

import com.example.genai.entity.Spot;
import com.example.genai.entity.SpotPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpotPhotoRepo extends JpaRepository<SpotPhoto, Long> {
    List<SpotPhoto> findBySpotOrderByOrderIndexAsc(Spot spot);
}

