package com.chambua.vismart.controller;

import com.chambua.vismart.dto.GroupedLeagueDTO;
import com.chambua.vismart.dto.LeagueSeasonOptionDTO;
import com.chambua.vismart.model.League;
import com.chambua.vismart.repository.LeagueRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leagues")
@CrossOrigin(origins = "*")
public class LeaguesQueryController {

    private final LeagueRepository leagueRepository;

    public LeaguesQueryController(LeagueRepository leagueRepository) {
        this.leagueRepository = leagueRepository;
    }

    @GetMapping
    public List<League> listLeagues(){
        return leagueRepository.findAll();
    }

    // Alias for frontend requirement: /api/leagues/list
    @GetMapping("/list")
    public List<League> listLeaguesAlias(){
        return leagueRepository.findAll();
    }

    @GetMapping("/{id}")
    public League getLeague(@PathVariable Long id){
        return leagueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found"));
    }

    @DeleteMapping("/{id}")
    public void deleteLeague(@PathVariable Long id){
        if (!leagueRepository.existsById(id)){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "League not found");
        }
        try{
            leagueRepository.deleteById(id);
        }catch (DataIntegrityViolationException ex){
            // Likely due to FK constraints from matches/fixtures/etc.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete league because it has dependent records (matches, fixtures, teams, or seasons). Remove dependents first.", ex);
        }
    }

    // New: Grouped for fixtures upload dropdown (group by country + league name, seasons latest -> oldest)
    @GetMapping("/grouped-upload")
    public List<GroupedLeagueDTO> getGroupedLeaguesForUpload(){
        List<League> leagues = leagueRepository.findAll();
        // group by country + name (case-insensitive for grouping, but keep original for display)
        Map<String, List<League>> grouped = leagues.stream().collect(Collectors.groupingBy(
                l -> (l.getCountry() + "\u0001" + l.getName()).toLowerCase()
        ));

        List<GroupedLeagueDTO> out = new ArrayList<>();
        for (Map.Entry<String, List<League>> e : grouped.entrySet()){
            List<League> fam = e.getValue();
            // derive display country/name from any element (prefer lexicographically smallest to stabilize)
            fam.sort(Comparator.comparing(League::getSeason)); // temp sort just to pick stable representative
            String country = fam.get(0).getCountry();
            String name = fam.get(0).getName();
            // sort seasons descending using custom comparator
            fam.sort((a,b) -> compareSeasonDesc(a.getSeason(), b.getSeason()));
            List<LeagueSeasonOptionDTO> options = fam.stream()
                    .map(l -> new LeagueSeasonOptionDTO(l.getId(), l.getSeason(), l.getSeason()))
                    .collect(Collectors.toList());
            String groupLabel = country + " — " + name;
            out.add(new GroupedLeagueDTO(country, name, groupLabel, options));
        }
        // sort groups by country then league name
        out.sort(Comparator.comparing(GroupedLeagueDTO::getCountry, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(GroupedLeagueDTO::getLeagueName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // Orders seasons like "2025/2026" before "2024/2025"; supports single-year like "2025"
    private static int compareSeasonDesc(String s1, String s2){
        int a1 = firstYear(s1);
        int a2 = firstYear(s2);
        if (a1 != a2) return Integer.compare(a2, a1); // higher first-year first
        // tie-breaker: compare second year if present
        int b1 = secondYearOrSame(s1, a1);
        int b2 = secondYearOrSame(s2, a2);
        if (b1 != b2) return Integer.compare(b2, b1);
        // final tie-breaker: lexicographic desc to stabilize
        return s2.compareToIgnoreCase(s1);
    }

    private static int firstYear(String s){
        if (s == null) return Integer.MIN_VALUE;
        try{
            int slash = s.indexOf('/');
            if (slash > 0){
                return Integer.parseInt(s.substring(0, slash).trim());
            }
            return Integer.parseInt(s.trim());
        }catch(Exception ex){
            return Integer.MIN_VALUE; // unknown formats go last
        }
    }

    private static int secondYearOrSame(String s, int fallback){
        if (s == null) return fallback;
        int slash = s.indexOf('/');
        if (slash > 0){
            try{
                return Integer.parseInt(s.substring(slash+1).trim());
            }catch(Exception ignore){
                return fallback;
            }
        }
        return fallback;
    }
}