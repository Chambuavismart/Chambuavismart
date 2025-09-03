package com.chambua.vismart.controller;

import com.chambua.vismart.model.Country;
import com.chambua.vismart.repository.CountryRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/countries")
@CrossOrigin(origins = "*")
public class CountriesController {

    private final CountryRepository countryRepository;

    public CountriesController(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    @GetMapping
    public List<Country> list() {
        return countryRepository.findAllByOrderByNameAsc();
    }
}
