package com.example.genai.repo;

import com.example.genai.entity.Response;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResponseRepo extends JpaRepository<Response, Long> {
    Optional<Response> findBySpotIdAndLanguageIdAndPromptKey(Long spotId, Long langId, String promptKey);
    List<Response> findBySpotId(Long spotId);
}
