package com.example.genai.repo;

import com.example.genai.entity.Language;
import com.example.genai.entity.Response;
import com.example.genai.entity.Spot;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;

public interface ResponseRepo extends JpaRepository<Response, Long> {
    Optional<Response> findBySpotIdAndLanguageIdAndTemplateKey(
            Long spotId, Long languageId, String templateKey);
    Optional<Response> findBySpotAndLanguageAndTemplateKey(Spot spot, Language language, String templateKey);

}
