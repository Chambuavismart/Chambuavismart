package com.chambua.vismart.dto;

public class LeagueTableEntryDTO {
    private int position;
    private Long teamId;
    private String teamName;
    private int mp;
    private int w;
    private int d;
    private int l;
    private int gf;
    private int ga;
    private int gd;
    private int pts;

    public LeagueTableEntryDTO() {}

    public LeagueTableEntryDTO(int position, Long teamId, String teamName, int mp, int w, int d, int l, int gf, int ga, int gd, int pts) {
        this.position = position;
        this.teamId = teamId;
        this.teamName = teamName;
        this.mp = mp;
        this.w = w;
        this.d = d;
        this.l = l;
        this.gf = gf;
        this.ga = ga;
        this.gd = gd;
        this.pts = pts;
    }

    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public int getMp() { return mp; }
    public void setMp(int mp) { this.mp = mp; }

    public int getW() { return w; }
    public void setW(int w) { this.w = w; }

    public int getD() { return d; }
    public void setD(int d) { this.d = d; }

    public int getL() { return l; }
    public void setL(int l) { this.l = l; }

    public int getGf() { return gf; }
    public void setGf(int gf) { this.gf = gf; }

    public int getGa() { return ga; }
    public void setGa(int ga) { this.ga = ga; }

    public int getGd() { return gd; }
    public void setGd(int gd) { this.gd = gd; }

    public int getPts() { return pts; }
    public void setPts(int pts) { this.pts = pts; }
}
