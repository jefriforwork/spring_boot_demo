package com.example.demo.service;

import com.example.demo.entity.Tag;
import com.example.demo.repository.TagRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public Set<Tag> resolve(Collection<String> names) {
        Set<Tag> tags = new LinkedHashSet<>();
        if (names == null) {
            return tags;
        }
        for (String raw : names) {
            if (raw == null) {
                continue;
            }
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            tags.add(tagRepository.findByName(name).orElseGet(() -> tagRepository.save(new Tag(name))));
        }
        return tags;
    }
}
