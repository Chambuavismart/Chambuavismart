package com.chambua.vismart.dto;

import java.util.ArrayList;
import java.util.List;

public class QuickInsightsResponse {
    private List<QuickInsightItem> highInterest;
    private List<QuickInsightItem> topPicks;

    public QuickInsightsResponse() {
        this.highInterest = new ArrayList<>();
        this.topPicks = new ArrayList<>();
    }

    public QuickInsightsResponse(List<QuickInsightItem> highInterest, List<QuickInsightItem> topPicks) {
        this.highInterest = highInterest != null ? highInterest : new ArrayList<>();
        this.topPicks = topPicks != null ? topPicks : new ArrayList<>();
    }

    public List<QuickInsightItem> getHighInterest() { return highInterest; }
    public void setHighInterest(List<QuickInsightItem> highInterest) { this.highInterest = highInterest != null ? highInterest : new ArrayList<>(); }

    public List<QuickInsightItem> getTopPicks() { return topPicks; }
    public void setTopPicks(List<QuickInsightItem> topPicks) { this.topPicks = topPicks != null ? topPicks : new ArrayList<>(); }
}
